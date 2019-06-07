package ninja.egg82.utils;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;
import javax.xml.xpath.XPathExpressionException;
import ninja.egg82.maven.Artifact;
import ninja.egg82.maven.ArtifactParent;
import ninja.egg82.maven.Scope;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class MavenUtil {
    private MavenUtil() {}

    public static Map<String, String> getProperties(Artifact artifact) throws IOException, XPathExpressionException {
        File pomFile = DownloadUtil.getOrDownloadFile(getCachePom(artifact), HTTPUtil.toURLs(artifact.getPomURIs()));
        return fetchProperties(DocumentUtil.getDocument(pomFile));
    }

    public static Map<String, String> getProperties(ArtifactParent parent) throws IOException, XPathExpressionException {
        File pomFile = DownloadUtil.getOrDownloadFile(getCachePom(parent), HTTPUtil.toURLs(parent.getPomURIs()));
        return fetchProperties(DocumentUtil.getDocument(pomFile));
    }

    private static Map<String, String> fetchProperties(Document document) throws XPathExpressionException {
        Map<String, String> retVal = new HashMap<>();

        NodeList propertiesNodes = DocumentUtil.getNodesByXPath(document, "/project/properties/*");
        if (propertiesNodes.getLength() == 0) {
            return retVal;
        }

        for (int i = 0; i < propertiesNodes.getLength(); i++) {
            Node childNode = propertiesNodes.item(i);
            if (childNode.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Node innerNode = childNode.getFirstChild();
            if (innerNode == null) {
                retVal.put(childNode.getNodeName(), "");
                continue;
            }

            if (innerNode.getNodeType() != Node.TEXT_NODE) {
                continue;
            }

            retVal.put(childNode.getNodeName(), innerNode.getNodeValue());
        }

        return retVal;
    }

    public static List<Artifact> getDependencies(Artifact artifact) throws URISyntaxException, IOException, XPathExpressionException, SAXException {
        File pomFile = DownloadUtil.getOrDownloadFile(getCachePom(artifact), HTTPUtil.toURLs(artifact.getPomURIs()));

        Set<String> repositories = getRepositories(artifact);

        List<Artifact> retVal = new ArrayList<>();
        List<Artifact.Builder> builders = fetchHardDependencies(artifact.getParent(), DocumentUtil.getDocument(pomFile), artifact.getProperties(), artifact.getCacheDir());

        for (Artifact.Builder builder : builders) {
            for (String repository : repositories) {
                builder.addRepository(repository);
            }
            retVal.add(builder.build());
        }

        ArtifactParent p = artifact.getParent();
        while (p != null) {
            retVal.addAll(p.getHardDependencies());
            p = p.getParent();
        }

        return retVal;
    }

    public static List<Artifact> getSoftDependencies(ArtifactParent parent) throws URISyntaxException, IOException, XPathExpressionException, SAXException {
        File pomFile = DownloadUtil.getOrDownloadFile(getCachePom(parent), HTTPUtil.toURLs(parent.getPomURIs()));

        Set<String> repositories = getRepositories(parent);

        List<Artifact> retVal = new ArrayList<>();
        List<Artifact.Builder> builders = fetchSoftDependencies(parent.getParent(), DocumentUtil.getDocument(pomFile), parent.getProperties(), parent.getCacheDir());

        for (Artifact.Builder builder : builders) {
            for (String repository : repositories) {
                builder.addRepository(repository);
            }
            retVal.add(builder.build());
        }
        return retVal;
    }

    public static List<Artifact> getHardDependencies(ArtifactParent parent) throws URISyntaxException, IOException, XPathExpressionException, SAXException {
        File pomFile = DownloadUtil.getOrDownloadFile(getCachePom(parent), HTTPUtil.toURLs(parent.getPomURIs()));

        Set<String> repositories = getRepositories(parent);

        List<Artifact> retVal = new ArrayList<>();
        List<Artifact.Builder> builders = fetchHardDependencies(parent.getParent(), DocumentUtil.getDocument(pomFile), parent.getProperties(), parent.getCacheDir());

        for (Artifact.Builder builder : builders) {
            for (String repository : repositories) {
                builder.addRepository(repository);
            }
            retVal.add(builder.build());
        }
        return retVal;
    }

    private static Set<String> getRepositories(Artifact artifact) {
        Set<String> retVal = new LinkedHashSet<>(artifact.getRepositories());
        retVal.addAll(artifact.getDeclaredRepositories());
        ArtifactParent p = artifact.getParent();
        while (p != null) {
            retVal.addAll(p.getDeclaredRepositories());
            p = p.getParent();
        }
        retVal.add("http://central.maven.org/maven2/");
        return retVal;
    }

    private static Set<String> getRepositories(ArtifactParent parent) {
        Set<String> retVal = new LinkedHashSet<>(parent.getRepositories());
        retVal.addAll(parent.getDeclaredRepositories());
        ArtifactParent p = parent.getParent();
        while (p != null) {
            retVal.addAll(p.getDeclaredRepositories());
            p = p.getParent();
        }
        retVal.add("http://central.maven.org/maven2/");
        return retVal;
    }

    private static List<Artifact.Builder> fetchSoftDependencies(ArtifactParent parent, Document document, Map<String, String> properties, File cacheDir) throws URISyntaxException, IOException, XPathExpressionException, SAXException {
        return fetchDependencies(parent, DocumentUtil.getNodesByXPath(document, "/project/dependencyManagement/dependencies/dependency"), properties, cacheDir);
    }

    private static List<Artifact.Builder> fetchHardDependencies(ArtifactParent parent, Document document, Map<String, String> properties, File cacheDir) throws URISyntaxException, IOException, XPathExpressionException, SAXException {
        return fetchDependencies(parent, DocumentUtil.getNodesByXPath(document, "/project/dependencies/dependency"), properties, cacheDir);
    }

    private static List<Artifact.Builder> fetchDependencies(ArtifactParent parent, NodeList dependencyNodes, Map<String, String> properties, File cacheDir) throws URISyntaxException, IOException, XPathExpressionException, SAXException {
        List<Artifact.Builder> retVal = new ArrayList<>();

        for (int i = 0; i < dependencyNodes.getLength(); i++) {
            Node dependencyNode = dependencyNodes.item(i);
            if (dependencyNode == null || dependencyNode.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            String groupId = null;
            String artifactId = null;
            String version = null;
            String scope = null;

            NodeList childNodes = dependencyNode.getChildNodes();
            for (int j = 0; j < childNodes.getLength(); j++) {
                Node childNode = childNodes.item(j);
                if (childNode == null || childNode.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }

                Node innerNode = childNode.getFirstChild();
                if (innerNode == null || innerNode.getNodeType() != Node.TEXT_NODE) {
                    continue;
                }

                if (childNode.getNodeName().equals("groupId")) {
                    groupId = innerNode.getNodeValue();
                } else if (childNode.getNodeName().equals("artifactId")) {
                    artifactId = innerNode.getNodeValue();
                } else if (childNode.getNodeName().equals("version")) {
                    version = innerNode.getNodeValue();
                } else if (childNode.getNodeName().equals("scope")) {
                    scope = innerNode.getNodeValue();
                }
            }

            if (version == null && parent != null) {
                ArtifactParent p = parent;
                while (p != null) {
                    if (p.getSoftDependencies() != null) { // Recursion fix
                        for (Artifact dependency : p.getSoftDependencies()) {
                            if (
                                    dependency.getGroupId().equals(groupId)
                                            && dependency.getArtifactId().equals(artifactId)
                            ) {
                                version = dependency.getVersion();
                                break;
                            }
                        }
                    }
                    p = p.getParent();
                }
            }

            groupId = fillPlaceholders(groupId, parent, properties);
            artifactId = fillPlaceholders(artifactId, parent, properties);
            version = fillPlaceholders(version, parent, properties);
            scope = fillPlaceholders(scope, parent, properties);

            if (version == null && parent != null) {
                version = parent.getVersion();
            }

            if (containsPlaceholder(version)) {
                // Some artifacts, like Maven, have these but don't fill them. Who knows why.
                continue;
            }

            if (groupId == null || artifactId == null || version == null) {
                throw new SAXException("Could not get dependencies from pom.");
            }

            Scope scopeEnum = Scope.fromName(scope);

            retVal.add(Artifact.builder(groupId.replaceAll("\\s", ""), artifactId.replaceAll("\\s", ""), version.replaceAll("\\s", ""), cacheDir));
        }

        return retVal;
    }

    public static List<String> getDeclaredRepositories(Artifact artifact) throws IOException, XPathExpressionException, SAXException {
        File pomFile = DownloadUtil.getOrDownloadFile(getCachePom(artifact), HTTPUtil.toURLs(artifact.getPomURIs()));
        return fetchDeclaredRepositories(DocumentUtil.getDocument(pomFile), artifact.getParent(), artifact.getProperties());
    }

    public static List<String> getDeclaredRepositories(ArtifactParent parent) throws IOException, XPathExpressionException, SAXException {
        File pomFile = DownloadUtil.getOrDownloadFile(getCachePom(parent), HTTPUtil.toURLs(parent.getPomURIs()));
        return fetchDeclaredRepositories(DocumentUtil.getDocument(pomFile), parent.getParent(), parent.getProperties());
    }

    private static List<String> fetchDeclaredRepositories(Document document, ArtifactParent parent, Map<String, String> properties) throws XPathExpressionException, SAXException {
        List<String> retVal = new ArrayList<>();

        NodeList repositoryNodes = DocumentUtil.getNodesByXPath(document, "/project/repositories/repository");
        for (int i = 0; i < repositoryNodes.getLength(); i++) {
            Node repositoryNode = repositoryNodes.item(i);
            if (repositoryNode == null || repositoryNode.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            String url = null;

            NodeList childNodes = repositoryNode.getChildNodes();
            for (int j = 0; j < childNodes.getLength(); j++) {
                Node childNode = childNodes.item(j);
                if (childNode == null || childNode.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }

                Node innerNode = childNode.getFirstChild();
                if (innerNode == null || innerNode.getNodeType() != Node.TEXT_NODE) {
                    continue;
                }

                if (childNode.getNodeName().equals("url")) {
                    url = innerNode.getNodeValue();
                }
            }

            url = fillPlaceholders(url, parent, properties);

            if (url == null) {
                throw new SAXException("Could not get repositories from pom.");
            }
            if (url.isEmpty()) {
                continue;
            }

            url = url.replace("\r", "").replace("\n", "");

            if (url.charAt(url.length() - 1) != '/') {
                url = url + "/";
            }
            retVal.add(url);
        }

        return retVal;
    }

    public static ArtifactParent getParent(Artifact artifact) throws URISyntaxException, IOException, XPathExpressionException, SAXException {
        File pomFile = DownloadUtil.getOrDownloadFile(getCachePom(artifact), HTTPUtil.toURLs(artifact.getPomURIs()));
        ArtifactParent.Builder retVal = fetchArtifactParent(DocumentUtil.getDocument(pomFile), artifact.getProperties(), artifact.getCacheDir());
        if (retVal == null) {
            return null;
        }

        for (String repository : artifact.getRepositories()) {
            retVal.addRepository(repository);
        }
        return retVal.build();
    }

    public static ArtifactParent getParent(ArtifactParent parent) throws URISyntaxException, IOException, XPathExpressionException, SAXException {
        File pomFile = DownloadUtil.getOrDownloadFile(getCachePom(parent), HTTPUtil.toURLs(parent.getPomURIs()));
        ArtifactParent.Builder retVal = fetchArtifactParent(DocumentUtil.getDocument(pomFile), parent.getProperties(), parent.getCacheDir());
        if (retVal == null) {
            return null;
        }

        for (String repository : parent.getRepositories()) {
            retVal.addRepository(repository);
        }
        return retVal.build();
    }

    private static ArtifactParent.Builder fetchArtifactParent(Document document, Map<String, String> properties, File cacheDir) throws XPathExpressionException, SAXException {
        NodeList parentNodes = DocumentUtil.getNodesByXPath(document, "/project/parent");
        if (parentNodes.getLength() == 0) {
            return null;
        }

        Node parentNode = parentNodes.item(0);
        if (parentNode == null || parentNode.getNodeType() != Node.ELEMENT_NODE) {
            throw new SAXException("Could not get parent from pom.");
        }

        String groupId = null;
        String artifactId = null;
        String version = null;

        NodeList childNodes = parentNode.getChildNodes();
        for (int j = 0; j < childNodes.getLength(); j++) {
            Node childNode = childNodes.item(j);
            if (childNode == null || childNode.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Node innerNode = childNode.getFirstChild();
            if (innerNode == null || innerNode.getNodeType() != Node.TEXT_NODE) {
                continue;
            }

            if (childNode.getNodeName().equals("groupId")) {
                groupId = innerNode.getNodeValue();
            } else if (childNode.getNodeName().equals("artifactId")) {
                artifactId = innerNode.getNodeValue();
            } else if (childNode.getNodeName().equals("version")) {
                version = innerNode.getNodeValue();
            }
        }

        groupId = fillPlaceholders(groupId, null, properties);
        artifactId = fillPlaceholders(artifactId, null, properties);
        version = fillPlaceholders(version, null, properties);

        if (groupId == null || artifactId == null || version == null) {
            throw new SAXException("Could not get parent from pom.");
        }

        return ArtifactParent.builder(groupId.replaceAll("\\s", ""), artifactId.replaceAll("\\s", ""), version.replaceAll("\\s", ""), cacheDir);
    }

    public static String getLatestVersion(Artifact artifact) throws IOException, XPathExpressionException, SAXException {
        return fetchLatestVersion(DocumentUtil.getDocument(getVersionMetadataURLs(artifact)));
    }

    public static String getLatestVersion(ArtifactParent parent) throws IOException, XPathExpressionException, SAXException {
        return fetchLatestVersion(DocumentUtil.getDocument(getVersionMetadataURLs(parent)));
    }

    private static String fetchLatestVersion(Document document) throws XPathExpressionException, SAXException {
        NodeList latestNodes = DocumentUtil.getNodesByXPath(document, "/metadata/versioning/latest");
        if (latestNodes.getLength() == 0) {
            throw new SAXException("Could not get latest version from metadata.");
        }

        Node latestNode = latestNodes.item(0);
        if (latestNode == null || latestNode.getNodeType() != Node.ELEMENT_NODE) {
            throw new SAXException("Could not get latest version from metadata.");
        }

        Node innerNode = latestNode.getFirstChild();
        if (innerNode == null || innerNode.getNodeType() != Node.TEXT_NODE) {
            throw new SAXException("Could not get latest version from metadata.");
        }

        return innerNode.getNodeValue().replaceAll("\\s", "");
    }

    public static String getReleaseVersion(Artifact artifact) throws IOException, XPathExpressionException, SAXException {
        return fetchReleaseVersion(DocumentUtil.getDocument(getVersionMetadataURLs(artifact)));
    }

    public static String getReleaseVersion(ArtifactParent parent) throws IOException, XPathExpressionException, SAXException {
        return fetchReleaseVersion(DocumentUtil.getDocument(getVersionMetadataURLs(parent)));
    }

    private static String fetchReleaseVersion(Document document) throws XPathExpressionException, SAXException {
        NodeList releaseNodes = DocumentUtil.getNodesByXPath(document, "/metadata/versioning/release");
        if (releaseNodes.getLength() == 0) {
            throw new SAXException("Could not get release version from metadata.");
        }

        Node releaseNode = releaseNodes.item(0);
        if (releaseNode == null || releaseNode.getNodeType() != Node.ELEMENT_NODE) {
            throw new SAXException("Could not get release version from metadata.");
        }

        Node innerNode = releaseNode.getFirstChild();
        if (innerNode == null || innerNode.getNodeType() != Node.TEXT_NODE) {
            throw new SAXException("Could not get release version from metadata.");
        }

        return innerNode.getNodeValue().replaceAll("\\s", "");
    }

    public static String getSnapshotVersion(Artifact artifact) throws IOException, XPathExpressionException, SAXException {
        return artifact.getStrippedVersion() + "-" + fetchSnapshotVersion(DocumentUtil.getDocument(getArtifactMetadataURLs(artifact)));
    }

    public static String getSnapshotVersion(ArtifactParent parent) throws IOException, XPathExpressionException, SAXException {
        return parent.getStrippedVersion() + "-" + fetchSnapshotVersion(DocumentUtil.getDocument(getArtifactMetadataURLs(parent)));
    }

    private static String fetchSnapshotVersion(Document document) throws XPathExpressionException, SAXException {
        NodeList snapshotNodes = DocumentUtil.getNodesByXPath(document, "/metadata/versioning/snapshot");
        if (snapshotNodes.getLength() == 0) {
            throw new SAXException("Could not get snapshot version from metadata.");
        }

        Node snapshotNode = snapshotNodes.item(0);
        if (snapshotNode == null || snapshotNode.getNodeType() != Node.ELEMENT_NODE) {
            throw new SAXException("Could not get snapshot version from metadata.");
        }

        String timestamp = null;
        String buildNumber = null;

        NodeList childNodes = snapshotNode.getChildNodes();
        for (int j = 0; j < childNodes.getLength(); j++) {
            Node childNode = childNodes.item(j);
            if (childNode == null || childNode.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Node innerNode = childNode.getFirstChild();
            if (innerNode == null || innerNode.getNodeType() != Node.TEXT_NODE) {
                continue;
            }

            if (childNode.getNodeName().equals("timestamp")) {
                timestamp = innerNode.getNodeValue();
            } else if (childNode.getNodeName().equals("buildNumber")) {
                buildNumber = innerNode.getNodeValue();
            }
        }

        if (timestamp == null || buildNumber == null) {
            throw new SAXException("Could not get snapshot version from metadata.");
        }

        return timestamp.replaceAll("\\s", "") + "-" + buildNumber.replaceAll("\\s", "");
    }

    private static List<URL> getVersionMetadataURLs(Artifact artifact) throws MalformedURLException {
        List<URL> retVal = new ArrayList<>();

        String group = artifact.getGroupId().replace('.', '/');
        for (String url : artifact.getRepositories()) {
            retVal.add(new URL(url + group + "/" + artifact.getArtifactId() + "/maven-metadata.xml"));
        }
        return retVal;
    }

    private static List<URL> getVersionMetadataURLs(ArtifactParent parent) throws MalformedURLException {
        List<URL> retVal = new ArrayList<>();

        String group = parent.getGroupId().replace('.', '/');
        for (String url : parent.getRepositories()) {
            retVal.add(new URL(url + group + "/" + parent.getArtifactId() + "/maven-metadata.xml"));
        }
        return retVal;
    }

    private static List<URL> getArtifactMetadataURLs(Artifact artifact) throws MalformedURLException, UnsupportedEncodingException {
        List<URL> retVal = new ArrayList<>();

        String group = artifact.getGroupId().replace('.', '/');
        for (String url : artifact.getRepositories()) {
            retVal.add(new URL(url + group + "/" + artifact.getArtifactId() + "/" + encode(artifact.getVersion()) + "/maven-metadata.xml"));
        }
        return retVal;
    }

    private static List<URL> getArtifactMetadataURLs(ArtifactParent parent) throws MalformedURLException, UnsupportedEncodingException {
        List<URL> retVal = new ArrayList<>();

        String group = parent.getGroupId().replace('.', '/');
        for (String url : parent.getRepositories()) {
            retVal.add(new URL(url + group + "/" + parent.getArtifactId() + "/" + encode(parent.getVersion()) + "/maven-metadata.xml"));
        }
        return retVal;
    }

    private static String fillPlaceholders(String text, ArtifactParent parent, Map<String, String> properties) {
        if (text == null || text.isEmpty() || !containsPlaceholder(text)) {
            return text;
        }

        for (Map.Entry<String, String> kvp : properties.entrySet()) {
            text = text.replace("${" + kvp.getKey() + "}", kvp.getValue());
        }

        if (parent != null && containsPlaceholder(text)) {
            return fillPlaceholders(text, parent.getParent(), parent.getProperties());
        }

        return text;
    }

    private static boolean containsPlaceholder(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        int beginIndex = text.indexOf("${");
        int endIndex = text.indexOf('}');
        return beginIndex > -1 && endIndex > beginIndex;
    }

    private static String encode(String raw) throws UnsupportedEncodingException { return URLEncoder.encode(raw, "UTF-8"); }

    private static File getCachePom(Artifact artifact) {
        return new File(artifact.getCacheDir(),
                artifact.getGroupId().replace('.', File.separatorChar)
                        + File.separator + artifact.getArtifactId()
                        + File.separator + artifact.getVersion() + ".pom"
        );
    }

    private static File getCachePom(ArtifactParent parent) {
        return new File(parent.getCacheDir(),
                parent.getGroupId().replace('.', File.separatorChar)
                        + File.separator + parent.getArtifactId()
                        + File.separator + parent.getVersion() + ".pom"
        );
    }
}
