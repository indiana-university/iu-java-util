package iu.auth.saml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import java.io.ByteArrayInputStream;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;

@SuppressWarnings("javadoc")
public class XmlDomUtilTest {

	@Test
	public void testParse() {
		XmlDomUtil util = new XmlDomUtil();
		Document doc = XmlDomUtil.parse("<foo><bar>baz</bar></foo>");
		assertEquals(1, doc.getChildNodes().getLength());
		assertEquals("foo", doc.getChildNodes().item(0).getNodeName());
	}

	@Test
	public void testAttribute() {
		Document doc = XmlDomUtil.parse("<foo bar='bam'><bar>baz</bar></foo>");
		assertEquals("bam", XmlDomUtil.getAttribute(doc.getDocumentElement(), "bar"));
		assertNull(XmlDomUtil.getAttribute(doc.getDocumentElement(), "tmp"));
		doc = XmlDomUtil.parse("<foo></foo>");
		assertNull(XmlDomUtil.getAttribute(doc.getDocumentElement(), "bar"));

	}

	@Test
	public void testChildElement() throws Exception {
		Document doc = XmlDomUtil.parse("<foo><bar>baz</bar></foo>");
		assertEquals("baz", XmlDomUtil.findElement(doc, "bar").getTextContent());
		assertNull(XmlDomUtil.findElement(doc, "bam"));
	}

	@Test
	void testGetChildNodes() {
		Document doc = XmlDomUtil.parse("<foo><bar>baz</bar></foo>");
		assertNotNull(XmlDomUtil.getChildNodes(doc.getDocumentElement(), "bar"));
		assertNotNull(XmlDomUtil.getChildNodes(doc.getDocumentElement(), "child"));
	}

	@Test
	public void testChildNode() {
		Document doc = XmlDomUtil.parse("<foo bar='bam'><bar>baz</bar></foo>");
		assertEquals("baz", XmlDomUtil.getChildNode(doc.getDocumentElement(), "bar").getTextContent());
		assertNull(XmlDomUtil.getChildNode(doc.getDocumentElement(), "tmp"));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testGetDom() {
		assertNotNull(XmlDomUtil.getDom());
		try (final var mockRegistry = mockStatic(DOMImplementationRegistry.class)) {
			mockRegistry.when(() -> DOMImplementationRegistry.newInstance()).thenThrow(IllegalAccessException.class,
					InstantiationException.class, ClassNotFoundException.class);
			assertThrows(IllegalStateException.class, () -> XmlDomUtil.getDom());
			assertThrows(IllegalStateException.class, () -> XmlDomUtil.getDom());
			assertThrows(IllegalStateException.class, () -> XmlDomUtil.getDom());
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testGetDomLS() {
		assertNotNull(XmlDomUtil.getDomLS());
		try (final var mockRegistry = mockStatic(DOMImplementationRegistry.class)) {
			mockRegistry.when(() -> DOMImplementationRegistry.newInstance()).thenThrow(IllegalAccessException.class,
					InstantiationException.class, ClassNotFoundException.class);
			assertThrows(IllegalStateException.class, () -> XmlDomUtil.getDomLS());
			assertThrows(IllegalStateException.class, () -> XmlDomUtil.getDomLS());
			assertThrows(IllegalStateException.class, () -> XmlDomUtil.getDomLS());
		}
	}

	@Test
	public void testGetContent() {
		Document doc = XmlDomUtil.parse("<foo bar='bam'><bar>baz</bar></foo>");
		assertNotNull(XmlDomUtil.getContent(doc.getDocumentElement()));
	}

	@Test
	public void testDateToString() {
		assertNotNull(XmlDomUtil.dateToString(new Date()));
	}

	@Test
	public void testStringToDate() {
		assertNotNull(XmlDomUtil.stringToDate("2012-03-16T00:00:00.000-0500"));
		assertThrows(IllegalArgumentException.class, () -> XmlDomUtil.stringToDate(""));
	}

	@Test
	public void testXmlToString() {
		String xml = "<Parent xmlns='https://www.example.org/Example'><Child1/><Child2/></Parent>";

		ByteArrayInputStream targetStream = new ByteArrayInputStream(xml.getBytes());
		assertNotNull(XmlDomUtil.xmlToString(targetStream));
	}
}
