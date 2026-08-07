package co.median.android;

import android.util.Log;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;

public class ProxyServer {
    private static final String TAG = "ProxyServer";
    private static final String TARGET_HOST = "https://vscode.dev";
    private HttpServer server;
    private int port = 8080;

    public ProxyServer() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", new ProxyHandler());
            server.setExecutor(null);
        } catch (Exception e) {
            Log.e(TAG, "Failed to create server: " + e.getMessage());
        }
    }

    public void start() {
        if (server != null) {
            server.start();
            Log.d(TAG, "Proxy server started on port " + port);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    public int getPort() {
        return port;
    }

    private class ProxyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws java.io.IOException {
            String path = exchange.getRequestURI().getPath();
            String targetUrl = TARGET_HOST + path;

            if (path.equals("/")) {
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

                    exchange.getResponseHeaders().set("Content-Type", contentType);
                    exchange.sendResponseHeaders(200, responseBody.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(responseBody);
                    os.close();
                } else {
                    String error = "Proxy error: Server returned " + responseCode;
                    exchange.sendResponseHeaders(500, error.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(error.getBytes());
                    os.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "Proxy exception: " + e.getMessage());
                String error = "Proxy error: " + e.getMessage();
                exchange.sendResponseHeaders(500, error.length());
                OutputStream os = exchange.getResponseBody();
                os.write(error.getBytes());
                os.close();
            }
        }
    }
                        }
