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

        if (!output.exists()) {
            IOException lastEx = null;
            boolean good = false;
            for (URL url : urls) {
                try {
                    HTTPUtil.downloadFile(url, output);
                    good = true;
                    break;
                } catch (IOException ex) {
                    lastEx = ex;
                }
            }
            if (!good && lastEx != null) {
                throw lastEx;
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
}
