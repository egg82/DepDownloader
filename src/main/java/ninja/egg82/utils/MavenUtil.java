package ninja.egg82.utils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import ninja.egg82.maven.Artifact;
import ninja.egg82.maven.ArtifactParent;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class MavenUtil {
    private MavenUtil() {}

    public static List<Artifact> getDependencies(Artifact artifact) throws URISyntaxException, IOException, SAXException {
        List<Artifact> retVal = new ArrayList<>();
        List<Artifact.Builder> builders = fetchDependencies(DocumentUtil.getDocument(HTTPUtil.toURLs(artifact.getPomURIs())));

        for (Artifact.Builder builder : builders) {
            for (String repository : artifact.getRepositories()) {
                builder.addRepository(repository);
            }
            for (String declaredRepository : artifact.getDeclaredRepositories()) {
                builder.addRepository(declaredRepository);
            }
            ArtifactParent p;
            while ((p = artifact.getParent()) != null) {
                for (String declaredRepository : p.getDeclaredRepositories()) {
                    builder.addRepository(declaredRepository);
                }
            }
            builder.addRepository("http://central.maven.org/maven2/");
            retVal.add(builder.build());
        }
        return retVal;
    }

    public static List<Artifact> getDependencies(ArtifactParent parent) throws URISyntaxException, IOException, SAXException {
        List<Artifact> retVal = new ArrayList<>();
        List<Artifact.Builder> builders = fetchDependencies(DocumentUtil.getDocument(HTTPUtil.toURLs(parent.getPomURIs())));

        for (Artifact.Builder builder : builders) {
            for (String repository : parent.getRepositories()) {
                builder.addRepository(repository);
            }
            for (String declaredRepository : parent.getDeclaredRepositories()) {
                builder.addRepository(declaredRepository);
            }
            ArtifactParent p;
            while ((p = parent.getParent()) != null) {
                for (String declaredRepository : p.getDeclaredRepositories()) {
                    builder.addRepository(declaredRepository);
                }
            }
            builder.addRepository("http://central.maven.org/maven2/");
            retVal.add(builder.build());
        }
        return retVal;
    }

    private static List<Artifact.Builder> fetchDependencies(Document document) throws SAXException {
        List<Artifact.Builder> retVal = new ArrayList<>();

        NodeList dependenciesList = document.getElementsByTagName("dependencies");
        if (dependenciesList.getLength() != 1) {
            return retVal;
        }
        Element dependenciesElement = (Element) dependenciesList.item(0);

        NodeList dependencyList = dependenciesElement.getElementsByTagName("dependency");
        for (int i = 0; i < dependencyList.getLength(); i++) {
            Element dependencyElement = (Element) dependencyList.item(i);

            NodeList groupIdList = dependencyElement.getElementsByTagName("groupId");
            if (groupIdList.getLength() != 1) {
                throw new SAXException("Could not get dependencies from pom.");
            }
            Element groupIdElement = (Element) groupIdList.item(0);

            NodeList artifactIdList = dependencyElement.getElementsByTagName("artifactId");
            if (artifactIdList.getLength() != 1) {
                throw new SAXException("Could not get dependencies from pom.");
            }
            Element artifactIdElement = (Element) artifactIdList.item(0);

            NodeList versionList = dependencyElement.getElementsByTagName("version");
            if (versionList.getLength() != 1) {
                throw new SAXException("Could not get dependencies from pom.");
            }
            Element versionElement = (Element) versionList.item(0);

            retVal.add(Artifact.builder(groupIdElement.getTextContent(), artifactIdElement.getTextContent(), versionElement.getTextContent()));
        }

        return retVal;
    }

    public static List<String> getDeclaredRepositories(Artifact artifact) throws IOException, SAXException {
        return fetchDeclaredRepositories(DocumentUtil.getDocument(HTTPUtil.toURLs(artifact.getPomURIs())));
    }

    public static List<String> getDeclaredRepositories(ArtifactParent parent) throws IOException, SAXException {
        return fetchDeclaredRepositories(DocumentUtil.getDocument(HTTPUtil.toURLs(parent.getPomURIs())));
    }

    private static List<String> fetchDeclaredRepositories(Document document) throws SAXException {
        List<String> retVal = new ArrayList<>();

        NodeList repositoriesList = document.getElementsByTagName("repositories");
        if (repositoriesList.getLength() != 1) {
            return retVal;
        }
        Element repositoriesElement = (Element) repositoriesList.item(0);

        NodeList repositoryList = repositoriesElement.getElementsByTagName("repository");
        for (int i = 0; i < repositoryList.getLength(); i++) {
            Element repositoryElement = (Element) repositoryList.item(i);

            NodeList urlList = repositoryElement.getElementsByTagName("url");
            if (urlList.getLength() != 1) {
                throw new SAXException("Could not get repositories from pom.");
            }

            String url = urlList.item(0).getTextContent();
            if (url == null || url.isEmpty()) {
                continue;
            }
            if (url.charAt(url.length() - 1) != '/') {
                url = url + "/";
            }
            retVal.add(url);
        }

        return retVal;
    }

    public static ArtifactParent getParent(Artifact artifact) throws URISyntaxException, IOException, SAXException {
        ArtifactParent.Builder retVal = fetchArtifactParent(DocumentUtil.getDocument(HTTPUtil.toURLs(artifact.getPomURIs())));
        if (retVal == null) {
            return null;
        }

        for (String repository : artifact.getRepositories()) {
            retVal.addRepository(repository);
        }
        return retVal.build();
    }

    public static ArtifactParent getParent(ArtifactParent parent) throws URISyntaxException, IOException, SAXException {
        ArtifactParent.Builder retVal = fetchArtifactParent(DocumentUtil.getDocument(HTTPUtil.toURLs(parent.getPomURIs())));
        if (retVal == null) {
            return null;
        }

        for (String repository : parent.getRepositories()) {
            retVal.addRepository(repository);
        }
        return retVal.build();
    }

    private static ArtifactParent.Builder fetchArtifactParent(Document document) throws SAXException {
        NodeList parentList = document.getElementsByTagName("parent");
        if (parentList.getLength() != 1) {
            return null;
        }
        Element parentElement = (Element) parentList.item(0);

        NodeList groupIdList = parentElement.getElementsByTagName("groupId");
        if (groupIdList.getLength() != 1) {
            throw new SAXException("Could not get parent from pom.");
        }
        Element groupIdElement = (Element) groupIdList.item(0);

        NodeList artifactIdList = parentElement.getElementsByTagName("artifactId");
        if (artifactIdList.getLength() != 1) {
            throw new SAXException("Could not get parent from pom.");
        }
        Element artifactIdElement = (Element) artifactIdList.item(0);

        NodeList versionList = parentElement.getElementsByTagName("version");
        if (versionList.getLength() != 1) {
            throw new SAXException("Could not get parent from pom.");
        }
        Element versionElement = (Element) versionList.item(0);

        return ArtifactParent.builder(groupIdElement.getTextContent(), artifactIdElement.getTextContent(), versionElement.getTextContent());
    }

    public static String getLatestVersion(Artifact artifact) throws IOException, SAXException {
        return fetchLatestVersion(DocumentUtil.getDocument(getVersionMetadataURLs(artifact)));
    }

    public static String getLatestVersion(ArtifactParent parent) throws IOException, SAXException {
        return fetchLatestVersion(DocumentUtil.getDocument(getVersionMetadataURLs(parent)));
    }

    private static String fetchLatestVersion(Document document) throws SAXException {
        NodeList versioningList = document.getElementsByTagName("versioning");
        if (versioningList.getLength() != 1) {
            throw new SAXException("Could not get latest version from pom.");
        }
        Element versioningElement = (Element) versioningList.item(0);

        NodeList latestList = versioningElement.getElementsByTagName("latest");
        if (latestList.getLength() != 1) {
            throw new SAXException("Could not get latest version from pom.");
        }
        return latestList.item(0).getTextContent();
    }

    public static String getReleaseVersion(Artifact artifact) throws IOException, SAXException {
        return fetchReleaseVersion(DocumentUtil.getDocument(getVersionMetadataURLs(artifact)));
    }

    public static String getReleaseVersion(ArtifactParent parent) throws IOException, SAXException {
        return fetchReleaseVersion(DocumentUtil.getDocument(getVersionMetadataURLs(parent)));
    }

    private static String fetchReleaseVersion(Document document) throws SAXException {
        NodeList versioningList = document.getElementsByTagName("versioning");
        if (versioningList.getLength() != 1) {
            throw new SAXException("Could not get release version from pom.");
        }
        Element versioningElement = (Element) versioningList.item(0);

        NodeList latestList = versioningElement.getElementsByTagName("release");
        if (latestList.getLength() != 1) {
            throw new SAXException("Could not get release version from pom.");
        }
        return latestList.item(0).getTextContent();
    }

    public static String getSnapshotVersion(Artifact artifact) throws IOException, SAXException {
        return artifact.getStrippedVersion() + "-" + fetchSnapshotVersion(DocumentUtil.getDocument(getArtifactMetadataURLs(artifact)));
    }

    public static String getSnapshotVersion(ArtifactParent parent) throws IOException, SAXException {
        return parent.getStrippedVersion() + "-" + fetchSnapshotVersion(DocumentUtil.getDocument(getArtifactMetadataURLs(parent)));
    }

    private static String fetchSnapshotVersion(Document document) throws SAXException {
        NodeList versioningList = document.getElementsByTagName("versioning");
        if (versioningList.getLength() != 1) {
            throw new SAXException("Could not get snapshot version from pom.");
        }
        Element versioningElement = (Element) versioningList.item(0);

        NodeList snapshotList = versioningElement.getElementsByTagName("snapshot");
        if (snapshotList.getLength() != 1) {
            throw new SAXException("Could not get snapshot version from pom.");
        }
        Element snapshotElement = (Element) snapshotList.item(0);

        NodeList timestampList = snapshotElement.getElementsByTagName("timestamp");
        if (timestampList.getLength() != 1) {
            throw new SAXException("Could not get snapshot version from pom.");
        }
        Element timestampElement = (Element) timestampList.item(0);

        NodeList buildNumberList = snapshotElement.getElementsByTagName("buildNumber");
        if (buildNumberList.getLength() != 1) {
            throw new SAXException("Could not get snapshot version from pom.");
        }
        Element buildNumberElement = (Element) buildNumberList.item(0);

        return timestampElement.getTextContent() + "-" + buildNumberElement.getTextContent();
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

    private static List<URL> getArtifactMetadataURLs(Artifact artifact) throws MalformedURLException {
        List<URL> retVal = new ArrayList<>();

        String group = artifact.getGroupId().replace('.', '/');
        for (String url : artifact.getRepositories()) {
            retVal.add(new URL(url + group + "/" + artifact.getArtifactId() + "/" + artifact.getVersion() + "/maven-metadata.xml"));
        }
        return retVal;
    }

    private static List<URL> getArtifactMetadataURLs(ArtifactParent parent) throws MalformedURLException {
        List<URL> retVal = new ArrayList<>();

        String group = parent.getGroupId().replace('.', '/');
        for (String url : parent.getRepositories()) {
            retVal.add(new URL(url + group + "/" + parent.getArtifactId() + "/" + parent.getVersion() + "/maven-metadata.xml"));
        }
        return retVal;
    }
}
