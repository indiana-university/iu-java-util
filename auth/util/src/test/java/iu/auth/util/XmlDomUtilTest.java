package iu.auth.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

@SuppressWarnings("javadoc")
public class XmlDomUtilTest {
	
	@Test
	public void testParse() {
		Document doc = XmlDomUtil.parse("<foo><bar>baz</bar></foo>");
		assertEquals(1, doc.getChildNodes().getLength());
		assertEquals("foo", doc.getChildNodes().item(0).getNodeName());
	}

	@Test
	public void testAttribute() {
		Document doc = XmlDomUtil.parse("<foo bar='bam'><bar>baz</bar></foo>");
		assertEquals("bam",
				XmlDomUtil.getAttribute(doc.getDocumentElement(), "bar"));
	}

	@Test
	public void testChildElement() {
		Document doc = XmlDomUtil.parse("<foo><bar>baz</bar></foo>");
		assertEquals("baz", XmlDomUtil.findElement(doc, "bar").getTextContent());
	}

	@Test
	public void testChildNode() {
		Document doc = XmlDomUtil.parse("<foo bar='bam'><bar>baz</bar></foo>");
		assertEquals("baz",
				XmlDomUtil.getChildNode(doc.getDocumentElement(), "bar")
						.getTextContent());
	}
}
