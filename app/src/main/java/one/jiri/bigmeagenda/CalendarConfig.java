package one.jiri.bigmeagenda;

import okhttp3.HttpUrl;

public final class CalendarConfig {
    public final String url;
    public final String user;
    public final String password;

    public CalendarConfig(String url, String user, String password) throws AgendaException {
        HttpUrl parsed = HttpUrl.parse(url.trim());
        if (parsed == null || !parsed.isHttps() || !parsed.username().isEmpty()
                || !parsed.password().isEmpty() || parsed.query() != null || parsed.fragment() != null
                || !parsed.encodedPath().endsWith("/") || user.trim().isEmpty()
                || user.contains(":") || user.contains("\n") || user.contains("\r")
                || password.isEmpty() || url.length() > 2048 || user.length() > 256
                || password.length() > 4096) {
            throw new AgendaException(AgendaException.Reason.CONFIG);
        }
        this.url = parsed.toString();
        this.user = user.trim();
        this.password = password;
    }
}
