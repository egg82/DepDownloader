package ninja.egg82.maven;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.*;
import java.util.*;
import ninja.egg82.utils.DownloadUtil;
import ninja.egg82.utils.HTTPUtil;
import ninja.egg82.utils.InjectUtil;
import ninja.egg82.utils.MavenUtil;
import org.xml.sax.SAXException;

public class Artifact {
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

    private final boolean snapshot;
    public boolean isSnapshot() { return snapshot; }

    private final boolean release;
    public boolean isRelease() { return release; }

    private final boolean latest;
    public boolean isLatest() { return latest; }

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

    private List<Artifact> dependencies = null;
    public List<Artifact> getDependencies() { return dependencies; }

    private Artifact(String groupId, String artifactId, String version, Scope scope) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.scope = scope;
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

    public static Builder builder(String groupId, String artifactId, String version) { return new Builder(groupId, artifactId, version, Scope.COMPILE); }

    public static Builder builder(String groupId, String artifactId, String version, Scope scope) { return new Builder(groupId, artifactId, version, scope); }

    public static class Builder {
        private final Artifact result;

        private Builder(String groupId, String artifactId, String version, Scope scope) {
            result = new Artifact(groupId, artifactId, version, scope);
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

        public Artifact build() throws URISyntaxException, IOException, SAXException {
            if (result.release) {
                String version = MavenUtil.getReleaseVersion(result);
                result.version = result.snapshot ? version + "-SNAPSHOT" : version;
                result.strippedVersion = version;
            } else if (result.latest) {
                String version = MavenUtil.getLatestVersion(result);
                result.version = result.snapshot ? version + "-SNAPSHOT" : version;
                result.strippedVersion = version;
            }

            for (String url : result.rawDirectJarURIs) {
                result.directJarURIs.add(new URI(replaceURL(url)));

                String pomURL = url.replace(".jar", ".pom");
                if (!pomURL.equals(url)) {
                    result.pomURIs.add(new URI(replaceURL(pomURL)));
                }
            }
            result.jarURIs.addAll(result.directJarURIs);

            String group = result.groupId.replace('.', '/');
            if (result.snapshot) {
                String snapshotVersion = MavenUtil.getSnapshotVersion(result);
                for (String repository : result.repositories) {
                    result.jarURIs.add(new URI(repository + group + "/" + result.artifactId + "/" + result.version + "/" + result.artifactId + "-" + snapshotVersion + ".jar"));
                    result.pomURIs.add(new URI(repository + group + "/" + result.artifactId + "/" + result.version + "/" + result.artifactId + "-" + snapshotVersion + ".pom"));
                }
            } else if (result.release) {
                String releaseVersion = MavenUtil.getReleaseVersion(result);
                for (String repository : result.repositories) {
                    result.jarURIs.add(new URI(repository + group + "/" + result.artifactId + "/" + result.version + "/" + result.artifactId + "-" + releaseVersion + ".jar"));
                    result.pomURIs.add(new URI(repository + group + "/" + result.artifactId + "/" + result.version + "/" + result.artifactId + "-" + releaseVersion + ".pom"));
                }
            } else {
                for (String repository : result.repositories) {
                    result.jarURIs.add(new URI(repository + group + "/" + result.artifactId + "/" + result.version + "/" + result.artifactId + "-" + result.version + ".jar"));
                    result.pomURIs.add(new URI(repository + group + "/" + result.artifactId + "/" + result.version + "/" + result.artifactId + "-" + result.version + ".pom"));
                }
            }

            result.parent = MavenUtil.getParent(result);
            result.declaredRepositories.addAll(MavenUtil.getDeclaredRepositories(result));
            result.dependencies = MavenUtil.getDependencies(result);

            return result;
        }

        private String replaceURL(String url) {
            return url.replace("{GROUP}", result.groupId.replace('.', '/'))
                    .replace("{ARTIFACT}", result.artifactId)
                    .replace("{VERSION}", result.version);
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

    public void injectJar(File output, URLClassLoader classLoader) throws IOException, IllegalAccessException, InvocationTargetException {
        InjectUtil.injectFile(DownloadUtil.getOrDownloadFile(output, HTTPUtil.toURLs(jarURIs)), classLoader);
    }
}
