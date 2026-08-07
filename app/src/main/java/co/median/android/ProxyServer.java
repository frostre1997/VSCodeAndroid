package co.median.android;

import android.util.Log;

import org.nanohttpd.protocols.http.IHTTPSession;
import org.nanohttpd.protocols.http.NanoHTTPD;
import org.nanohttpd.protocols.http.response.Response;
import org.nanohttpd.protocols.http.response.Status;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ProxyServer extends NanoHTTPD {
    private static final String TAG = "ProxyServer";
    private static final String TARGET_HOST = "https://vscode.dev";

    public ProxyServer() {
        super(8080);
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        String targetUrl = TARGET_HOST + uri;

        if (uri.equals("/")) {
            targetUrl = TARGET_HOST + "/";
        }

        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(targetUrl).openConnection();
            connection.setRequestMethod("GET");

            // Full desktop Chrome headers
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36");
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7");
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            connection.setRequestProperty("Accept-Encoding", "gzip, deflate, br");
            connection.setRequestProperty("Sec-Fetch-Site", "none");
            connection.setRequestProperty("Sec-Fetch-Mode", "navigate");
            connection.setRequestProperty("Sec-Fetch-User", "?1");
            connection.setRequestProperty("Sec-Fetch-Dest", "document");
            connection.setRequestProperty("Upgrade-Insecure-Requests", "1");
            connection.setRequestProperty("Cache-Control", "max-age=0");
            connection.setRequestProperty("Connection", "keep-alive");
            connection.setRequestProperty("Origin", TARGET_HOST);
            connection.setRequestProperty("Referer", TARGET_HOST + "/");

            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);

            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                InputStream inputStream = connection.getInputStream();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int len;
                while ((len = inputStream.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
                byte[] responseBody = baos.toByteArray();
                String contentType = connection.getContentType();
                if (contentType == null) contentType = "text/html";

                return newFixedLengthResponse(Status.OK, contentType, new String(responseBody, "UTF-8"));
            } else {
                Log.e(TAG, "Error response: " + responseCode);
                return newFixedLengthResponse(Status.INTERNAL_ERROR, "text/plain", "Proxy error: Server returned " + responseCode);
            }
        } catch (Exception e) {
            Log.e(TAG, "Proxy exception: " + e.getMessage());
            return newFixedLengthResponse(Status.INTERNAL_ERROR, "text/plain", "Proxy error: " + e.getMessage());
        }
    }
                }
