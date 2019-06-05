package ninja.egg82.utils;

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

    public static Map<String, String> getProperties(Artifact artifact) throws IOException, XPathExpressionException, SAXException {
        return fetchProperties(DocumentUtil.getDocument(HTTPUtil.toURLs(artifact.getPomURIs())));
    }

    public static Map<String, String> getProperties(ArtifactParent parent) throws IOException, XPathExpressionException, SAXException {
        return fetchProperties(DocumentUtil.getDocument(HTTPUtil.toURLs(parent.getPomURIs())));
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

    public static List<Artifact> getDependencies(Artifact artifact, Scope[] targetDependencyScopes) throws URISyntaxException, IOException, XPathExpressionException, SAXException {
        System.out.println("Getting deps for " + artifact.getGroupId() + ":" + artifact.getArtifactId() + "::" + artifact.getVersion());

        Set<String> repositories = getRepositories(artifact);

        List<Artifact> retVal = new ArrayList<>();
        List<Artifact.Builder> builders = fetchHardDependencies(artifact.getParent(), repositories, DocumentUtil.getDocument(HTTPUtil.toURLs(artifact.getPomURIs())), targetDependencyScopes, artifact.getProperties());

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

    public static List<Artifact> getSoftDependencies(ArtifactParent parent, Scope[] targetDependencyScopes) throws URISyntaxException, IOException, XPathExpressionException, SAXException {
        System.out.println("Getting soft deps for " + parent.getGroupId() + ":" + parent.getArtifactId() + "::" + parent.getVersion());

        Set<String> repositories = getRepositories(parent);

        List<Artifact> retVal = new ArrayList<>();
        List<Artifact.Builder> builders = fetchSoftDependencies(parent.getParent(), repositories, DocumentUtil.getDocument(HTTPUtil.toURLs(parent.getPomURIs())), targetDependencyScopes, parent.getProperties());

        for (Artifact.Builder builder : builders) {
            for (String repository : repositories) {
                builder.addRepository(repository);
            }
            retVal.add(builder.build());
        }
        return retVal;
    }

    public static List<Artifact> getHardDependencies(ArtifactParent parent, Scope[] targetDependencyScopes) throws URISyntaxException, IOException, XPathExpressionException, SAXException {
        System.out.println("Getting hard deps for " + parent.getGroupId() + ":" + parent.getArtifactId() + "::" + parent.getVersion());

        Set<String> repositories = getRepositories(parent);

        List<Artifact> retVal = new ArrayList<>();
        List<Artifact.Builder> builders = fetchHardDependencies(parent.getParent(), repositories, DocumentUtil.getDocument(HTTPUtil.toURLs(parent.getPomURIs())), targetDependencyScopes, parent.getProperties());

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

    private static List<Artifact.Builder> fetchSoftDependencies(ArtifactParent parent, Set<String> repositories, Document document, Scope[] targetDependencyScopes, Map<String, String> properties) throws IOException, XPathExpressionException, SAXException {
        return fetchDependencies(parent, repositories, DocumentUtil.getNodesByXPath(document, "/project/dependencyManagement/dependencies/dependency"), targetDependencyScopes, properties);
    }

    private static List<Artifact.Builder> fetchHardDependencies(ArtifactParent parent, Set<String> repositories, Document document, Scope[] targetDependencyScopes, Map<String, String> properties) throws IOException, XPathExpressionException, SAXException {
        return fetchDependencies(parent, repositories, DocumentUtil.getNodesByXPath(document, "/project/dependencies/dependency"), targetDependencyScopes, properties);
    }

    private static List<Artifact.Builder> fetchDependencies(ArtifactParent parent, Set<String> repositories, NodeList dependencyNodes, Scope[] targetDependencyScopes, Map<String, String> properties) throws IOException, SAXException {
        List<Artifact.Builder> retVal = new ArrayList<>();

        System.out.println("Num deps: " + dependencyNodes.getLength());

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
                    for (Artifact dependency : p.getSoftDependencies()) {
                        if (
                                dependency.getGroupId().equals(groupId)
                                && dependency.getArtifactId().equals(artifactId)
                        ) {
                            version = dependency.getVersion();
                            break;
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

            System.out.println("Group: " + groupId);
            System.out.println("Artifact: " + artifactId);
            System.out.println("Version: " + version);
            System.out.println("Scope: " + scope);

            if (groupId == null || artifactId == null || version == null) {
                throw new SAXException("Could not get dependencies from pom.");
            }

            Scope scopeEnum = Scope.fromName(scope);

            if (!hasScope(targetDependencyScopes, scopeEnum)) {
                continue;
            }

            retVal.add(Artifact.builder(groupId, artifactId, version, scopeEnum));
        }

        return retVal;
    }

    public static List<String> getDeclaredRepositories(Artifact artifact) throws IOException, XPathExpressionException, SAXException {
        System.out.println("Getting declared repositories for " + artifact.getGroupId() + ":" + artifact.getArtifactId() + "::" + artifact.getVersion());
        return fetchDeclaredRepositories(DocumentUtil.getDocument(HTTPUtil.toURLs(artifact.getPomURIs())), artifact.getParent(), artifact.getProperties());
    }

    public static List<String> getDeclaredRepositories(ArtifactParent parent) throws IOException, XPathExpressionException, SAXException {
        System.out.println("Getting declared repositories for " + parent.getGroupId() + ":" + parent.getArtifactId() + "::" + parent.getVersion());
        return fetchDeclaredRepositories(DocumentUtil.getDocument(HTTPUtil.toURLs(parent.getPomURIs())), parent.getParent(), parent.getProperties());
    }

    private static List<String> fetchDeclaredRepositories(Document document, ArtifactParent parent, Map<String, String> properties) throws XPathExpressionException, SAXException {
        List<String> retVal = new ArrayList<>();

        NodeList repositoryNodes = DocumentUtil.getNodesByXPath(document, "/project/repositories/repository");
        System.out.println("Num repos: " + repositoryNodes.getLength());
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

            if (url.charAt(url.length() - 1) != '/') {
                url = url + "/";
            }
            retVal.add(url);
        }

        return retVal;
    }

    public static ArtifactParent getParent(Artifact artifact) throws URISyntaxException, IOException, XPathExpressionException, SAXException {
        System.out.println("Getting parent for " + artifact.getGroupId() + ":" + artifact.getArtifactId() + "::" + artifact.getVersion());
        ArtifactParent.Builder retVal = fetchArtifactParent(DocumentUtil.getDocument(HTTPUtil.toURLs(artifact.getPomURIs())), null, artifact.getProperties());
        if (retVal == null) {
            return null;
        }

        for (String repository : artifact.getRepositories()) {
            retVal.addRepository(repository);
        }
        return retVal.build();
    }

    public static ArtifactParent getParent(ArtifactParent parent) throws URISyntaxException, IOException, XPathExpressionException, SAXException {
        System.out.println("Getting parent for " + parent.getGroupId() + ":" + parent.getArtifactId() + "::" + parent.getVersion());
        ArtifactParent.Builder retVal = fetchArtifactParent(DocumentUtil.getDocument(HTTPUtil.toURLs(parent.getPomURIs())), null, parent.getProperties());
        if (retVal == null) {
            return null;
        }

        for (String repository : parent.getRepositories()) {
            retVal.addRepository(repository);
        }
        return retVal.build();
    }

    private static ArtifactParent.Builder fetchArtifactParent(Document document, ArtifactParent parent, Map<String, String> properties) throws XPathExpressionException, SAXException {
        NodeList parentNodes = DocumentUtil.getNodesByXPath(document, "/project/parent");
        System.out.println("Has parent: " + (parentNodes.getLength() != 0));
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

        groupId = fillPlaceholders(groupId, parent, properties);
        artifactId = fillPlaceholders(artifactId, parent, properties);
        version = fillPlaceholders(version, parent, properties);

        if (groupId == null || artifactId == null || version == null) {
            throw new SAXException("Could not get parent from pom.");
        }

        return ArtifactParent.builder(groupId, artifactId, version);
    }

    public static String getLatestVersion(Artifact artifact) throws IOException, XPathExpressionException, SAXException {
        System.out.println("Getting latest version for " + artifact.getGroupId() + ":" + artifact.getArtifactId() + "::" + artifact.getVersion());
        return fetchLatestVersion(DocumentUtil.getDocument(getVersionMetadataURLs(artifact)));
    }

    public static String getLatestVersion(ArtifactParent parent) throws IOException, XPathExpressionException, SAXException {
        System.out.println("Getting latest version for " + parent.getGroupId() + ":" + parent.getArtifactId() + "::" + parent.getVersion());
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

        System.out.println("Version: " + innerNode.getNodeValue());
        return innerNode.getNodeValue();
    }

    public static String getReleaseVersion(Artifact artifact) throws IOException, XPathExpressionException, SAXException {
        System.out.println("Getting release version for " + artifact.getGroupId() + ":" + artifact.getArtifactId() + "::" + artifact.getVersion());
        return fetchReleaseVersion(DocumentUtil.getDocument(getVersionMetadataURLs(artifact)));
    }

    public static String getReleaseVersion(ArtifactParent parent) throws IOException, XPathExpressionException, SAXException {
        System.out.println("Getting release version for " + parent.getGroupId() + ":" + parent.getArtifactId() + "::" + parent.getVersion());
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

        System.out.println("Version: " + innerNode.getNodeValue());
        return innerNode.getNodeValue();
    }

    public static String getSnapshotVersion(Artifact artifact) throws IOException, XPathExpressionException, SAXException {
        System.out.println("Getting snapshot version for " + artifact.getGroupId() + ":" + artifact.getArtifactId() + "::" + artifact.getVersion());
        return artifact.getStrippedVersion() + "-" + fetchSnapshotVersion(DocumentUtil.getDocument(getArtifactMetadataURLs(artifact)));
    }

    public static String getSnapshotVersion(ArtifactParent parent) throws IOException, XPathExpressionException, SAXException {
        System.out.println("Getting snapshot version for " + parent.getGroupId() + ":" + parent.getArtifactId() + "::" + parent.getVersion());
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

        System.out.println("Timestamp: " + timestamp);
        System.out.println("Build: " + buildNumber);

        if (timestamp == null || buildNumber == null) {
            throw new SAXException("Could not get snapshot version from metadata.");
        }

        return timestamp + "-" + buildNumber;
    }

    private static List<URL> getVersionMetadataURLs(Artifact artifact) throws MalformedURLException, UnsupportedEncodingException {
        List<URL> retVal = new ArrayList<>();

        String group = artifact.getGroupId().replace('.', '/');
        for (String url : artifact.getRepositories()) {
            retVal.add(new URL(url + group + "/" + artifact.getArtifactId() + "/maven-metadata.xml"));
        }
        return retVal;
    }

    private static List<URL> getVersionMetadataURLs(ArtifactParent parent) throws MalformedURLException, UnsupportedEncodingException {
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

    private static boolean hasScope(Scope[] scopes, Scope scope) {
        for (Scope s : scopes) {
            if (s.equals(scope)) {
                return true;
            }
        }
        return false;
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
}
