package ninja.egg82.maven;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import javax.xml.xpath.XPathExpressionException;
import ninja.egg82.utils.MavenUtil;
import org.xml.sax.SAXException;

public class ArtifactParent {
    private final String groupId;
    public String getGroupId() { return groupId; }

    private final String artifactId;
    public String getArtifactId() { return artifactId; }

    private String version;
    public String getVersion() { return version; }

    private String strippedVersion;
    public String getStrippedVersion() { return strippedVersion; }

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

    private ArtifactParent(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;

        snapshot = version.endsWith("-SNAPSHOT") || version.endsWith("-LATEST");
        release = version.equalsIgnoreCase("release");
        latest = version.equalsIgnoreCase("latest");

        if (snapshot) {
            strippedVersion = version.substring(0, version.lastIndexOf('-'));
        } else {
            strippedVersion = version;
        }
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

        public ArtifactParent build() throws URISyntaxException, IOException, XPathExpressionException, SAXException { return build(Scope.COMPILE, Scope.RUNTIME); }

        public ArtifactParent build(Scope... targetDependencyScopes) throws URISyntaxException, IOException, XPathExpressionException, SAXException {
            if (result.release) {
                String version = MavenUtil.getReleaseVersion(result);
                result.version = result.snapshot ? version + "-SNAPSHOT" : version;
                result.strippedVersion = version;
            } else if (result.latest) {
                String version = MavenUtil.getLatestVersion(result);
                result.version = result.snapshot ? version + "-SNAPSHOT" : version;
                result.strippedVersion = version;
            }

            String group = result.groupId.replace('.', '/');
            if (result.snapshot) {
                String snapshotVersion = MavenUtil.getSnapshotVersion(result);
                for (String repository : result.repositories) {
                    result.pomURIs.add(new URI(repository + group + "/" + result.artifactId + "/" + result.version + "/" + result.artifactId + "-" + snapshotVersion + ".pom"));
                }
            } else if (result.release) {
                String releaseVersion = MavenUtil.getReleaseVersion(result);
                for (String repository : result.repositories) {
                    result.pomURIs.add(new URI(repository + group + "/" + result.artifactId + "/" + result.version + "/" + result.artifactId + "-" + releaseVersion + ".pom"));
                }
            } else {
                for (String repository : result.repositories) {
                    result.pomURIs.add(new URI(repository + group + "/" + result.artifactId + "/" + result.version + "/" + result.artifactId + "-" + result.version + ".pom"));
                }
            }

            result.properties = MavenUtil.getProperties(result);
            result.parent = MavenUtil.getParent(result);
            result.declaredRepositories.addAll(MavenUtil.getDeclaredRepositories(result));
            result.softDependencies = MavenUtil.getSoftDependencies(result, targetDependencyScopes);
            result.hardDependencies = MavenUtil.getHardDependencies(result, targetDependencyScopes);

            return result;
        }
    }
}
