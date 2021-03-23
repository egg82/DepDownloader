package ninja.egg82.utils;

import java.io.File;
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
import org.w3c.dom.NodeList;

public class DocumentUtil {
    private static Map<URI, Document> documentCache = new HashMap<>();

    private static final XPathFactory xPathFactory = XPathFactory.newInstance();

    private DocumentUtil() {}

    public static NodeList getNodesByXPath(Document document, String xPath) throws XPathExpressionException {
        XPath xp = xPathFactory.newXPath();
        return (NodeList) xp.evaluate(xPath, document, XPathConstants.NODESET);
    }

    public static synchronized Document getDocument(List<URL> urls) throws IOException {
        for (URL url : urls) {
            Document retVal = documentCache.get(toURI(url));
            if (retVal != null) {
                return retVal;
            }
        }

        try (InputStream stream = HTTPUtil.getInputStream(urls)) {
            Document doc = XMLUtil.getDocument(stream);
            for (URL url : urls) {
                documentCache.put(toURI(url), doc);
            }
            return doc;
        }
    }

    public static synchronized Document getDocument(File file) throws IOException {
        URI uri = file.toURI();
        Document retVal = documentCache.get(uri);
        if (retVal != null) {
            return retVal;
        }

        Document doc = XMLUtil.getDocument(file);
        documentCache.put(uri, doc);
        return doc;
    }

    private static URI toURI(URL url) throws IOException {
        try {
            return url.toURI();
        } catch (URISyntaxException ex) {
            throw new IOException("Could not convert URL to URI.", ex);
        }
    }
    
    pubic static synchronized void clearDocumentCache() {
        documentCache.clear();
    }
}
