package ninja.egg82.utils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import ninja.egg82.core.JarDep;

/**
 * Some of this code taken from LuckPerms
 * https://github.com/lucko/LuckPerms/blob/55220e9d104de7a9405237bdd8624a781ac23109/common/src/main/java/me/lucko/luckperms/common/dependencies/classloader/ReflectionClassLoader.java
 */

public class JarUtil {
    private static final Method ADD_URL_METHOD;

    static {
        try {
            ADD_URL_METHOD = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            ADD_URL_METHOD.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private JarUtil() {}

    public static boolean hasJar(JarDep dep) { return hasJar(dep, null); }

    public static boolean hasJar(JarDep dep, File parent) {
        if (dep == null) {
            throw new IllegalArgumentException("dep cannot be null.");
        }

        File output = dep.getFile(parent);

        if (output.exists()) {
            return !output.isDirectory();
        }
        return false;
    }

    public static void loadJar(JarDep dep, URLClassLoader classLoader) throws IOException, IllegalAccessException, InvocationTargetException { loadJar(dep, classLoader, null); }

    public static void loadJar(JarDep dep, URLClassLoader classLoader, File parent) throws IOException, IllegalAccessException, InvocationTargetException {
        if (dep == null) {
            throw new IllegalArgumentException("dep cannot be null.");
        }
        if (classLoader == null) {
            throw new IllegalArgumentException("classLoader cannot be null.");
        }

        File input = dep.getFile(parent);

        if (input.exists() && input.isDirectory()) {
            Files.delete(input.toPath());
        }

        if (!input.exists()) {
            IOException lastEx = null;
            boolean good = false;
            for (URL url : dep.getURLs()) {
                try {
                    downloadJar(url, input);
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

        ADD_URL_METHOD.invoke(classLoader, input.toPath().toUri().toURL());
    }

    public static boolean hasRawJarFile(File jar) {
        if (jar == null) {
            return false;
        }

        if (jar.exists()) {
            return !jar.isDirectory();
        }
        return false;
    }

    public static void loadRawJarFile(File jar, URLClassLoader classLoader) throws IOException, IllegalAccessException, InvocationTargetException {
        if (jar == null) {
            throw new IllegalArgumentException("jar cannot be null.");
        }
        if (classLoader == null) {
            throw new IllegalArgumentException("classLoader cannot be null.");
        }

        if (jar.exists() && jar.isDirectory()) {
            Files.delete(jar.toPath());
        }
        if (!jar.exists()) {
            throw new IOException("jar does not exist.");
        }

        ADD_URL_METHOD.invoke(classLoader, jar.toPath().toUri().toURL());
    }

    private static void downloadJar(URL url, File output) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(true);

        boolean redirect;

        do {
            int status = conn.getResponseCode();
            redirect = status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_SEE_OTHER;

            if (redirect) {
                String newUrl = conn.getHeaderField("Location");
                String cookies = conn.getHeaderField("Set-Cookie");

                conn = (HttpURLConnection) new URL(newUrl).openConnection();
                conn.setRequestProperty("Cookie", cookies);
                conn.addRequestProperty("Accept-Language", "en-US,en;q=0.8");
            }
        } while (redirect);

        try (BufferedInputStream in = new BufferedInputStream(conn.getInputStream()); FileOutputStream fileOutputStream = new FileOutputStream(output)) {
            byte[] dataBuffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                fileOutputStream.write(dataBuffer, 0, bytesRead);
            }
        }
    }
}
