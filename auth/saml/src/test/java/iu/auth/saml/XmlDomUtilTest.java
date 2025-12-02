/*
 * Copyright © 2025 Indiana University
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;

import java.io.ByteArrayInputStream;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;

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
