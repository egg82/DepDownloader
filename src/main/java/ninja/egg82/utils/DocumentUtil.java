package ninja.egg82.utils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public class DocumentUtil {
    private static Map<URI, Document> documentCache = new HashMap<>();

    private DocumentUtil() {}

    public static synchronized Document getDocument(List<URL> urls) throws IOException, SAXException {
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

    private static URI toURI(URL url) throws IOException {
        try {
            return url.toURI();
        } catch (URISyntaxException ex) {
            throw new IOException("Could not convert URL to URI.", ex);
        }
    }
}
