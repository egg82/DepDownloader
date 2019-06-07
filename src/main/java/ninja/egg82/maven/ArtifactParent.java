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

    private List<Artifact> softDependencies = null;
    public List<Artifact> getSoftDependencies() { return softDependencies; }

    private List<Artifact> hardDependencies = null;
    public List<Artifact> getHardDependencies() { return hardDependencies; }

    private Scope[] dependencyScopes = null;
    private boolean finalDepth = false;

    private final int computedHash;

    private ArtifactParent(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;

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

    public static Builder builder(String groupId, String artifactId, String version) { return new Builder(groupId, artifactId, version); }

    public static class Builder {
        private final ArtifactParent result;

        private Builder(String groupId, String artifactId, String version) {
            result = new ArtifactParent(groupId, artifactId, version);
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

        public ArtifactParent build(File cacheDir, int depth) throws URISyntaxException, IOException, XPathExpressionException, SAXException { return build(cacheDir, depth, Scope.COMPILE, Scope.RUNTIME); }

        public ArtifactParent build(File cacheDir, int depth, Scope... targetDependencyScopes) throws URISyntaxException, IOException, XPathExpressionException, SAXException {
            if (cacheDir == null) {
                throw new IllegalArgumentException("cacheDir cannot be null.");
            }
            DownloadUtil.createDirectory(cacheDir);

            if (depth == 0) {
                result.finalDepth = true;
            }
            result.dependencyScopes = targetDependencyScopes;

            ArtifactParent cachedResult = cache.putIfAbsent(result.toString(), result);
            if (result.equals(cachedResult)) {
                if (!scopesEqual(targetDependencyScopes, cachedResult.dependencyScopes) || (!result.finalDepth && cachedResult.finalDepth)) {
                    cache.put(result.toString(), result);
                } else {
                    System.out.println("Returning cached result for " + result.groupId + ":" + result.artifactId + "::" + result.version);
                    return cachedResult;
                }
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

            result.properties = MavenUtil.getProperties(result, cacheDir);
            result.properties.put("project.groupId", result.groupId);
            result.properties.put("project.artifactId", result.artifactId);
            result.properties.put("project.version", result.version);
            result.properties.put("pom.groupId", result.groupId);
            result.properties.put("pom.artifactId", result.artifactId);
            result.properties.put("pom.version", result.version);
            result.parent = MavenUtil.getParent(result, cacheDir, (depth == -1) ? depth : 1);
            result.declaredRepositories.addAll(MavenUtil.getDeclaredRepositories(result, cacheDir));
            result.softDependencies = (depth == 0) ? new ArrayList<>() : MavenUtil.getSoftDependencies(result, cacheDir, Math.max(depth - 1, -1), targetDependencyScopes);
            result.hardDependencies = (depth == 0) ? new ArrayList<>() : MavenUtil.getHardDependencies(result, cacheDir, Math.max(depth - 1, -1), targetDependencyScopes);

            return result;
        }

        private String encode(String raw) throws UnsupportedEncodingException { return URLEncoder.encode(raw, "UTF-8"); }

        private boolean scopesEqual(Scope[] scopes1, Scope[] scopes2) {
            if (scopes1.length != scopes2.length) {
                return false;
            }
            for (int i = 0; i < scopes1.length; i++) {
                if (scopes1[i] != scopes2[i]) {
                    return false;
                }
            }
            return true;
        }
    }

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
