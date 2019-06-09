package ninja.egg82.services;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

public class ProxiedURLClassLoader extends URLClassLoader {
    private static final Method FIND_METHOD;

    static {
        try {
            FIND_METHOD = URLClassLoader.class.getDeclaredMethod("findClass", String.class);
            FIND_METHOD.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }

        ClassLoader.registerAsParallelCapable();
    }

    private ClassLoader parent;
    private ClassLoader system;
    private boolean parentIsSystem;

    public ProxiedURLClassLoader(ClassLoader parent) {
        super(new URL[0]);
        this.parent = parent;
        system = getSystemClassLoader();
        parentIsSystem = parent == system;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        // Find in local
        try {
            return super.findClass(name);
        } catch (ClassNotFoundException | SecurityException ignored) { }

        // Find in parent
        try {
            return (Class<?>) FIND_METHOD.invoke(parent, name);
        } catch (InvocationTargetException | IllegalAccessException ignored) { }

        // Find in system (JVM, classpath, etc)
        if (system != null && !parentIsSystem) {
            try {
                return (Class<?>) FIND_METHOD.invoke(system, name);
            } catch (InvocationTargetException ex) {
                if (ex.getCause() instanceof ClassNotFoundException) {
                    throw (ClassNotFoundException) ex.getCause();
                }
            } catch (IllegalAccessException ignored) { }
        }

        throw new ClassNotFoundException(name);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // Check if class has been loaded
        Class<?> clazz = findLoadedClass(name);

        // Find in local
        if (clazz == null) {
            try {
                clazz = super.findClass(name);
            } catch (ClassNotFoundException | SecurityException ignored) { }
        }

        // Load in parent
        if (clazz == null) {
            try {
                clazz = parent.loadClass(name);
            } catch (ClassNotFoundException | SecurityException ignored) { }
        }

        // Load in system (JVM, classpath, etc)
        if (clazz == null && system != null && !parentIsSystem) {
            clazz = system.loadClass(name);
        }

        if (resolve) {
            resolveClass(clazz);
        }
        return clazz;
    }

    @Override
    public URL getResource(String name) {
        // Find in local
        URL url = findResource(name);

        // Find in parent
        if (url == null) {
            url = parent.getResource(name);
        }

        // Find in system (JVM, classpath, etc)
        if (url == null && system != null && !parentIsSystem) {
            url = system.getResource(name);
        }

        return url;
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        List<URL> urls = new ArrayList<>();

        // Get local
        Enumeration<URL> enums = findResources(name);
        if (enums != null) {
            while (enums.hasMoreElements()) {
                urls.add(enums.nextElement());
            }
        }

        // Get parent
        if (parent != null) {
            enums = parent.getResources(name);
            if (enums != null) {
                while (enums.hasMoreElements()) {
                    urls.add(enums.nextElement());
                }
            }
        }

        // Get system (JVM, classpath, etc)
        if (system != null && !parentIsSystem) {
            enums = system.getResources(name);
            if (enums != null) {
                while (enums.hasMoreElements()) {
                    urls.add(enums.nextElement());
                }
            }
        }

        return new Enumeration<URL>() {
            Iterator<URL> i = urls.iterator();
            public boolean hasMoreElements() {
                return i.hasNext();
            }
            public URL nextElement() {
                return i.next();
            }
        };
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        URL url = getResource(name);
        try {
            return url != null ? url.openStream() : null;
        } catch (IOException ignored) {}
        return null;
    }
}
