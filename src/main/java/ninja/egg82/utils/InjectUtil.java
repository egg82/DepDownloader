package ninja.egg82.utils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * Some of this code taken from LuckPerms
 * https://github.com/lucko/LuckPerms/blob/55220e9d104de7a9405237bdd8624a781ac23109/common/src/main/java/me/lucko/luckperms/common/dependencies/classloader/ReflectionClassLoader.java
 */

public class InjectUtil {
    private static final Method ADD_URL_METHOD;

    static {
        try {
            ADD_URL_METHOD = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            ADD_URL_METHOD.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private InjectUtil() {}

    public static void injectFile(File file, URLClassLoader classLoader) throws IOException, IllegalAccessException, InvocationTargetException {
        if (file == null) {
            throw new IllegalArgumentException("file cannot be null.");
        }
        if (classLoader == null) {
            throw new IllegalArgumentException("classLoader cannot be null.");
        }

        if (file.exists() && file.isDirectory()) {
            throw new IOException("file is not a file.");
        }
        if (!file.exists()) {
            throw new IOException("file does not exist.");
        }

        ADD_URL_METHOD.invoke(classLoader, file.toPath().toUri().toURL());
    }
}
