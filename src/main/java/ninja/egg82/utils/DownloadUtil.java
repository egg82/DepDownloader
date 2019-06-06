package ninja.egg82.utils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;

public class DownloadUtil {
    private DownloadUtil() {}

    public static File getOrDownloadFile(File output, List<URL> urls) throws IOException {
        if (output.exists() && output.isDirectory()) {
            Files.delete(output.toPath());
        }

        createDirectory(output.getParentFile());

        if (!output.exists()) {
            IOException lastEx = null;
            for (URL url : urls) {
                try {
                    System.out.println("Trying: " + url);
                    HTTPUtil.downloadFile(url, output);
                    return output;
                } catch (IOException ex) {
                    lastEx = ex;
                }
            }
            if (lastEx != null) {
                throw new IOException("Could not download file from URLs provided.", lastEx);
            }
        }

        return output;
    }

    public static boolean hasFile(File file) {
        if (file == null) {
            return false;
        }

        if (file.exists()) {
            return !file.isDirectory();
        }
        return false;
    }

    public static boolean hasDirectory(File file) {
        if (file == null) {
            return false;
        }

        if (file.exists()) {
            return file.isDirectory();
        }
        return false;
    }

    public static void createDirectory(File file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("file cannot be null.");
        }

        if (file.exists()) {
            if (!file.isDirectory()) {
                Files.delete(file.toPath());
            } else {
                return;
            }
        }
        if (!file.mkdirs()) {
            throw new IOException("Could not create directory structure.");
        }
    }
}
