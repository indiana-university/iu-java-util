package iu.auth.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.SAXException;

/**
 * Provides simplified access to DOM document elements.
 */
public class XmlDomUtil {
	/**
	 * The standard XML date format.
	 */
	public static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
	
	/**
	 *  default constructor
	 */
	public XmlDomUtil() {
		// TODO Auto-generated constructor stub
	}
	

	/**
	 * Parse an XML document from a string.
	 * 
	 * @param xml The XML document.
	 * @return The document, parsed.
	 */
	public static Document parse(String xml) {
		try {
			DocumentBuilderFactory builder = DocumentBuilderFactory.newInstance();
			
			// CWE-611 mitigation: Prevent XXE in XML Parse4
			// See https://cwe.mitre.org/data/definitions/611.html
			// See https://blog.shiftleft.io/preventing-xxe-in-java-applications-d557b6092db1
			builder.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			builder.setFeature("http://xml.org/sax/features/external-general-entities", false);
			builder.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			builder.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			builder.setXIncludeAware(false);
			builder.setExpandEntityReferences(false);
			
			return builder.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes()));
		} catch (SAXException e) {
			throw new IllegalStateException(e);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		} catch (ParserConfigurationException e) {
			throw new IllegalStateException(e);
		}
	}
	
	/**
	 * Parse XML input stream to string
	 * @param inputStream xml input stream
	 * @return string representation of XML 
	 */
	public static String xmlToString(InputStream inputStream) {
		try {
			DocumentBuilderFactory builder = DocumentBuilderFactory.newInstance();
			
			// CWE-611 mitigation: Prevent XXE in XML Parse4
			// See https://cwe.mitre.org/data/definitions/611.html
			// See https://blog.shiftleft.io/preventing-xxe-in-java-applications-d557b6092db1
			builder.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			builder.setFeature("http://xml.org/sax/features/external-general-entities", false);
			builder.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			builder.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			builder.setXIncludeAware(false);
			builder.setExpandEntityReferences(false);
			Document doc = builder.newDocumentBuilder().parse(inputStream);
			StringWriter stw = new StringWriter();
			Transformer serializer = TransformerFactory.newInstance().newTransformer();
			serializer.transform(new DOMSource(doc), new StreamResult(stw));
			return stw.toString();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Get an attribute value for a node.
	 * 
	 * @param node The node.
	 * @param attr The attribute name.
	 * @return The attribute value for the node.
	 */
	public static String getAttribute(Node node, String attr) {
		if (!node.hasAttributes()) {
			return null;
		}
		Node rv = node.getAttributes().getNamedItem(attr);
		if (rv == null) {
			return null;
		}
		return rv.getTextContent();
	}

	/**
	 * Get an array of child nodes with the given node name.
	 * 
	 * @param node The node.
	 * @param name The child node name.
	 * @return An array of nodes that match the given node name.
	 */
	public static Node[] getChildNodes(Node node, String name) {
		List<Node> rv = new java.util.LinkedList<Node>();
		NodeList children = node.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node c = children.item(i);
			if (name.equals(c.getNodeName())) {
				rv.add(c);
			}
		}
		return rv.toArray(new Node[rv.size()]);
	}

	/**
	 * Get the first child node with a given node name.
	 * 
	 * @param node The node.
	 * @param name The child node name.
	 * @return The first child node with the given node name.
	 */
	public static Node getChildNode(Node node, String name) {
		NodeList children = node.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node c = children.item(i);
			if (name.equals(c.getNodeName())) {
				return c;
			}
		}
		return null;
	}

	/**
	 * Determine if a node has any non-text child nodes.
	 * 
	 * @param node The node.
	 * @return True if the node has any non-text child nodes, false if no children
	 *         or text only.
	 */
	public static boolean hasNonTextChildNodes(Node node) {
		if (!node.hasChildNodes()) {
			return false;
		}
		NodeList children = node.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			if (!(children.item(i) instanceof Text)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Get the first occurrence of a document element by name.
	 * 
	 * @param doc  The document.
	 * @param name The element name.
	 * @return The first document element found by name, null if no match.
	 */
	public static Element findElement(Document doc, String name) {
		NodeList ctl = doc.getElementsByTagName(name);
		if (ctl == null || ctl.getLength() == 0) {
			return null;
		}
		return (Element) ctl.item(0);
	}

	/**
	 * Convert a date to a standard XML date string.
	 * 
	 * @param rv The date.
	 * @return The standard XML date string.
	 */
	public static String dateToString(Date rv) {
		DateFormat df = new java.text.SimpleDateFormat(DATE_FORMAT);
		return df.format(rv);
	}

	/**
	 * Convert a date to a standard XML date string.
	 * 
	 * @param rv The date.
	 * @return The standard XML date string.
	 */
	public static Date stringToDate(String rv) {
		DateFormat df = new java.text.SimpleDateFormat(DATE_FORMAT);
		try {
			return df.parse(rv);
		} catch (ParseException e) {
			throw new IllegalArgumentException("Invalid XML date: " + rv, e);
		}
	}

	/**
	 * Get a DOM instance.
	 * 
	 * @return DOM instance
	 */
	public static DOMImplementation getDom() {
		try {
			return DOMImplementationRegistry.newInstance().getDOMImplementation("XML");
		} catch (IllegalAccessException iaE) {
			throw new IllegalStateException("Illegal access acquiring DOM instance", iaE);
		} catch (InstantiationException itE) {
			Throwable c = itE.getCause();
			if (c instanceof RuntimeException) {
				throw (RuntimeException) c;
			} else if (c instanceof Error) {
				throw (Error) c;
			} else {
				throw new IllegalStateException("Checked exception acquiring DOM instance", c);
			}
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException("Class not found acquiring DOM instance", e);
		}
	}

	/**
	 * Get a DOM LS instance.
	 * 
	 * @return DOM LS instance
	 */
	public static DOMImplementationLS getDomLS() {
		try {
			return (DOMImplementationLS) DOMImplementationRegistry.newInstance().getDOMImplementation("LS");
		} catch (IllegalAccessException iaE) {
			throw new IllegalStateException("Illegal access acquiring DOM LS instance", iaE);
		} catch (InstantiationException itE) {
			Throwable c = itE.getCause();
			if (c instanceof RuntimeException) {
				throw (RuntimeException) c;
			} else if (c instanceof Error) {
				throw (Error) c;
			} else {
				throw new IllegalStateException("Checked exception acquiring DOM LS instance", c);
			}
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException("Class not found acquiring DOM LS instance", e);
		}
	}

	/**
	 * Render a DOM node as a string.
	 * 
	 * @param n The node.
	 * @return A string representation of a DOM node.
	 */
	public static String getContent(Node n) {
		DOMImplementationLS ls = getDomLS();
		LSSerializer ser = ls.createLSSerializer();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		LSOutput lso = ls.createLSOutput();
		lso.setEncoding("UTF-8");
		lso.setByteStream(baos);
		ser.write(n, lso);
		try {
			return new String(baos.toByteArray(), "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new IllegalStateException("UTF-8 is unsupported", e);
		}
	}


}
