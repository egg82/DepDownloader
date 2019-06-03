package ninja.egg82.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public class XMLUtil {
    private static DocumentBuilder builder;

    static {
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        builderFactory.setIgnoringElementContentWhitespace(true);

        try {
            builder = builderFactory.newDocumentBuilder();
        } catch (ParserConfigurationException ex) {
            // This should really never happen
            throw new RuntimeException("Could not create XML document parser.", ex);
        }
    }

    private XMLUtil() {}

    public static Document getDocument(File file) throws IOException, SAXException {
        Document retVal = builder.parse(file);
        retVal.normalizeDocument();
        return retVal;
    }

    public static Document getDocument(InputStream stream) throws IOException, SAXException {
        Document retVal = builder.parse(stream);
        retVal.normalizeDocument();
        return retVal;
    }
}
