package one.jiri.bigmeagenda;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class CalDavClient {
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private final OkHttpClient client;

    public CalDavClient() { this(new OkHttpClient()); }
    CalDavClient(OkHttpClient client) {
        this.client = client.newBuilder().followRedirects(false).followSslRedirects(false)
                .retryOnConnectionFailure(false).connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS).build();
    }
    public List<String> fetch(CalendarConfig config, Instant from, Instant until) throws IOException {
        Request request = new Request.Builder().url(config.url)
                .header("User-Agent", "BigmeAgenda/1.0 (Android; CalDAV)")
                .header("Authorization", Credentials.basic(config.user, config.password, StandardCharsets.UTF_8))
                .header("Depth", "1").header("Accept", "application/xml, text/xml")
                .method("REPORT", RequestBody.create(report(from, until), MediaType.get("application/xml; charset=utf-8")))
                .build();
        try (Response response = client.newCall(request).execute()) {
            int code = response.code();
            if (code == 401 || code == 403) throw new AgendaException(AgendaException.Reason.AUTH);
            if (code >= 300 && code < 400) throw new AgendaException(AgendaException.Reason.REDIRECT);
            if (code != 207) throw new AgendaException(AgendaException.Reason.HTTP, code == 429 || code >= 500);
            ResponseBody body = response.body();
            if (body == null) throw new AgendaException(AgendaException.Reason.XML);
            if (body.contentLength() > MAX_BYTES) throw new AgendaException(AgendaException.Reason.SIZE);
            return DavXml.calendars(readBounded(body.byteStream()));
        }
    }
    static String report(Instant from, Instant until) {
        if (!until.isAfter(from)) throw new IllegalArgumentException("range");
        String range = " start=\"" + UTC.format(from) + "\" end=\"" + UTC.format(until) + "\"";
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<c:calendar-query xmlns:d=\"DAV:\" xmlns:c=\"urn:ietf:params:xml:ns:caldav\">"
                + "<d:prop><c:calendar-data><c:expand" + range + "/></c:calendar-data></d:prop>"
                + "<c:filter><c:comp-filter name=\"VCALENDAR\"><c:comp-filter name=\"VEVENT\">"
                + "<c:time-range" + range + "/></c:comp-filter></c:comp-filter></c:filter></c:calendar-query>";
    }
    static byte[] readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (out.size() + count > MAX_BYTES) throw new AgendaException(AgendaException.Reason.SIZE);
            out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }
}
