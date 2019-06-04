package ninja.egg82.utils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class DocumentUtil {
    private static Map<URI, Document> documentCache = new HashMap<>();

    private static final XPathFactory xPathFactory = XPathFactory.newInstance();

    private DocumentUtil() {}

    public static NodeList getNodesByXPath(Document document, String xPath) throws XPathExpressionException {
        XPath xp = xPathFactory.newXPath();
        return (NodeList) xp.evaluate(xPath, document, XPathConstants.NODESET);
    }

    public static synchronized Document getDocument(List<URL> urls) throws IOException, XPathExpressionException, SAXException {
        for (URL url : urls) {
            Document retVal = documentCache.get(toURI(url));
            if (retVal != null) {
                return retVal;
            }
        }

        try (InputStream stream = HTTPUtil.getInputStream(urls)) {
            Document doc = applyProperties(XMLUtil.getDocument(stream));
            for (URL url : urls) {
                documentCache.put(toURI(url), doc);
            }
            return doc;
        }
    }

    private static URI toURI(URL url) throws IOException {
        try {
            return url.toURI();
        } catch (URISyntaxException ex) {
            throw new IOException("Could not convert URL to URI.", ex);
        }
    }

    private static Document applyProperties(Document document) throws XPathExpressionException {
        NodeList propertiesNodes = getNodesByXPath(document, "/project/properties/*");
        if (propertiesNodes.getLength() == 0) {
            return document;
        }

        Map<String, String> properties = new HashMap<>();

        for (int i = 0; i < propertiesNodes.getLength(); i++) {
            Node childNode = propertiesNodes.item(i);
            if (childNode.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Node innerNode = childNode.getFirstChild();
            if (innerNode == null) {
                properties.put(childNode.getNodeName(), "");
                continue;
            }

            if (innerNode.getNodeType() != Node.TEXT_NODE) {
                continue;
            }

            properties.put(childNode.getNodeName(), innerNode.getNodeValue());
        }

        NodeList allNodes = document.getElementsByTagName("*");
        for (int i = 0; i < allNodes.getLength(); i++) {
            Node childNode = allNodes.item(i);
            if (childNode == null || childNode.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Node innerNode = childNode.getFirstChild();
            if (innerNode == null || innerNode.getNodeType() != Node.TEXT_NODE) {
                continue;
            }

            innerNode.setNodeValue(replaceAll(innerNode.getNodeValue(), properties));
        }

        return document;
    }

    private static String replaceAll(String text, Map<String, String> replacements) {
        if (text == null) {
            return null;
        }

        for (Map.Entry<String, String> kvp : replacements.entrySet()) {
            text = text.replace("${" + kvp.getKey() + "}", kvp.getValue());
        }
        return text;
    }
}
