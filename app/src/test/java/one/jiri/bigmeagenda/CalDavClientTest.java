package one.jiri.bigmeagenda;

import static org.junit.Assert.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;
import okio.Buffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 34, manifest = org.robolectric.annotation.Config.NONE)
public class CalDavClientTest {
    private MockWebServer server;
    private CalDavClient client;
    private final Instant from = Instant.parse("2026-11-03T00:00:00Z"), until = Instant.parse("2026-11-10T00:00:00Z");
    @Before public void setup() throws Exception {
        HeldCertificate certificate = new HeldCertificate.Builder().addSubjectAlternativeName("localhost").build();
        HandshakeCertificates serverTls = new HandshakeCertificates.Builder().heldCertificate(certificate).build();
        HandshakeCertificates clientTls = new HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate()).build();
        server = new MockWebServer(); server.useHttps(serverTls.sslSocketFactory(), false); server.start();
        client = new CalDavClient(new OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager()).build());
    }
    @After public void cleanup() throws Exception { server.shutdown(); }
    private CalendarConfig config() throws Exception { return new CalendarConfig(server.url("/calendar/").toString(), "test", "synthetic"); }
    @Test public void sendsOnlyReportWithBoundedExpansionAndDepthOne() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(207).setBody(DavXmlTest.multistatus("")));
        assertTrue(client.fetch(config(), from, until).isEmpty());
        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(request); assertEquals("REPORT", request.getMethod()); assertEquals("1", request.getHeader("Depth"));
        assertEquals("BigmeAgenda/1.0 (Android; CalDAV)", request.getHeader("User-Agent"));
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("<c:expand start=\"20261103T000000Z\" end=\"20261110T000000Z\"/>"));
        assertTrue(body.contains("<c:time-range start=\"20261103T000000Z\" end=\"20261110T000000Z\"/>"));
        assertEquals(1, server.getRequestCount());
    }
    @Test public void redirectsNeverForwardCredentialsOrGenerateFollowUpRequests() throws Exception {
        try (MockWebServer other = new MockWebServer()) {
            other.start();
            for (String target : new String[]{other.url("/other/").toString(), server.url("/other/").toString()}) {
                server.enqueue(new MockResponse().setResponseCode(307).setHeader("Location", target));
                AgendaException error = assertThrows(AgendaException.class, () -> client.fetch(config(), from, until));
                assertEquals(AgendaException.Reason.REDIRECT, error.reason);
            }
            assertEquals(0, other.getRequestCount()); assertEquals(2, server.getRequestCount());
        }
    }
    @Test public void reportsAuthAndNon207FailuresWithoutLeakingBody() throws Exception {
        for (int status : new int[]{200, 401, 403, 429, 500, 503}) {
            server.enqueue(new MockResponse().setResponseCode(status).setBody("synthetic private server error"));
            AgendaException error = assertThrows(AgendaException.class, () -> client.fetch(config(), from, until));
            assertFalse(error.getMessage().contains("private"));
            assertEquals(status == 429 || status >= 500, error.retryable);
        }
    }
    @Test public void refusesCompressedResponseOverLimitAfterDecompression() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) { gzip.write(new byte[CalDavClient.MAX_BYTES + 1]); }
        server.enqueue(new MockResponse().setResponseCode(207).setHeader("Content-Encoding", "gzip").setBody(new Buffer().write(bytes.toByteArray())));
        AgendaException error = assertThrows(AgendaException.class, () -> client.fetch(config(), from, until));
        assertEquals(AgendaException.Reason.SIZE, error.reason);
    }
    @Test public void responseSizeBoundaryIncludesLastByte() throws Exception {
        assertEquals(CalDavClient.MAX_BYTES, CalDavClient.readBounded(new ByteArrayInputStream(new byte[CalDavClient.MAX_BYTES])).length);
        assertThrows(AgendaException.class, () -> CalDavClient.readBounded(new ByteArrayInputStream(new byte[CalDavClient.MAX_BYTES + 1])));
    }
    @Test public void defaultClientRejectsUntrustedCertificates() throws Exception {
        assertThrows(java.io.IOException.class, () -> new CalDavClient().fetch(config(), from, until));
        assertEquals(0, server.getRequestCount());
    }
    @Test public void configRejectsCleartextCredentialsQueriesAndNonCollectionPaths() {
        for (String url : new String[]{"http://example.test/calendar/", "https://name:secret@example.test/calendar/",
                "https://example.test/calendar/?token=synthetic", "https://example.test/event.ics", "https://example.test/calendar/#fragment"}) {
            assertThrows(AgendaException.class, () -> new CalendarConfig(url, "jiri", "synthetic"));
        }
    }
}
