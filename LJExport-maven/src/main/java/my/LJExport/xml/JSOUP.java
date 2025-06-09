package my.LJExport.xml;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Vector;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Attributes;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;

import my.LJExport.Config;
import my.LJExport.Main;
import my.LJExport.runtime.Util;

public class JSOUP
{
    private static void out(String s) throws Exception
    {
        Main.out(s);
    }

    public static Element makeElement(String tagname, Node nref) throws Exception
    {
        return new Element(Tag.valueOf(tagname), nref.baseUri(), new Attributes());
    }

    public static Element makeElement(String tagname, String baseUri) throws Exception
    {
        return new Element(Tag.valueOf(tagname), baseUri, new Attributes());
    }

    public static Vector<Node> getChildren(Node n) throws Exception
    {
        return new Vector<Node>(n.childNodes());
    }

    public static Node getParent(Node n) throws Exception
    {
        Node p = n.parent();
        if (p == n)
            p = null;
        return p;
    }

    public static void addChild(Node parent, Node child) throws Exception
    {
        int nc = parent.childNodeSize();
        if (nc == 0)
            throw new Exception("Cannot add a child to a parent without children");
        parent.childNode(nc - 1).after(child);
    }

    public static String getAttribute(Node n, String name) throws Exception
    {
        if (!(n instanceof Element))
            return null;

        Element el = (Element) n;

        for (Attribute at : el.attributes().asList())
        {
            if (at.getKey().equalsIgnoreCase(name))
                return at.getValue();
        }

        return null;
    }

    public static void updateAttribute(Node n, String attrname, String value) throws Exception
    {
        for (Attribute at : n.attributes().asList())
        {
            if (at.getKey().equalsIgnoreCase(attrname))
                at.setValue(value);
        }
    }

    public static void setAttribute(Node n, String attrname, String value) throws Exception
    {
        n.attr(attrname, value);
    }

    public static Vector<Node> flattenChildren(Node el) throws Exception
    {
        Vector<Node> vec = flatten(el);
        if (vec.size() != 0 && vec.get(0) == el)
            vec.remove(0);
        return vec;
    }

    public static Vector<Node> flatten(Node el) throws Exception
    {
        Vector<Node> v1 = flatten_1(el);
        Vector<Node> v2 = flatten_2(el);

        /*
         * Verification
         */
        if (Config.True)
        {
            for (Node xn1 : v1)
            {
                if (!v2.contains(xn1))
                    throw new Exception("Bug in flatten #1");
            }

            for (Node xn2 : v2)
            {
                if (!v1.contains(xn2))
                    throw new Exception("Bug in flatten #2");
            }

            if (v1.size() != v2.size())
                throw new Exception("Bug in flatten #3");
        }

        return v1;
    }

    public static Vector<Node> flatten_1(Node el) throws Exception
    {
        Vector<Node> vec = new Vector<Node>();
        flatten_1(vec, el, 0);
        return vec;
    }

    private static void flatten_1(Vector<Node> vec, Node el, int level) throws Exception
    {
        if (el == null)
            return;
        vec.add(el);
        flatten_1(vec, firstChild(el), level + 1);
        if (level != 0)
            flatten_1(vec, el.nextSibling(), level + 1);
    }

    public static Node firstChild(Node n) throws Exception
    {
        List<Node> children = n.childNodes();
        if (children.size() == 0)
            return null;
        else
            return children.get(0);
    }

    /*
     * Alternative version for flattening
     */
    public static Vector<Node> flatten_2(Node root)
    {
        Vector<Node> result = new Vector<>();
        flatten_2(root, result);
        return result;
    }

    private static void flatten_2(Node node, Vector<Node> result)
    {
        if (node == null)
            return;

        // Visit current node
        result.add(node);

        // Recurse into children
        for (Node child : node.childNodes())
            flatten_2(child, result);
    }

    public static String nodeName(Node n) throws Exception
    {
        return n.nodeName();
    }

    public static Node nextSibling(Node n) throws Exception
    {
        return n.nextSibling();
    }

    public static Vector<Node> findElements(Node root, String tagname) throws Exception
    {
        return findElements(flatten(root), tagname);
    }

    public static Vector<Node> findElements(Vector<Node> pageFlat, String tagname) throws Exception
    {
        Vector<Node> vel = new Vector<Node>();

        for (Node n : pageFlat)
        {
            if (!(n instanceof Element))
                continue;
            if (!n.nodeName().equalsIgnoreCase(tagname))
                continue;

            vel.add(n);
        }

        return vel;
    }

    public static Vector<Node> findElements(Vector<Node> pageFlat, String tagname, String an1, String av1) throws Exception
    {
        Vector<Node> vel = new Vector<Node>();
        Element el;
        String av;

        for (Node n : pageFlat)
        {
            if (!(n instanceof Element))
                continue;
            if (!n.nodeName().equalsIgnoreCase(tagname))
                continue;

            el = (Element) n;

            av = getAttribute(el, an1);
            if (av == null || !av.equalsIgnoreCase(av1))
                continue;

            vel.add(el);
        }

        return vel;
    }

    public static Vector<Node> findElements(Vector<Node> pageFlat, String tagname, String an1, String av1, String an2, String av2)
            throws Exception
    {
        Vector<Node> vel = new Vector<Node>();
        Element el;
        String av;

        for (Node n : pageFlat)
        {
            if (!(n instanceof Element))
                continue;
            if (!n.nodeName().equalsIgnoreCase(tagname))
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

    public static Vector<Node> findElementsWithClass(Node root, String tagname, String cls) throws Exception
    {
        return findElementsWithClass(flatten(root), tagname, cls);
    }

    public static Vector<Node> findElementsWithClass(Vector<Node> pageFlat, String tagname, String cls) throws Exception
    {
        Vector<Node> vel = new Vector<Node>();
        Element el;

        @SuppressWarnings("unused")
        String av;

        for (Node n : pageFlat)
        {
            if (!(n instanceof Element))
                continue;
            if (tagname != null && !n.nodeName().equalsIgnoreCase(tagname))
                continue;

            el = (Element) n;

            av = getAttribute(el, "class");

            if (classContains(getAttribute(el, "class"), cls))
                vel.add(el);
        }

        return vel;
    }

    public static Vector<Node> findElementsWithAllClasses(Node root, String tagname, Set<String> classes) throws Exception
    {
        return findElementsWithAllClasses(flatten(root), tagname, classes);
    }

    public static Vector<Node> findElementsWithAllClasses(Vector<Node> pageFlat, String tagname, Set<String> classes)
            throws Exception
    {
        Vector<Node> vel = new Vector<Node>();
        Element el;

        @SuppressWarnings("unused")
        String av;

        for (Node n : pageFlat)
        {
            if (!(n instanceof Element))
                continue;
            if (tagname != null && !n.nodeName().equalsIgnoreCase(tagname))
                continue;

            el = (Element) n;

            av = getAttribute(el, "class");

            if (classContainsAll(getAttribute(el, "class"), classes))
                vel.add(el);
        }

        return vel;
    }

    public static boolean classContains(String clist, String cls) throws Exception
    {
        if (clist == null)
            return false;

        clist = clist.toLowerCase();
        cls = cls.toLowerCase();

        if (clist.indexOf(cls) == -1)
            return false;

        StringTokenizer st = new StringTokenizer(clist, " \t\r\n,");
        while (st.hasMoreTokens())
        {
            if (st.nextToken().equals(cls))
                return true;
        }

        return false;
    }

    public static boolean classContainsAll(String clist, Set<String> classes) throws Exception
    {
        if (clist == null)
            return false;

        Set<String> xs = new HashSet<>();

        clist = Util.despace(clist);

        for (String clz : clist.split(" "))
        {
            clz = clz.trim();
            if (clz.length() != 0)
                xs.add(clz.toLowerCase());
        }

        for (String clz : classes)
        {
            if (!xs.contains(clz.toLowerCase()))
                return false;
        }

        return true;
    }

    public static void removeElementsWithClass(Node pageRoot, String tagname, String cls) throws Exception
    {
        removeElementsWithClass(pageRoot, null, tagname, cls);
    }

    public static void removeElementsWithClass(Node pageRoot, Vector<Node> pageFlat, String tagname, String cls) throws Exception
    {
        if (pageFlat == null)
            pageFlat = flatten(pageRoot);
        Vector<Node> vel = findElementsWithClass(pageFlat, tagname, cls);
        removeElements(pageRoot, vel);
    }

    public static void removeElements(Node pageRoot, String tagname, String an1, String av1) throws Exception
    {
        removeElements(pageRoot, null, tagname, an1, av1);
    }

    public static void removeElements(Node pageRoot, Vector<Node> pageFlat, String tagname, String an1, String av1)
            throws Exception
    {
        if (pageFlat == null)
            pageFlat = flatten(pageRoot);
        Vector<Node> vel = findElements(pageFlat, tagname, an1, av1);
        removeElements(pageRoot, vel);
    }

    public static void removeElements(Node pageRoot, String tagname, String an1, String av1, String an2, String av2)
            throws Exception
    {
        removeElements(pageRoot, null, tagname, an1, av1, an2, av2);
    }

    public static void removeElements(Node pageRoot, Vector<Node> pageFlat, String tagname, String an1, String av1, String an2,
            String av2) throws Exception
    {
        if (pageFlat == null)
            pageFlat = flatten(pageRoot);
        Vector<Node> vel = findElements(pageFlat, tagname, an1, av1, an2, av2);
        removeElements(pageRoot, vel);
    }

    public static void removeElements(Node pageRoot, Vector<Node> vnodes) throws Exception
    {
        for (Node n : vnodes)
            n.remove();
    }

    public static void removeElement(Node pageRoot, Node node) throws Exception
    {
        node.remove();
    }

    public static void removeNodes(Vector<Node> vnodes) throws Exception
    {
        for (Node n : vnodes)
            n.remove();
    }

    public static void removeNode(Node n) throws Exception
    {
        n.remove();
    }

    public static void insertAfter(Node nold, Node nnew) throws Exception
    {
        nold.after(nnew);
    }

    public static void enumParents(Set<Node> parents, Node n) throws Exception
    {
        for (;;)
        {
            Node p = n.parent();
            if (p == null || p == n)
                break;
            parents.add(p);
            n = p;
        }
    }

    public static void dumpNodes(Vector<Node> vnodes, String prefix) throws Exception
    {
        for (Node n : vnodes)
            dumpNode(n, prefix);
    }

    public static void dumpNodesOffset(Vector<Node> vnodes) throws Exception
    {
        dumpNodesOffset(vnodes, null);
    }

    public static void dumpNodesOffset(Vector<Node> vnodes, String comment) throws Exception
    {
        for (Node n : vnodes)
            dumpNodeOffset(n, comment);
    }

    public static void dumpNode(Node n) throws Exception
    {
        dumpNode(n, "*** ");
    }

    public static void dumpNode(Node n, String prefix) throws Exception
    {
        StringBuilder sb = new StringBuilder(n.nodeName());
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

    public static void dumpNodeOffset(Node n) throws Exception
    {
        dumpNodeOffset(n, null);
    }

    public static void dumpNodeOffset(Node n, String comment) throws Exception
    {
        int level = 0;
        Node p;

        for (Node nx = n;; nx = p)
        {
            p = nx.parent();
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

    public static Vector<String> extractHrefs(String html) throws Exception
    {
        Node rootNode = parseHtml(html);
        return extractHrefs(rootNode);
    }

    public static Vector<String> extractHrefs(Node root) throws Exception
    {
        Vector<String> vs = new Vector<String>();
        Vector<Node> vnodes = flatten(root);

        for (Node n : vnodes)
        {
            if (!n.nodeName().equalsIgnoreCase("a"))
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

    public static Node parseHtml(String html) throws Exception
    {
        Document doc = Jsoup.parse(html);
        return doc;
    }

    public static String emitHtml(Node pageRoot) throws Exception
    {
        try
        {
            String html = pageRoot.outerHtml();
            return html;
        }
        catch (Exception ex)
        {
            throw ex;
        }
    }

    public static String filterOutImageTags(String html) // throws Exception
    {
        Document doc = Jsoup.parse(html);
        Elements imgTags = doc.select("img");

        // Replace the <img> element with a text node "<deleted-img>"
        for (org.jsoup.nodes.Element img : imgTags)
            img.replaceWith(new TextNode("<deleted-img>", ""));

        // in all elements with attribute data-auth-token
        // delete it or replace its value with empty
        try
        {
            for (Node n : flatten(doc))
            {
                if (!(n instanceof Element))
                    continue;

                Element el = (Element) n;

                if (getAttribute(el, "data-auth-token") != null)
                    el.removeAttr("data-auth-token");
            }

        }
        catch (Exception ex)
        {
            throw new RuntimeException(ex.getLocalizedMessage(), ex);
        }

        // Return only the body content (to avoid adding <html> and <head>)
        return doc.body().html();
    }

    public static String nodeText(Node node)
    {
        if (node instanceof TextNode)
        {
            return ((TextNode) node).text();
        }
        else if (node instanceof Element)
        {
            // includes nested text
            return ((Element) node).text();
        }
        else
        {
            StringBuilder sb = new StringBuilder();
            for (Node child : node.childNodes())
                sb.append(nodeText(child));
            return sb.toString();
        }
    }

    public static Set<String> getClasses(Node n) throws Exception
    {
        Set<String> xs = new HashSet<>();

        String classes = getAttribute(n, "class");
        if (classes == null)
            return xs;
        classes = Util.despace(classes);

        for (String clz : classes.split(" "))
        {
            clz = clz.trim();
            if (clz.length() != 0)
                xs.add(clz);
        }

        return xs;
    }

    public static Set<String> getClassesLowercase(Node n) throws Exception
    {
        return toLowerCase(getClasses(n));
    }

    public static Set<String> toLowerCase(Set<String> ss)
    {
        Set<String> xs = new HashSet<>();
        for (String s : ss)
            xs.add(s.toLowerCase());
        return xs;
    }
}
