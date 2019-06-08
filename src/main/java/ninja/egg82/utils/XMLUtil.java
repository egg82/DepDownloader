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
        builderFactory.setIgnoringComments(true);

        try {
            builder = builderFactory.newDocumentBuilder();
        } catch (ParserConfigurationException ex) {
            // This should really never happen
            throw new RuntimeException("Could not create XML document parser.", ex);
        }
    }

    private XMLUtil() {}

    public static Document getDocument(File file) throws IOException {
        Document retVal;
        try {
            retVal = builder.parse(file);
        } catch (SAXException ignored) {
            // I have no idea why some pom files aren't XML, but this is where we are.
            retVal = builder.newDocument();
        }
        retVal.normalizeDocument();
        return retVal;
    }

    public static Document getDocument(InputStream stream) throws IOException {
        Document retVal;
        try {
            retVal = builder.parse(stream);
        } catch (SAXException ignored) {
            // I have no idea why some pom files aren't XML, but this is where we are.
            retVal = builder.newDocument();
        }
        retVal.normalizeDocument();
        return retVal;
    }
}
