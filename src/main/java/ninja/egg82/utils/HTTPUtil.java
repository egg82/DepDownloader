package ninja.egg82.utils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class HTTPUtil {
    private HTTPUtil() {}

    public static List<URL> toURLs(Collection<URI> uris) throws MalformedURLException {
        List<URL> retVal = new ArrayList<>();
        for (URI uri : uris) {
            retVal.add(uri.toURL());
        }
        return retVal;
    }

    public static void downloadFile(URL url, File output) throws IOException {
        try (BufferedInputStream in = new BufferedInputStream(getInputStream(url)); FileOutputStream fileOutputStream = new FileOutputStream(output)) {
            byte[] dataBuffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                fileOutputStream.write(dataBuffer, 0, bytesRead);
            }
        }
    }

    public static void downloadFile(List<URL> urls, File output) throws IOException {
        try (BufferedInputStream in = new BufferedInputStream(getInputStream(urls)); FileOutputStream fileOutputStream = new FileOutputStream(output)) {
            byte[] dataBuffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                fileOutputStream.write(dataBuffer, 0, bytesRead);
            }
        }
    }

    public static HttpURLConnection getConnection(URL url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        int status;
        boolean redirect;

        do {
            status = conn.getResponseCode();
            redirect = status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_SEE_OTHER;

            if (redirect) {
                String newUrl = conn.getHeaderField("Location");
                String cookies = conn.getHeaderField("Set-Cookie");

                conn = (HttpURLConnection) new URL(newUrl).openConnection();
                conn.setRequestProperty("Cookie", cookies);
                conn.addRequestProperty("Accept-Language", "en-US,en;q=0.8");
            }
        } while (redirect);

        return conn;
    }

    /**
     * Returns an HttpURLConnection from the first URL to present one without error.
     * This method follows redirects.
     *
     * @param urls The URLs to get the connection from, in order
     * @return An HttpURLConnection from one of the URLs
     * @throws IOException If all URLs errored, the last error presented
     */
    public static HttpURLConnection getConnection(List<URL> urls) throws IOException {
        IOException lastEx = null;
        int lastStatus = -1;
        boolean is404 = false;
        for (URL url : urls) {
            try {
                HttpURLConnection conn = getConnection(url);
                int status = conn.getResponseCode();
                if ((status >= 200 && status < 300) || status == 304) {
                    return conn;
                }
                if (status != 404) {
                    lastStatus = status;
                } else {
                    is404 = true;
                }
            } catch (IOException ex) {
                lastEx = ex;
            }
        }
        if (lastEx != null) {
            throw new IOException("Could not get connection from URLs provided.", lastEx);
        }

        if ((lastStatus == -1 && is404) || (lastStatus >= 400 && lastStatus < 600)) {
            throw new IOException("Server returned status code " + lastStatus);
        }
        throw new IOException("Could not get connection from URLs provided.");
    }

    public static boolean remoteExists(List<URL> urls) {
        for (URL url : urls) {
            try {
                HttpURLConnection conn = getConnection(url);
                int status = conn.getResponseCode();
                if ((status >= 200 && status < 300) || status == 304) {
                    return true;
                }
            } catch (IOException ignored) { }
        }

        return false;
    }

    public static InputStream getInputStream(URL url) throws IOException {
        HttpURLConnection conn = getConnection(url);
        int status = conn.getResponseCode();

        if (status >= 400 && status < 600) {
            // 400-500 errors
            throw new IOException("Server returned status code " + status);
        }

        return conn.getInputStream();
    }

    public static InputStream getInputStream(List<URL> urls) throws IOException { return getConnection(urls).getInputStream(); }
}
