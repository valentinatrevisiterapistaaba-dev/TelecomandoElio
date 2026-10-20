package it.valentina.telecomandoelio;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class FullyClient {
    private FullyClient() {}

    public static Result send(Context context, String ip, String password, String command) {
        String last = "Errore sconosciuto";
        for (int attempt = 1; attempt <= 3; attempt++) {
            HttpURLConnection c = null;
            try {
                String pw = URLEncoder.encode(password, StandardCharsets.UTF_8.name());
                URL u = new URL("http://" + ip + ":2323/" + command
                        + "?password=" + pw + "&_=" + System.currentTimeMillis());

                c = openPreferWifi(context, u);
                c.setRequestMethod("GET");
                c.setConnectTimeout(1800);
                c.setReadTimeout(2400);
                c.setUseCaches(false);

                int code = c.getResponseCode();
                InputStream in = code >= 200 && code < 400 ? c.getInputStream() : c.getErrorStream();
                String body = readShort(in);

                if (code >= 200 && code < 400 && !looksLikeError(body)) {
                    return new Result(true, code, body, attempt);
                }

                last = code >= 200 && code < 400
                        ? (body.isEmpty() ? "Risposta non valida da Fully" : body)
                        : "HTTP " + code + (body.isEmpty() ? "" : " – " + body);

            } catch (Exception e) {
                last = e.getClass().getSimpleName() + ": "
                        + (e.getMessage() == null ? "" : e.getMessage());
            } finally {
                if (c != null) c.disconnect();
            }

            try {
                Thread.sleep(450L * attempt);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        return new Result(false, -1, last, 3);
    }

    private static HttpURLConnection openPreferWifi(Context context, URL url) throws Exception {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm != null) {
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(n);
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    return (HttpURLConnection) n.openConnection(url);
                }
            }
        }

        throw new java.io.IOException(
                "Wi-Fi non disponibile: collega il telecomando alla stessa Wi-Fi del telefono controllato"
        );
    }

    private static boolean looksLikeError(String body) {
        String s = body == null ? "" : body.toLowerCase(Locale.ROOT);
        return s.contains("missing admin rights")
                || s.contains("wrong password")
                || s.contains("password is wrong")
                || s.contains("please set the remote admin password")
                || s.contains("not allowed")
                || s.contains("permission denied");
    }

    private static String readShort(InputStream in) {
        if (in == null) return "";

        try (BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
            StringBuilder sb = new StringBuilder();
            String line;

            while ((line = br.readLine()) != null && sb.length() < 1200) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(line.trim());
            }

            String s = sb.toString()
                    .replaceAll("<[^>]*>", " ")
                    .replaceAll("\\s+", " ")
                    .trim();

            return s.length() > 300 ? s.substring(0, 300) : s;

        } catch (Exception e) {
            return "";
        }
    }

    public static final class Result {
        public final boolean ok;
        public final int httpCode;
        public final String message;
        public final int attempts;

        Result(boolean ok, int httpCode, String message, int attempts) {
            this.ok = ok;
            this.httpCode = httpCode;
            this.message = message;
            this.attempts = attempts;
        }
    }
}
