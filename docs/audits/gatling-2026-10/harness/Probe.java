import com.sun.net.httpserver.HttpServer;
import io.testfly.loadtest.*;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
public class Probe {
  public static void main(String[] a) throws Exception {
    AtomicInteger hits = new AtomicInteger();
    HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    s.createContext("/ok", ex -> { hits.incrementAndGet(); byte[] b="{}".getBytes(); ex.sendResponseHeaders(200,b.length); ex.getResponseBody().write(b); ex.close(); });
    s.start();
    String base = "http://127.0.0.1:" + s.getAddress().getPort();
    if (a[0].equals("jdkcheck")) {
      LoadTestMetrics m = LoadScenario.named("P").baseUrl(base).engine("jdk").step("ok").get("/ok").check(LoadStep.status().is(201)).and()
        .users(3).rampUp(Duration.ofSeconds(1)).hold(Duration.ofSeconds(2)).run().metrics();
      System.out.println("JDK failing check: total="+m.totalRequests()+" ko="+m.failedRequests()+" errorRate="+m.errorRate()+" status="+m.statusCodes()+" serverHits="+hits.get());
    } else {
      Thread t = new Thread(() -> { try { LoadScenario.named("P2").baseUrl(base).engine("gatling").step("ok").get("/ok").and()
          .users(2).rampUp(Duration.ofSeconds(1)).hold(Duration.ofSeconds(30)).run(); } catch (Throwable e) { System.out.println("ended: "+e); } });
      t.start(); Thread.sleep(6000);
      System.out.println("children before interrupt:"); ProcessHandle.current().descendants().forEach(p -> System.out.println("  pid="+p.pid()+" alive="+p.isAlive()));
      t.interrupt(); Thread.sleep(2000);
      int before = hits.get(); Thread.sleep(5000);
      System.out.println("server hits continued after interrupt: " + before + " -> " + hits.get());
      ProcessHandle.current().descendants().forEach(p -> System.out.println("  after: pid="+p.pid()+" alive="+p.isAlive()+" cmd="+p.info().command().orElse("?")));
      System.out.println("leftover temp configs: " + java.util.Arrays.toString(new java.io.File(System.getProperty("java.io.tmpdir")).list((d,n)->n.startsWith("testfly-gatling-"))));
    }
    s.stop(0); System.exit(0);
  }
}
