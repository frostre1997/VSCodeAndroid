package co.median.android;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;

public class ProxyServer extends Thread {
    private static final String TAG = "ProxyServer";
    private static final String TARGET_HOST = "https://vscode.dev";
    private ServerSocket serverSocket;
    private boolean running = false;

    public ProxyServer() {
        try {
            serverSocket = new ServerSocket(8080);
        } catch (Exception e) {
            Log.e(TAG, "Failed to create server socket: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        running = true;
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                handleClient(clientSocket);
            } catch (Exception e) {
                if (running) {
                    Log.e(TAG, "Error accepting connection: " + e.getMessage());
                }
            }
        }
    }

    private void handleClient(Socket clientSocket) {
        try {
            // Read the request from the client (WebView)
            InputStream in = clientSocket.getInputStream();
            ByteArrayOutputStream requestBuffer = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                requestBuffer.write(buffer, 0, len);
                // Check if we've read the entire request (HTTP headers end with \r\n\r\n)
                if (requestBuffer.toString().contains("\r\n\r\n")) {
                    break;
                }
            }
            String request = requestBuffer.toString();
            // Extract the path from the request (e.g., GET /some/path HTTP/1.1)
            String[] lines = request.split("\r\n");
            String[] requestLineParts = lines[0].split(" ");
            String path = requestLineParts[1];

            // Build the target URL
            String targetUrl = TARGET_HOST + path;
            if (path.equals("/")) {
                targetUrl = TARGET_HOST + "/";
            }

            // Forward the request to the real server with desktop Chrome headers
            HttpURLConnection connection = (HttpURLConnection) new URL(targetUrl).openConnection();
            connection.setRequestMethod("GET");
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
            InputStream responseStream = (responseCode >= 200 && responseCode < 400) ? connection.getInputStream() : connection.getErrorStream();
            ByteArrayOutputStream responseBuffer = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int bytesRead;
            while ((bytesRead = responseStream.read(buf)) != -1) {
                responseBuffer.write(buf, 0, bytesRead);
            }
            byte[] responseBody = responseBuffer.toByteArray();

            // Build HTTP response for the client
            String contentType = connection.getContentType();
            if (contentType == null) contentType = "text/html";
            String statusLine = "HTTP/1.1 " + responseCode + " OK\r\n";
            String headers = "Content-Type: " + contentType + "\r\n";
            headers += "Content-Length: " + responseBody.length + "\r\n";
            headers += "Connection: close\r\n\r\n";
            String fullResponse = statusLine + headers;

            OutputStream out = clientSocket.getOutputStream();
            out.write(fullResponse.getBytes("UTF-8"));
            out.write(responseBody);
            out.flush();
            out.close();
            clientSocket.close();

        } catch (Exception e) {
            Log.e(TAG, "Proxy handler error: " + e.getMessage());
            try {
                clientSocket.close();
            } catch (Exception ignored) {}
        }
    }

    public void startServer() {
        this.start();
    }

    public void stopServer() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (Exception ignored) {}
    }
                                          }
