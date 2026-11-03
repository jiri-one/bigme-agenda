package one.jiri.bigmeagenda;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

public final class DavXml {
    private static final String DAV = "DAV:";
    private static final String CAL = "urn:ietf:params:xml:ns:caldav";
    private DavXml() {}

    public static List<String> calendars(byte[] xml) throws AgendaException {
        if (xml.length > CalDavClient.MAX_BYTES) throw new AgendaException(AgendaException.Reason.SIZE);
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XmlPullParser parser = factory.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false);
            parser.setInput(new ByteArrayInputStream(xml), null);
            List<Node> stack = new ArrayList<>();
            Node root = null;
            int nodes = 0;
            for (int token = parser.nextToken(); token != XmlPullParser.END_DOCUMENT; token = parser.nextToken()) {
                if (token == XmlPullParser.DOCDECL || (token == XmlPullParser.ENTITY_REF && parser.getText() == null)) fail();
                if (token == XmlPullParser.START_TAG) {
                    if (++nodes > 20000 || stack.size() >= 16) fail();
                    Node node = new Node(parser.getNamespace(), parser.getName());
                    if (node.is(DAV, "error")) fail();
                    if (stack.isEmpty()) { if (root != null) fail(); root = node; }
                    else stack.get(stack.size() - 1).children.add(node);
                    stack.add(node);
                } else if (token == XmlPullParser.END_TAG) {
                    if (stack.isEmpty()) fail();
                    stack.remove(stack.size() - 1);
                } else if (token == XmlPullParser.TEXT || token == XmlPullParser.CDSECT || token == XmlPullParser.ENTITY_REF) {
                    if (!stack.isEmpty()) stack.get(stack.size() - 1).text.append(parser.getText());
                }
            }
            if (root == null || !root.is(DAV, "multistatus") || !stack.isEmpty() || !root.text.toString().trim().isEmpty()) fail();
            List<String> calendars = new ArrayList<>();
            for (Node response : root.children) {
                if (response.is(DAV, "responsedescription")) continue;
                if (!response.is(DAV, "response")) fail();
                List<String> found = new ArrayList<>();
                for (Node part : response.children) {
                    if (part.is(DAV, "status") && !ok(part.text.toString())) fail();
                    if (!part.is(DAV, "propstat")) continue;
                    Node status = part.one(DAV, "status");
                    if (status == null || !ok(status.text.toString())) fail();
                    Node prop = part.one(DAV, "prop");
                    if (prop == null) fail();
                    for (Node data : prop.children) {
                        if (data.is(CAL, "calendar-data")) {
                            if (!data.children.isEmpty() || data.text.toString().trim().isEmpty()) fail();
                            found.add(data.text.toString());
                        }
                    }
                }
                if (found.size() != 1) fail();
                calendars.add(found.get(0));
            }
            return calendars;
        } catch (AgendaException e) { throw e; }
        catch (Exception e) { throw new AgendaException(AgendaException.Reason.XML); }
    }
    private static boolean ok(String status) { return status.trim().matches("HTTP/\\d(?:\\.\\d)? 200(?: .*|$)"); }
    private static void fail() throws AgendaException { throw new AgendaException(AgendaException.Reason.XML); }
    private static final class Node {
        final String ns, name;
        final StringBuilder text = new StringBuilder();
        final List<Node> children = new ArrayList<>();
        Node(String ns, String name) { this.ns = ns; this.name = name; }
        boolean is(String ns, String name) { return this.ns.equals(ns) && this.name.equals(name); }
        Node one(String ns, String name) throws AgendaException {
            Node result = null;
            for (Node child : children) if (child.is(ns, name)) { if (result != null) fail(); result = child; }
            return result;
        }
    }
}
