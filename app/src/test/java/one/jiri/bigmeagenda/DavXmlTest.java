package one.jiri.bigmeagenda;

import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Test;

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 34, manifest = org.robolectric.annotation.Config.NONE)
public class DavXmlTest {
    static String multistatus(String body) { return "<d:multistatus xmlns:d=\"DAV:\" xmlns:c=\"urn:ietf:params:xml:ns:caldav\">" + body + "</d:multistatus>"; }
    static String response(String data, int code) { return "<d:response><d:href>/synthetic.ics</d:href><d:propstat><d:prop><c:calendar-data>" + data + "</c:calendar-data></d:prop><d:status>HTTP/1.1 " + code + " Result</d:status></d:propstat></d:response>"; }
    static List<String> parse(String xml) throws AgendaException { return DavXml.calendars(xml.getBytes(StandardCharsets.UTF_8)); }
    @Test public void acceptsEmptyCollectionAndNamespaceAliases() throws Exception {
        assertTrue(parse(multistatus("")).isEmpty());
        assertEquals(List.of("A & B <C>"), parse(multistatus(response("A &amp; B &lt;C&gt;", 200))));
        assertEquals(List.of("data"), parse(multistatus(response("<![CDATA[data]]>", 200)).replace("d:", "x:").replace("xmlns:d", "xmlns:x")));
    }
    @Test public void rejectsPartialResponsesAndMissingCalendarData() {
        for (String body : List.of(response("valid",200) + response("error",404), "<d:response><d:status>HTTP/1.1 403 Forbidden</d:status></d:response>",
                "<d:response><d:propstat><d:prop/><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>", "<d:error/>", response("",200))) {
            assertThrows(AgendaException.class, () -> parse(multistatus(body)));
        }
    }
    @Test public void rejectsDoctypeAndExternalOrInternalEntities() {
        for (String dtd : List.of("<!DOCTYPE x SYSTEM 'https://invalid.example/no'>", "<!DOCTYPE x [<!ENTITY a SYSTEM 'file:///nonexistent'>]>",
                "<!DOCTYPE x [<!ENTITY a 'boom'><!ENTITY b '&a;&a;'>]>")) {
            assertThrows(AgendaException.class, () -> parse(dtd + multistatus(response("&a;",200))));
        }
        assertThrows(AgendaException.class, () -> parse(multistatus(response("&missing;",200))));
    }
    @Test public void rejectsWrongRootTruncationAndExcessiveDepth() {
        assertThrows(AgendaException.class, () -> parse("<html/>"));
        assertThrows(AgendaException.class, () -> parse("<d:multistatus xmlns:d=\"DAV:\">"));
        assertThrows(AgendaException.class, () -> parse(multistatus("<d:x>".repeat(20) + "</d:x>".repeat(20))));
    }
    @Test public void boundsResponseBytes() {
        AgendaException e = assertThrows(AgendaException.class, () -> DavXml.calendars(new byte[CalDavClient.MAX_BYTES + 1]));
        assertEquals(AgendaException.Reason.SIZE, e.reason);
    }
}
