package my.LJExport.xml;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Attributes;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
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

    public static List<Node> getChildren(Node n) throws Exception
    {
        return new ArrayList<>(n.childNodes());
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

    public static List<Node> flattenChildren(Node el) throws Exception
    {
        List<Node> vec = flatten(el);
        if (vec.size() != 0 && vec.get(0) == el)
            vec.remove(0);
        return vec;
    }

    public static List<Node> flatten(Node el) throws Exception
    {
        List<Node> v1 = flatten_1(el);

        /*
         * Verification
         */
        if (Config.False)
        {
            List<Node> v2 = flatten_2(el);

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

    public static List<Node> flatten_1(Node el) throws Exception
    {
        List<Node> vec = new ArrayList<>();
        flatten_1(vec, el, 0);
        return vec;
    }

    private static void flatten_1(List<Node> vec, Node el, int level) throws Exception
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
    public static List<Node> flatten_2(Node root)
    {
        List<Node> result = new ArrayList<>();
        flatten_2(root, result);
        return result;
    }

    private static void flatten_2(Node node, List<Node> result)
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

    public static List<Node> findComments(Node root) throws Exception
    {
        return findComments(flatten(root));
    }

    public static List<Node> findComments(List<Node> pageFlat) throws Exception
    {
        List<Node> vel = new ArrayList<>();

        for (Node n : pageFlat)
        {
            if (n instanceof Comment)
                vel.add(n);
        }

        return vel;
    }

    public static List<Node> findElements(Node root, String tagname) throws Exception
    {
        return findElements(flatten(root), tagname);
    }

    public static List<Node> findElements(List<Node> pageFlat, String tagname) throws Exception
    {
        List<Node> vel = new ArrayList<>();

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

    public static List<Node> findElements(Node root, String tagname, String an1, String av1) throws Exception
    {
        return findElements(flatten(root), tagname, an1, av1);
    }

    public static List<Node> findElements(List<Node> pageFlat, String tagname, String an1, String av1) throws Exception
    {
        List<Node> vel = new ArrayList<>();
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

    public static List<Node> findElements(List<Node> pageFlat, String tagname, String an1, String av1, String an2, String av2)
            throws Exception
    {
        List<Node> vel = new ArrayList<>();
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

    public static List<Node> findElementsWithClass(Node root, String tagname, String cls) throws Exception
    {
        return findElementsWithClass(flatten(root), tagname, cls);
    }

    public static List<Node> findElementsWithClass(List<Node> pageFlat, String tagname, String cls) throws Exception
    {
        List<Node> vel = new ArrayList<>();
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

    public static List<Node> findElementsWithAllClasses(Node root, String tagname, Set<String> classes) throws Exception
    {
        return findElementsWithAllClasses(flatten(root), tagname, classes);
    }

    public static List<Node> findElementsWithAllClasses(List<Node> pageFlat, String tagname, Set<String> classes)
            throws Exception
    {
        List<Node> vel = new ArrayList<>();
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

    public static void removeElementsWithClass(Node pageRoot, List<Node> pageFlat, String tagname, String cls) throws Exception
    {
        if (pageFlat == null)
            pageFlat = flatten(pageRoot);
        List<Node> vel = findElementsWithClass(pageFlat, tagname, cls);
        removeElements(pageRoot, vel);
    }

    public static void removeElements(Node pageRoot, String tagname, String an1, String av1) throws Exception
    {
        removeElements(pageRoot, null, tagname, an1, av1);
    }

    public static void removeElements(Node pageRoot, List<Node> pageFlat, String tagname, String an1, String av1)
            throws Exception
    {
        if (pageFlat == null)
            pageFlat = flatten(pageRoot);
        List<Node> vel = findElements(pageFlat, tagname, an1, av1);
        removeElements(pageRoot, vel);
    }

    public static void removeElements(Node pageRoot, String tagname, String an1, String av1, String an2, String av2)
            throws Exception
    {
        removeElements(pageRoot, null, tagname, an1, av1, an2, av2);
    }

    public static void removeElements(Node pageRoot, List<Node> pageFlat, String tagname, String an1, String av1, String an2,
            String av2) throws Exception
    {
        if (pageFlat == null)
            pageFlat = flatten(pageRoot);
        List<Node> vel = findElements(pageFlat, tagname, an1, av1, an2, av2);
        removeElements(pageRoot, vel);
    }

    public static void removeElements(Node pageRoot, List<Node> vnodes) throws Exception
    {
        for (Node n : vnodes)
            n.remove();
    }

    public static void removeElement(Node pageRoot, Node node) throws Exception
    {
        node.remove();
    }

    public static void removeNodes(List<Node> vnodes) throws Exception
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

    public static void dumpNodes(List<Node> vnodes, String prefix) throws Exception
    {
        for (Node n : vnodes)
            dumpNode(n, prefix);
    }

    public static void dumpNodesOffset(List<Node> vnodes) throws Exception
    {
        dumpNodesOffset(vnodes, null);
    }

    public static void dumpNodesOffset(List<Node> vnodes, String comment) throws Exception
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

    public static List<String> extractHrefs(String html) throws Exception
    {
        Node rootNode = parseHtml(html);
        return extractHrefs(rootNode);
    }

    public static List<String> extractHrefs(Node root) throws Exception
    {
        List<String> vs = new ArrayList<String>();
        List<Node> vnodes = flatten(root);

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

    public static boolean hasParent(Node n, String tagname)
    {
        for (Node p = n.parent(); p != null && p != n; n = p)
        {
            if (p instanceof Element && p.nodeName().equalsIgnoreCase(tagname))
                return true;
        }

        return false;
    }

    public static List<Node> union(Collection<Node> c1, Collection<Node> c2)
    {
        List<Node> vn = new ArrayList<>();

        for (Node n : c1)
        {
            if (!vn.contains(n))
                vn.add(n);
        }

        for (Node n : c2)
        {
            if (!vn.contains(n))
                vn.add(n);
        }

        return vn;
    }

    public static void removeWhitespaceNodes(Node node)
    {
        for (int i = 0; i < node.childNodeSize(); i++)
        {
            Node child = node.childNode(i);
            if (child instanceof TextNode)
            {
                TextNode textNode = (TextNode) child;
                if (textNode.isBlank())
                {
                    child.remove();
                    i--; // adjust index after removal
                }
            }
            else
            {
                removeWhitespaceNodes(child);
            }
        }
    }

    public static List<Node> parseFragment(String html, Element context, String baseUri)
    {
        return Parser.parseFragment(html, context, baseUri);
    }

    public static List<Node> parseBodyFragment(String html)
    {
        List<Node> result = new ArrayList<>();
        Element dummy = Jsoup.parseBodyFragment(html).body();
        for (Node n : dummy.childNodes())
            result.add(n.clone());
        return result;    
    }
}