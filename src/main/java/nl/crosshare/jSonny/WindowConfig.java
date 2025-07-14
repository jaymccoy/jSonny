package nl.crosshare.jSonny;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import java.io.*;

public class WindowConfig {
    public double x, y, width, height;
    public double[] dividers;

    public static WindowConfig load(File file) throws Exception {
        WindowConfig config = new WindowConfig();
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
        Element root = doc.getDocumentElement();
        config.x = Double.parseDouble(root.getElementsByTagName("x").item(0).getTextContent());
        config.y = Double.parseDouble(root.getElementsByTagName("y").item(0).getTextContent());
        config.width = Double.parseDouble(root.getElementsByTagName("width").item(0).getTextContent());
        config.height = Double.parseDouble(root.getElementsByTagName("height").item(0).getTextContent());
        NodeList divs = ((Element)root.getElementsByTagName("splitPaneDividers").item(0)).getChildNodes();
        config.dividers = new double[2];
        int idx = 0;
        for (int i = 0; i < divs.getLength(); i++) {
            if (divs.item(i) instanceof Element) {
                config.dividers[idx++] = Double.parseDouble(divs.item(i).getTextContent());
            }
        }
        return config;
    }

    public void save(File file) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.newDocument();
        Element root = doc.createElement("window");
        doc.appendChild(root);

        root.appendChild(createElem(doc, "x", String.valueOf(x)));
        root.appendChild(createElem(doc, "y", String.valueOf(y)));
        root.appendChild(createElem(doc, "width", String.valueOf(width)));
        root.appendChild(createElem(doc, "height", String.valueOf(height)));

        Element divs = doc.createElement("splitPaneDividers");
        for (int i = 0; i < dividers.length; i++) {
            divs.appendChild(createElem(doc, "divider" + i, String.valueOf(dividers[i])));
        }
        root.appendChild(divs);

        Transformer t = TransformerFactory.newInstance().newTransformer();
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        t.transform(new DOMSource(doc), new StreamResult(file));
    }

    private static Element createElem(Document doc, String name, String value) {
        Element e = doc.createElement(name);
        e.setTextContent(value);
        return e;
    }
}