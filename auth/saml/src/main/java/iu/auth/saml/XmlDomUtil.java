/*
 * Copyright © 2024 Indiana University
 * All rights reserved.
 *
 * BSD 3-Clause License
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * 
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * 
 * - Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package iu.auth.saml;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;

import edu.iu.IuException;
import edu.iu.IuText;

/**
 * Provides simplified access to DOM document elements.
 */
public class XmlDomUtil {
	/**
	 * The standard XML date format.
	 */
	public static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

	/**
	 * default constructor
	 */
	public XmlDomUtil() {
	}

	/**
	 * Parse an XML document from a string.
	 * 
	 * @param string The XML document.
	 * @return The document, parsed.
	 */
	public static Document parse(String string) {
		DocumentBuilderFactory builder = DocumentBuilderFactory.newInstance();

		// CWE-611 mitigation: Prevent XXE in XML Parse4
		// See https://cwe.mitre.org/data/definitions/611.html
		// See
		// https://blog.shiftleft.io/preventing-xxe-in-java-applications-d557b6092db1
		IuException.unchecked(() -> {
			builder.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			builder.setFeature("http://xml.org/sax/features/external-general-entities", false);
			builder.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			builder.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			builder.setXIncludeAware(false);
			builder.setExpandEntityReferences(false);
		});

		return IuException.unchecked(() -> {
			return builder.newDocumentBuilder().parse(new ByteArrayInputStream(string.getBytes()));
		});
	}

	/**
	 * Parse an XML input stream to string
	 * 
	 * @param inputStream XML input stream
	 * @return string representation of XML
	 */
	public static String xmlToString(InputStream inputStream) {

		DocumentBuilderFactory builder = DocumentBuilderFactory.newInstance();

		// CWE-611 mitigation: Prevent XXE in XML Parse4
		// See https://cwe.mitre.org/data/definitions/611.html
		// See
		// https://blog.shiftleft.io/preventing-xxe-in-java-applications-d557b6092db1
		return IuException.unchecked(() -> {
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
		});

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
	 * Get the first occurrence of a document element by name.
	 * 
	 * @param doc  The document.
	 * @param name The element name.
	 * @return The first document element found by name, null if no match.
	 */
	public static Element findElement(Document doc, String name) {
		NodeList ctl = doc.getElementsByTagName(name);
		if (ctl.getLength() == 0) {
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
		} catch (IllegalAccessException iae) {
			throw new IllegalStateException("Illegal access acquiring DOM instance", iae);
		} catch (InstantiationException ite) {
			throw new IllegalStateException("Checked exception acquiring DOM instance", ite);
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
		} catch (IllegalAccessException iae) {
			throw new IllegalStateException("Illegal access acquiring DOM LS instance", iae);
		} catch (InstantiationException ite) {
			throw new IllegalStateException("Checked exception acquiring DOM LS instance", ite);
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
		return IuText.utf8(baos.toByteArray());

	}

}
