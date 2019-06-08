package ninja.egg82.maven;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.xml.xpath.XPathExpressionException;
import ninja.egg82.utils.DownloadUtil;
import ninja.egg82.utils.HTTPUtil;
import ninja.egg82.utils.InjectUtil;
import ninja.egg82.utils.MavenUtil;
import org.xml.sax.SAXException;

public class Artifact {
    private static ConcurrentMap<String, Artifact> cache = new ConcurrentHashMap<>();

    private final String groupId;
    public String getGroupId() { return groupId; }

    private final String artifactId;
    public String getArtifactId() { return artifactId; }

    private final Scope scope;
    public Scope getScope() { return scope; }

    private String version;
    public String getVersion() { return version; }

    private String strippedVersion;
    public String getStrippedVersion() { return strippedVersion; }

    private String realVersion;
    public String getRealVersion() { return realVersion; }

    private final boolean snapshot;
    public boolean isSnapshot() { return snapshot; }

    private final boolean release;
    public boolean isRelease() { return release; }

    private final boolean latest;
    public boolean isLatest() { return latest; }

    private Map<String, String> properties = null;
    public Map<String, String> getProperties() { return Collections.unmodifiableMap(properties); }

    private Set<String> repositories = new LinkedHashSet<>();
    public Set<String> getRepositories() { return Collections.unmodifiableSet(repositories); }

    private Set<String> declaredRepositories = new LinkedHashSet<>();
    public Set<String> getDeclaredRepositories() { return Collections.unmodifiableSet(declaredRepositories); }

    private Set<String> rawDirectJarURIs = new LinkedHashSet<>();
    public Set<String> getRawDirectJarURIs() { return Collections.unmodifiableSet(rawDirectJarURIs); }

    private Set<URI> directJarURIs = new LinkedHashSet<>();
    public Set<URI> getDirectJarURIs() { return Collections.unmodifiableSet(directJarURIs); }

    private Set<URI> jarURIs = new LinkedHashSet<>();
    public Set<URI> getJarURIs() { return Collections.unmodifiableSet(jarURIs); }

    private Set<URI> pomURIs = new LinkedHashSet<>();
    public Set<URI> getPomURIs() { return Collections.unmodifiableSet(pomURIs); }

    private ArtifactParent parent = null;
    public ArtifactParent getParent() { return parent; }

    private volatile List<Artifact> dependencies = null;
    public List<Artifact> getDependencies() throws URISyntaxException, IOException, XPathExpressionException, SAXException {
        List<Artifact> tmp = dependencies;
        if (tmp == null) {
            synchronized (this) {
                tmp = dependencies;
                if (tmp == null) {
                    dependencies = tmp = MavenUtil.getDependencies(this);
                }
            }
        }
        return tmp;
    }

    private final File cacheDir;
    public File getCacheDir() { return cacheDir; }

    private final int computedHash;

    private Artifact(String groupId, String artifactId, String version, File cacheDir, Scope scope) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.scope = scope;
        this.version = version;
        this.cacheDir = cacheDir;

        computedHash = Objects.hash(groupId, artifactId, version);

        snapshot = version.endsWith("-SNAPSHOT") || version.endsWith("-LATEST");
        release = version.equalsIgnoreCase("release");
        latest = version.equalsIgnoreCase("latest");

        if (snapshot) {
            strippedVersion = version.substring(0, version.lastIndexOf('-'));
        } else {
            strippedVersion = version;
        }

        realVersion = strippedVersion;
    }

    public static Builder builder(String groupId, String artifactId, String version, File cacheDir) throws IOException { return new Builder(groupId, artifactId, version, cacheDir, Scope.COMPILE); }

    public static Builder builder(String groupId, String artifactId, String version, File cacheDir, Scope scope) throws IOException { return new Builder(groupId, artifactId, version, cacheDir, scope); }

    public static class Builder {
        private final Artifact result;

        private Builder(String groupId, String artifactId, String version, File cacheDir, Scope scope) throws IOException {
            if (groupId == null || groupId.isEmpty()) {
                throw new IllegalArgumentException("groupId cannot be null or empty.");
            }
            if (artifactId == null || artifactId.isEmpty()) {
                throw new IllegalArgumentException("artifactId cannot be null or empty.");
            }
            if (version == null || version.isEmpty()) {
                throw new IllegalArgumentException("version cannot be null or empty.");
            }
            if (cacheDir == null) {
                throw new IllegalArgumentException("cacheDir cannot be null.");
            }
            if (scope == null) {
                throw new IllegalArgumentException("scope cannot be null.");
            }

            DownloadUtil.createDirectory(cacheDir);

            result = new Artifact(groupId, artifactId, version, cacheDir, scope);
        }

        public Builder addRepository(String url) {
            if (url == null || url.isEmpty()) {
                throw new IllegalArgumentException("url cannot be null or empty.");
            }

            if (url.charAt(url.length() - 1) != '/') {
                url = url + "/";
            }

            result.repositories.add(url);
            return this;
        }

        public Builder addDirectJarURL(String url) {
            if (url == null || url.isEmpty()) {
                throw new IllegalArgumentException("url cannot be null or empty.");
            }

            result.rawDirectJarURIs.add(url);
            return this;
        }

        public Artifact build() throws URISyntaxException, IOException, XPathExpressionException, SAXException {
            Artifact cachedResult = cache.putIfAbsent(result.toString(), result);
            if (result.equals(cachedResult)) {
                return result.scope == cachedResult.scope && result.cacheDir.equals(cachedResult.cacheDir) ? cachedResult : result.copyArtifact(result);
            }

            return result.build();
        }
    }

    public void downloadJar(File output) throws IOException {
        if (output == null) {
            throw new IllegalArgumentException("output cannot be null.");
        }
        HTTPUtil.downloadFile(HTTPUtil.toURLs(jarURIs), output);
    }

    public void downloadPom(File output) throws IOException {
        if (output == null) {
            throw new IllegalArgumentException("output cannot be null.");
        }
        HTTPUtil.downloadFile(HTTPUtil.toURLs(pomURIs), output);
    }

    public boolean fileExists(File output) { return DownloadUtil.hasFile(output); }

    public void injectJar(File output, URLClassLoader classLoader) throws IOException, IllegalAccessException, InvocationTargetException {
        InjectUtil.injectFile(DownloadUtil.getOrDownloadFile(output, HTTPUtil.toURLs(jarURIs)), classLoader);
    }

    public String toString() { return groupId + ":" + artifactId + ":" + version; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Artifact artifact = (Artifact) o;
        return groupId.equals(artifact.groupId) &&
                artifactId.equals(artifact.artifactId) &&
                version.equals(artifact.version);
    }

    @Override
    public int hashCode() { return computedHash; }

    private Artifact build() throws URISyntaxException, IOException, XPathExpressionException, SAXException {
        if (snapshot) {
            realVersion = MavenUtil.getSnapshotVersion(this);
        }
        if (release) {
            String v = MavenUtil.getReleaseVersion(this);
            version = snapshot ? v + "-SNAPSHOT" : v;
            strippedVersion = v;
            realVersion = v;
        } else if (latest) {
            String v = MavenUtil.getLatestVersion(this);
            version = snapshot ? v + "-SNAPSHOT" : v;
            strippedVersion = v;
            realVersion = v;
        }

        for (String url : rawDirectJarURIs) {
            directJarURIs.add(new URI(replaceURL(url)));

            String pomURL = url.replace(".jar", ".pom");
            if (!pomURL.equals(url)) {
                pomURIs.add(new URI(replaceURL(pomURL)));
            }
        }
        jarURIs.addAll(directJarURIs);

        String group = groupId.replace('.', '/');
        for (String repository : repositories) {
            jarURIs.add(new URI(repository + group + "/" + artifactId + "/" + encode(version) + "/" + artifactId + "-" + encode(realVersion) + ".jar"));
            pomURIs.add(new URI(repository + group + "/" + artifactId + "/" + encode(version) + "/" + artifactId + "-" + encode(realVersion) + ".pom"));
        }

        if (!HTTPUtil.remoteExists(HTTPUtil.toURLs(pomURIs))) {
            // Some deps just don't exist any more. Wheee!
            properties = new HashMap<>();
            dependencies = new ArrayList<>();
            return this;
        }

        properties = MavenUtil.getProperties(this);
        properties.put("project.groupId", groupId);
        properties.put("project.artifactId", artifactId);
        properties.put("project.version", version);
        properties.put("project.scope", scope.getName());
        properties.put("pom.groupId", groupId);
        properties.put("pom.artifactId", artifactId);
        properties.put("pom.version", version);
        properties.put("pom.scope", scope.getName());
        parent = MavenUtil.getParent(this);
        declaredRepositories.addAll(MavenUtil.getDeclaredRepositories(this));

        return this;
    }

    private String encode(String raw) throws UnsupportedEncodingException { return URLEncoder.encode(raw, "UTF-8"); }

    private String replaceURL(String url) {
        return url.replace("{GROUP}", groupId.replace('.', '/'))
                .replace("{ARTIFACT}", artifactId)
                .replace("{VERSION}", version)
                .replace("{SNAPSHOT}", realVersion)
                .replace("{LATEST}", realVersion)
                .replace("{RELEASE}", realVersion)
                .replace("{STRIPPED}", strippedVersion)
                .replace("{REAL}", realVersion);
    }

    private Artifact copyArtifact(Artifact artifact) throws URISyntaxException, IOException, XPathExpressionException, SAXException {
        Artifact retVal = new Artifact(artifact.groupId, artifact.artifactId, artifact.version, artifact.cacheDir, artifact.scope);
        retVal.strippedVersion = artifact.strippedVersion;
        retVal.realVersion = artifact.realVersion;
        retVal.properties = artifact.properties;
        retVal.repositories = artifact.repositories;
        retVal.declaredRepositories = artifact.declaredRepositories;
        retVal.rawDirectJarURIs = artifact.rawDirectJarURIs;
        retVal.directJarURIs = artifact.directJarURIs;
        retVal.jarURIs = artifact.jarURIs;
        retVal.pomURIs = artifact.pomURIs;
        retVal.parent = artifact.parent;
        retVal.dependencies = artifact.dependencies;
        return retVal.build();
    }
}
