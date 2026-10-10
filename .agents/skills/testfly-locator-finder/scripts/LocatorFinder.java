import io.testfly.driver.DriverManager;
import io.testfly.locator.Locator;
import io.testfly.locator.Role;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.json.Json;

/**
 * Opens a URL in Chrome, runs locator-scan.js, then re-resolves every proposed
 * locator through the real io.testfly.locator.Locator to prove it hits exactly the
 * scanned element. Prints a report and a ready-to-paste BasePage subclass.
 *
 * Agent tooling, launched as a Java 21 source file by find-locators.sh.
 */
public final class LocatorFinder {

    public static void main(String[] args) throws Exception {
        Map<String, String> opt = parseArgs(args);
        String url = opt.get("url");
        Path scanJs = Path.of(opt.getOrDefault("scan-js", "locator-scan.js"));
        String pageClass = opt.getOrDefault("class", "ScannedPage");

        ChromeOptions options = new ChromeOptions();
        if (!opt.containsKey("headed")) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--window-size=" + opt.getOrDefault("window", "1440,900"), "--lang=tr-TR");
        WebDriver driver = new ChromeDriver(options);
        try {
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));
            driver.get(url);
            Thread.sleep(Long.parseLong(opt.getOrDefault("wait", "1500"))); // let SPAs hydrate

            if (opt.containsKey("test-id-attr")) {
                Locator.setTestIdAttribute(opt.get("test-id-attr"));
            }
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("roles", roleTable());
            config.put("formControlCss", privateStatic("FORM_CONTROL_CSS"));
            config.put("accessibleNameJs", privateStatic("ACCESSIBLE_NAME_JS"));
            config.put("testIdAttribute", privateStatic("testIdAttribute"));
            config.put("includeHidden", opt.containsKey("include-hidden"));
            config.put("returnElements", true);

            String script = Files.readString(scanJs);
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = (Map<String, Object>) ((JavascriptExecutor) driver)
                    .executeScript("return (" + script + ")(arguments[0]);", config);
            @SuppressWarnings("unchecked")
            Map<String, Object> report = new Json().toType((String) raw.get("json"), Map.class);
            @SuppressWarnings("unchecked")
            List<WebElement> scanned = (List<WebElement>) raw.get("elements");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> locators = (List<Map<String, Object>>) report.get("locators");

            DriverManager.pushSessionOverride(driver);
            int verified = 0;
            List<String> failures = new ArrayList<>();
            for (int i = 0; i < locators.size(); i++) {
                Map<String, Object> l = locators.get(i);
                String status;
                try {
                    @SuppressWarnings("unchecked")
                    Locator locator = build((Map<String, Object>) l.get("spec"));
                    List<WebElement> hits = locator.findElements(driver);
                    boolean nth = ((Map<?, ?>) l.get("spec")).containsKey("nth");
                    if (hits.size() == 1 && hits.get(0).equals(scanned.get(i))) {
                        status = nth ? "VERIFIED_NTH" : "VERIFIED";
                        verified++;
                    } else {
                        status = "MISMATCH(" + hits.size() + ")";
                        failures.add(l.get("java") + " -> " + hits.size() + " match(es)");
                    }
                } catch (RuntimeException e) {
                    status = "ERROR";
                    failures.add(l.get("java") + " -> " + e.getMessage());
                }
                l.put("testfly", status);
            }
            DriverManager.popSessionOverride();

            if (opt.containsKey("screenshot")) {
                Path shot = Path.of(opt.get("screenshot"));
                Files.write(shot, ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES));
            }
            report.put("verified", verified);
            String text = render(report, locators, pageClass, failures);
            System.out.println(text);
            if (opt.containsKey("json")) {
                Files.writeString(Path.of(opt.get("json")), new Json().toJson(report));
            }
            if (opt.containsKey("out")) {
                Files.writeString(Path.of(opt.get("out")), text);
            }
        } finally {
            driver.quit();
        }
    }

    /** Rebuilds the scanner's choice with the public TestFly factories. */
    private static Locator build(Map<String, Object> s) {
        String kind = (String) s.get("kind");
        String value = (String) s.get("value");
        Locator l = switch (kind) {
            case "testId" -> Locator.byTestId(value);
            case "role" -> {
                Locator r = Locator.byRole(Role.valueOf((String) s.get("role"))).withName((String) s.get("name"));
                yield s.get("level") != null ? r.withLevel(((Number) s.get("level")).intValue()) : r;
            }
            case "label" -> Locator.byLabel(value);
            case "placeholder" -> Locator.byPlaceholder(value);
            case "altText" -> Locator.byAltText(value);
            case "title" -> Locator.byTitle(value);
            case "id" -> Locator.id(value);
            case "name" -> Locator.name(value);
            case "text" -> Locator.byText(value);
            case "css" -> Locator.cssSelector(value);
            default -> throw new IllegalArgumentException("unknown kind " + kind);
        };
        if (Boolean.TRUE.equals(s.get("exact"))) {
            l = l.exact();
        }
        if (s.get("nth") != null) {
            l = l.nth(((Number) s.get("nth")).intValue());
        }
        return l;
    }

    private static String render(Map<String, Object> report, List<Map<String, Object>> locators, String pageClass,
            List<String> failures) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Locators for ").append(report.get("url")).append('\n');
        sb.append("Title: ").append(report.get("title")).append(" | elements: ").append(locators.size())
                .append(" | verified by io.testfly.locator.Locator: ").append(report.get("verified")).append("\n\n");
        Map<?, ?> hints = (Map<?, ?>) report.get("testIdHints");
        if (hints != null && !hints.isEmpty()) {
            sb.append("> Test-id attribute in use: `").append(report.get("testIdAttribute"))
                    .append("`. Page also carries ").append(hints)
                    .append(" — if the suite standardises on one, rerun with `--test-id-attr <attr>` and configure ")
                    .append("`Locator.setTestIdAttribute(\"<attr>\")` once before tests start.\n\n");
        }
        sb.append("| # | field | TestFly locator | strategy | status | alternatives |\n|---|---|---|---|---|---|\n");
        for (int i = 0; i < locators.size(); i++) {
            Map<String, Object> l = locators.get(i);
            sb.append("| ").append(i + 1).append(" | ").append(l.get("field")).append(" | `").append(l.get("java"))
                    .append("` | ").append(l.get("strategy")).append(" | ").append(l.get("testfly")).append(" | ")
                    .append(String.join("<br>", ((List<?>) l.get("alternatives")).stream()
                            .map(a -> "`" + a + "`").toList()))
                    .append(" |\n");
        }
        if (!failures.isEmpty()) {
            sb.append("\n## Not verified\n");
            failures.forEach(f -> sb.append("- ").append(f).append('\n'));
        }
        sb.append("\n```java\nimport io.testfly.locator.Locator;\nimport io.testfly.locator.Role;\n")
                .append("import io.testfly.test.BasePage;\n\n")
                .append("public final class ").append(pageClass).append(" extends BasePage {\n");
        for (Map<String, Object> l : locators) {
            String status = String.valueOf(l.get("testfly"));
            if (!status.startsWith("VERIFIED")) {
                continue;
            }
            sb.append("    private final Locator ").append(l.get("field")).append(" = ").append(l.get("java")).append(";");
            if (status.equals("VERIFIED_NTH") || String.valueOf(l.get("java")).contains(":nth-of-type(")) {
                sb.append(" // positional: no unique semantic locator");
            }
            sb.append('\n');
        }
        sb.append("}\n```\n");
        return sb.toString();
    }

    private static List<List<String>> roleTable() {
        List<List<String>> rows = new ArrayList<>();
        for (Role r : Role.values()) {
            rows.add(List.of(r.name(), r.ariaName(), r.cssSelector()));
        }
        return rows;
    }

    /** Reads Locator's private matching constants so the scanner can never drift from the SDK. */
    private static String privateStatic(String name) throws ReflectiveOperationException {
        Field f = Locator.class.getDeclaredField(name);
        f.setAccessible(true);
        return (String) f.get(null);
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--")) {
                String k = a.substring(2);
                boolean flag = k.equals("headed") || k.equals("include-hidden");
                m.put(k, flag ? "true" : args[++i]);
            } else {
                m.put("url", a);
            }
        }
        if (!m.containsKey("url")) {
            throw new IllegalArgumentException("usage: LocatorFinder <url> [--class Name] [--out f.md] [--json f.json] "
                    + "[--screenshot f.png] [--wait ms] [--test-id-attr data-test] [--headed] [--include-hidden] [--scan-js path]");
        }
        return m;
    }
}
