package ninja.egg82.core;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class JarDep {
    private final String name;
    public String getName() { return name; }

    private final String version;
    public String getVersion() { return version; }

    private final List<URL> urls = new ArrayList<>();
    public List<URL> getURLs() { return urls; }

    private JarDep(String name, String version) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("name cannot be null or empty.");
        }
        if (version == null || version.isEmpty()) {
            throw new IllegalArgumentException("version cannot be null or empty.");
        }

        this.name = name;
        this.version = version;
    }

    public File getFile(File parent) { return new File(parent, name + "-" + version + ".jar"); }

    public static JarDep.Builder builder(String name, String version) { return new JarDep.Builder(name, version); }

    public static class Builder {
        private final JarDep result;

        private Builder(String name, String version) { result = new JarDep(name, version); }

        public JarDep.Builder addURL(String url) throws MalformedURLException {
            if (url == null || url.isEmpty()) {
                throw new IllegalArgumentException("url cannot be null or empty.");
            }

            result.urls.add(new URL(replaceURL(url)));
            return this;
        }

        public JarDep build() { return result; }

        private String replaceURL(String url) { return url.replace("{NAME}", result.name).replace("{VERSION}", result.version); }
    }
}
