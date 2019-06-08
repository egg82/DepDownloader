package ninja.egg82.maven;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.xml.xpath.XPathExpressionException;
import ninja.egg82.utils.DownloadUtil;
import ninja.egg82.utils.HTTPUtil;
import ninja.egg82.utils.MavenUtil;
import org.xml.sax.SAXException;

public class ArtifactParent {
    private static ConcurrentMap<String, ArtifactParent> cache = new ConcurrentHashMap<>();

    private final String groupId;
    public String getGroupId() { return groupId; }

    private final String artifactId;
    public String getArtifactId() { return artifactId; }

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

    private Set<URI> pomURIs = new LinkedHashSet<>();
    public Set<URI> getPomURIs() { return Collections.unmodifiableSet(pomURIs); }

    private ArtifactParent parent = null;
    public ArtifactParent getParent() { return parent; }

    private volatile List<Artifact> softDependencies = null;
    public List<Artifact> getSoftDependencies() throws URISyntaxException, IOException, XPathExpressionException, SAXException {
        List<Artifact> tmp = softDependencies;
        if (tmp == null) {
            synchronized (this) {
                tmp = softDependencies;
                if (tmp == null) {
                    softDependencies = tmp = MavenUtil.getSoftDependencies(this);
                }
            }
        }
        return tmp;
    }

    private volatile List<Artifact> hardDependencies = null;
    public List<Artifact> getHardDependencies() throws URISyntaxException, IOException, XPathExpressionException, SAXException {
        List<Artifact> tmp = hardDependencies;
        if (tmp == null) {
            synchronized (this) {
                tmp = hardDependencies;
                if (tmp == null) {
                    hardDependencies = tmp = MavenUtil.getHardDependencies(this);
                }
            }
        }
        return tmp;
    }

    private final File cacheDir;
    public File getCacheDir() { return cacheDir; }

    private final int computedHash;

    private ArtifactParent(String groupId, String artifactId, String version, File cacheDir) {
        this.groupId = groupId;
        this.artifactId = artifactId;
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

    public static Builder builder(String groupId, String artifactId, String version, File cacheDir) { return new Builder(groupId, artifactId, version, cacheDir); }

    public static class Builder {
        private final ArtifactParent result;

        private Builder(String groupId, String artifactId, String version, File cacheDir) {
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

            result = new ArtifactParent(groupId, artifactId, version, cacheDir);
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

        public ArtifactParent build() throws URISyntaxException, IOException, XPathExpressionException, SAXException {
            ArtifactParent cachedResult = cache.putIfAbsent(result.toString(), result);
            if (result.equals(cachedResult)) {
                return result.cacheDir == cachedResult.cacheDir ? cachedResult : copyParent(result);
            }

            if (result.snapshot) {
                String version = MavenUtil.getSnapshotVersion(result);
                result.realVersion = version;
            } else if (result.release) {
                String version = MavenUtil.getReleaseVersion(result);
                result.version = result.snapshot ? version + "-SNAPSHOT" : version;
                result.strippedVersion = version;
                result.realVersion = version;
            } else if (result.latest) {
                String version = MavenUtil.getLatestVersion(result);
                result.version = result.snapshot ? version + "-SNAPSHOT" : version;
                result.strippedVersion = version;
                result.realVersion = version;
            }

            String group = result.groupId.replace('.', '/');
            for (String repository : result.repositories) {
                result.pomURIs.add(new URI(repository + group + "/" + result.artifactId + "/" + encode(result.version) + "/" + result.artifactId + "-" + encode(result.realVersion) + ".pom"));
            }

            if (!HTTPUtil.remoteExists(HTTPUtil.toURLs(result.pomURIs))) {
                // Some deps just don't exist any more. Wheee!
                result.properties = new HashMap<>();
                result.softDependencies = new ArrayList<>();
                result.hardDependencies = new ArrayList<>();
                return result;
            }

            result.properties = MavenUtil.getProperties(result);
            result.properties.put("project.groupId", result.groupId);
            result.properties.put("project.artifactId", result.artifactId);
            result.properties.put("project.version", result.version);
            result.properties.put("pom.groupId", result.groupId);
            result.properties.put("pom.artifactId", result.artifactId);
            result.properties.put("pom.version", result.version);
            result.parent = MavenUtil.getParent(result);
            result.declaredRepositories.addAll(MavenUtil.getDeclaredRepositories(result));

            return result;
        }

        private String encode(String raw) throws UnsupportedEncodingException { return URLEncoder.encode(raw, "UTF-8"); }

        private ArtifactParent copyParent(ArtifactParent parent) {
            ArtifactParent retVal = new ArtifactParent(parent.groupId, parent.artifactId, parent.version, parent.cacheDir);
            retVal.strippedVersion = parent.strippedVersion;
            retVal.realVersion = parent.realVersion;
            retVal.properties = parent.properties;
            retVal.repositories = parent.repositories;
            retVal.declaredRepositories = parent.declaredRepositories;
            retVal.pomURIs = parent.pomURIs;
            retVal.parent = parent.parent;
            retVal.softDependencies = parent.softDependencies;
            retVal.hardDependencies = parent.hardDependencies;
            return retVal;
        }
    }

    public void downloadPom(File output) throws IOException {
        if (output == null) {
            throw new IllegalArgumentException("output cannot be null.");
        }
        HTTPUtil.downloadFile(HTTPUtil.toURLs(pomURIs), output);
    }

    public boolean fileExists(File output) { return DownloadUtil.hasFile(output); }

    public String toString() { return groupId + ":" + artifactId + ":" + version; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArtifactParent parent = (ArtifactParent) o;
        return groupId.equals(parent.groupId) &&
                artifactId.equals(parent.artifactId) &&
                version.equals(parent.version);
    }

    @Override
    public int hashCode() { return computedHash; }
}
