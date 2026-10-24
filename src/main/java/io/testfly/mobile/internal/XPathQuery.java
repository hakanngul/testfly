package io.testfly.mobile.internal;

import io.testfly.mobile.MobileLocatorException;
import io.testfly.mobile.MobileNode;
import io.testfly.mobile.internal.LocatorEngine.Query;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * XPath 1.0 over mobilecli's view tree, with the JDK's XPath engine. Elements are named like Appium
 * names them ({@code android.widget.TextView}, {@code XCUIElementTypeButton}) under a
 * {@code hierarchy} root; attributes: {@code resource-id}/{@code identifier}, {@code content-desc},
 * {@code text}, {@code name}, {@code label}, {@code value}, {@code placeholder}, {@code type},
 * {@code class}, {@code enabled}, {@code visible}, {@code selected}, {@code checked}, {@code focused},
 * {@code x}, {@code y}, {@code width}, {@code height}. mobilecli prunes unlabeled containers, so
 * Appium XPaths with full hierarchy paths may not match; id, text and type based ones do.
 */
public final class XPathQuery extends Query {

    private final String xpath;

    public XPathQuery(String xpath) {
        if (xpath == null || xpath.isBlank()) {
            throw new MobileLocatorException("Invalid XPath: it is blank");
        }
        this.xpath = xpath;
        try {
            XPathFactory.newInstance().newXPath().compile(xpath);
        } catch (XPathExpressionException e) {
            throw new MobileLocatorException("Invalid XPath \"" + xpath + "\": " + rootMessage(e));
        }
    }

    @Override
    public List<MobileNode> evaluate(List<MobileNode> roots) {
        Document doc;
        try {
            doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("No XML document builder in this JDK", e);
        }
        Map<Element, MobileNode> nodes = new IdentityHashMap<>();
        Element hierarchy = doc.createElement("hierarchy");
        doc.appendChild(hierarchy);
        for (MobileNode root : roots) {
            hierarchy.appendChild(toElement(doc, root, nodes));
        }
        NodeList result;
        try {
            // Compiled per call: XPath objects are not thread-safe and a locator may be shared.
            result = (NodeList) XPathFactory.newInstance().newXPath().compile(xpath).evaluate(doc, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            throw new MobileLocatorException("XPath \"" + xpath + "\" must select elements: " + rootMessage(e));
        }
        List<MobileNode> out = new ArrayList<>();
        for (int i = 0; i < result.getLength(); i++) {
            MobileNode n = result.item(i) instanceof Element el ? nodes.get(el) : null;
            if (n != null) {
                out.add(n);
            }
        }
        return out;
    }

    private static Element toElement(Document doc, MobileNode n, Map<Element, MobileNode> nodes) {
        String type = NsPredicate.appiumType(n);
        Element el = doc.createElement(xmlName(type));
        nodes.put(el, n);
        set(el, "type", type);
        set(el, "class", type);
        set(el, "text", n.text());
        set(el, "name", n.name() != null ? n.name() : n.identifier());
        set(el, "label", n.label());
        set(el, "content-desc", n.label());
        set(el, "value", n.value());
        set(el, "placeholder", n.placeholder());
        set(el, "identifier", n.identifier());
        set(el, "resource-id", n.identifier());
        el.setAttribute("enabled", String.valueOf(n.enabled()));
        el.setAttribute("visible", String.valueOf(n.visible()));
        el.setAttribute("selected", String.valueOf(n.selected()));
        el.setAttribute("checked", String.valueOf(n.checked()));
        el.setAttribute("focused", String.valueOf(n.focused()));
        el.setAttribute("x", String.valueOf((int) n.bounds().x()));
        el.setAttribute("y", String.valueOf((int) n.bounds().y()));
        el.setAttribute("width", String.valueOf((int) n.bounds().width()));
        el.setAttribute("height", String.valueOf((int) n.bounds().height()));
        for (MobileNode child : n.children()) {
            el.appendChild(toElement(doc, child, nodes));
        }
        return el;
    }

    private static void set(Element el, String name, String value) {
        if (value != null) {
            el.setAttribute(name, value);
        }
    }

    /** XML element names cannot hold every character of a native class name (e.g. {@code $}). */
    private static String xmlName(String type) {
        String name = type.replaceAll("[^A-Za-z0-9._-]", "_");
        return name.isEmpty() || !Character.isLetter(name.charAt(0)) && name.charAt(0) != '_' ? "_" + name : name;
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getMessage() != null ? t.getMessage() : e.toString();
    }

    @Override
    public String describe() {
        return "xpath(\"" + xpath + "\")";
    }
}
