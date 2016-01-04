package my.LJExport;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.*;
import org.w3c.dom.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.StreamResult;

import java.io.*;
import java.util.*;

import org.htmlcleaner.*;

public class DOM
{
    static public void out(String s) throws Exception
    {
        Main.out(s);
    }

    static Vector<Node> flatten(Node el) throws Exception
    {
        Vector<Node> vec = new Vector<Node>();
        flatten(vec, el);
        return vec;
    }

    static void flatten(Vector<Node> vec, Node el) throws Exception
    {
        if (el == null)
            return;
        vec.add(el);
        flatten(vec, el.getFirstChild());
        flatten(vec, el.getNextSibling());
    }

    static public Node parseHtmlAsXml(String html) throws Exception
    {
        CleanerProperties props = new CleanerProperties();
        props.setAdvancedXmlEscape(true);
        props.setTranslateSpecialEntities(true);
        props.setTransResCharsToNCR(true);
        props.setOmitComments(true);
        TagNode tagNode = new HtmlCleaner(props).clean(html);
        String xml = new PrettyXmlSerializer(props).getAsString(tagNode, "UTF-8");

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setValidating(false);
        dbf.setNamespaceAware(false);
        dbf.setCoalescing(true);
        dbf.setIgnoringComments(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = null;

        xml = removeDoctype(xml);

        try
        {
            doc = db.parse(new InputSource(new StringReader(xml)));
        }
        catch (Exception ex)
        {
            out("Error parsing " + Web.getLastURL());
            out("Raw data follows:");
            out("=====================================================================");
            out(xml);
            out("=====================================================================");
            throw ex;
        }

        return doc.getDocumentElement();
    }

    static private String removeDoctype(String xml) throws Exception
    {
        final String doctype = "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.0 Transitional//EN\" \"http://www.w3.org/TR/REC-html40/loose.dtd\">";
        final String newdoctype = "<!DOCTYPE html>";
        int k = xml.indexOf(doctype);
        if (k != -1)
        {
            int len = doctype.length();
            xml = xml.substring(0, k) + newdoctype + xml.substring(k + len);
        }
        return xml;
    }

    static public Vector<String> extractHrefs(String html) throws Exception
    {
        Vector<String> vs = new Vector<String>();
        Vector<Node> vnodes = flatten(parseHtmlAsXml(html));

        for (Node n : vnodes)
        {
            if (!n.getNodeName().equalsIgnoreCase("a"))
                continue;
            if (!(n instanceof Element))
                continue;
            String href = getAttribute((Element) n, "href");
            if (href == null || href.equals("") || href.charAt(0) == '#')
                continue;
            vs.add(href);
        }

        return vs;
    }

    static public void removeElements(Node pageRoot, Vector<Node> pageFlat, String tagname, String an1, String av1)
            throws Exception
    {
        if (pageFlat == null)
            pageFlat = flatten(pageRoot);
        Vector<Node> vel = findElements(pageFlat, tagname, an1, av1);
        removeElements(pageRoot, vel);
    }

    static public void removeElements(Node pageRoot, Vector<Node> pageFlat, String tagname, String an1, String av1, String an2,
            String av2) throws Exception
    {
        if (pageFlat == null)
            pageFlat = flatten(pageRoot);
        Vector<Node> vel = findElements(pageFlat, tagname, an1, av1, an2, av2);
        removeElements(pageRoot, vel);
    }

    static public Vector<Node> findElements(Vector<Node> pageFlat, String tagname) throws Exception
    {
        Vector<Node> vel = new Vector<Node>();

        for (Node n : pageFlat)
        {
            if (!(n instanceof Element))
                continue;
            if (!n.getNodeName().equalsIgnoreCase(tagname))
                continue;

            vel.add(n);
        }

        return vel;
    }

    static public Vector<Node> findElements(Vector<Node> pageFlat, String tagname, String an1, String av1) throws Exception
    {
        Vector<Node> vel = new Vector<Node>();
        Element el;
        String av;

        for (Node n : pageFlat)
        {
            if (!(n instanceof Element))
                continue;
            if (!n.getNodeName().equalsIgnoreCase(tagname))
                continue;

            el = (Element) n;

            av = getAttribute(el, an1);
            if (av == null || !av.equalsIgnoreCase(av1))
                continue;

            vel.add(el);
        }

        return vel;
    }

    static public Vector<Node> findElements(Vector<Node> pageFlat, String tagname, String an1, String av1, String an2, String av2)
            throws Exception
    {
        Vector<Node> vel = new Vector<Node>();
        Element el;
        String av;

        for (Node n : pageFlat)
        {
            if (!(n instanceof Element))
                continue;
            if (!n.getNodeName().equalsIgnoreCase(tagname))
                continue;

            el = (Element) n;

            av = getAttribute(el, an1);
            if (av == null || !av.equalsIgnoreCase(av1))
                continue;

            av = getAttribute(el, an2);
            if (av == null || !av.equalsIgnoreCase(av2))
                continue;

            vel.add(el);
        }

        return vel;
    }

    static public void removeElements(Node pageRoot, Vector<Node> vnodes) throws Exception
    {
        for (Node n : vnodes)
        {
            Node p = n.getParentNode();
            if (p != null && p != n)
                p.removeChild(n);
        }
    }

    static public void enumParents(Set<Node> parents, Node n) throws Exception
    {
        for (;;)
        {
            Node p = n.getParentNode();
            if (p == null || p == n)
                break;
            parents.add(p);
            n = p;
        }
    }

    static public void dumpNode(Node n) throws Exception
    {
        dumpNode(n, "*** ");
    }

    static public void dumpNode(Node n, String prefix) throws Exception
    {
        StringBuilder sb = new StringBuilder(n.getNodeName());
        String av;

        if (n instanceof Element)
        {
            Element el = (Element) n;
            if (null != (av = getAttribute(el, "class")))
                sb.append(" class=[").append(av).append("]");
            if (null != (av = getAttribute(el, "role")))
                sb.append(" role=[").append(av).append("]");
        }

        out(prefix + sb.toString());
    }

    static public void dumpNodeOffset(Node n) throws Exception
    {
        dumpNodeOffset(n, null);
    }

    static public void dumpNodeOffset(Node n, String comment) throws Exception
    {
        int level = 0;
        Node p;

        for (Node nx = n;; nx = p)
        {
            p = nx.getParentNode();
            if (p == null || p == nx)
                break;
            level++;
        }

        StringBuilder sb = new StringBuilder();
        while (level-- > 0)
            sb.append("    ");
        if (comment != null)
            sb.append(comment);
        dumpNode(n, sb.toString());
    }

    static public String getAttribute(Node n, String name) throws Exception
    {
        if (!(n instanceof Element))
            return null;

        Element el = (Element) n;

        String lc = name.toLowerCase();
        String uc = name.toUpperCase();

        if (el.hasAttribute(lc))
            return el.getAttribute(lc);
        else if (el.hasAttribute(uc))
            return el.getAttribute(uc);
        else
            return null;
    }

    static public String emitHtml(Node pageRoot) throws Exception
    {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty(OutputKeys.METHOD, "html");
        // transformer.setOutputProperty(OutputKeys.MEDIA_TYPE, "text/html")
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        // transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        StringWriter sw = new StringWriter();
        transformer.transform(new DOMSource(pageRoot), new StreamResult(sw));

        return sw.toString();
    }
}
