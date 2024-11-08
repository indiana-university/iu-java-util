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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;
import org.opensaml.core.xml.schema.XSAny;
import org.opensaml.core.xml.schema.XSString;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Attribute;
import org.opensaml.saml.saml2.core.AttributeStatement;
import org.opensaml.saml.saml2.core.AuthnStatement;
import org.opensaml.saml.saml2.core.Conditions;
import org.w3c.dom.Element;

import edu.iu.IdGenerator;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class SamlAssertionTest {

	@Test
	public void testReadString() {
		final var value = IdGenerator.generateId();
		final var attribute = mock(Attribute.class);
		final var xsstring = mock(XSString.class);
		when(xsstring.getValue()).thenReturn(value);
		when(attribute.getAttributeValues()).thenReturn(List.of(xsstring));
		assertEquals(value, SamlAssertion.readStringAttribute(attribute));
	}

	@Test
	public void testReadAny() {
		final var value = IdGenerator.generateId();
		final var attribute = mock(Attribute.class);
		final var xsany = mock(XSAny.class);
		when(xsany.getTextContent()).thenReturn(value);
		when(attribute.getAttributeValues()).thenReturn(List.of(xsany));
		assertEquals(value, SamlAssertion.readStringAttribute(attribute));
	}

	@Test
	public void testConstructor() {
		final var attribute = mock(Attribute.class);
		final var name = IdGenerator.generateId();
		final var friendlyName = IdGenerator.generateId();
		final var value = IdGenerator.generateId();
		final var xsstring = mock(XSString.class);
		when(xsstring.getValue()).thenReturn(value);
		when(attribute.getName()).thenReturn(name);
		when(attribute.getFriendlyName()).thenReturn(friendlyName);
		when(attribute.getAttributeValues()).thenReturn(List.of(xsstring));

		final var attributeStatement = mock(AttributeStatement.class);
		when(attributeStatement.getAttributes()).thenReturn(List.of(attribute));

		final var assertion = mock(Assertion.class);
		when(assertion.getAttributeStatements()).thenReturn(List.of(attributeStatement));

		final var iat = Instant.now();
		final var nbf = iat.minusSeconds(5L);
		final var exp = iat.plusSeconds(15L);
		final var conditions = mock(Conditions.class);
		when(conditions.getNotBefore()).thenReturn(nbf);
		when(conditions.getNotOnOrAfter()).thenReturn(exp);
		when(assertion.getConditions()).thenReturn(conditions);

		final var authnStatement = mock(AuthnStatement.class);
		when(authnStatement.getAuthnInstant()).thenReturn(iat);
		when(assertion.getAuthnStatements()).thenReturn(List.of(authnStatement));

		final var dom = mock(Element.class);
		when(assertion.getDOM()).thenReturn(dom);
		try (final var mockXmlDomUtil = mockStatic(XmlDomUtil.class)) {
			final var content = IdGenerator.generateId();
			mockXmlDomUtil.when(() -> XmlDomUtil.getContent(dom)).thenReturn(content);
			IuTestLogger.expect(SamlAssertion.class.getName(), Level.FINE, "SAML2 assertion " + content);

			final var samlAssertion = new SamlAssertion(assertion);
			assertEquals(value, samlAssertion.getAttributes().get(name));
			assertEquals(value, samlAssertion.getAttributes().get(friendlyName));
			assertEquals(iat, samlAssertion.getAuthnInstant());
			assertEquals(nbf, samlAssertion.getNotBefore());
			assertEquals(exp, samlAssertion.getNotOnOrAfter());
		}

	}

	@Test
	public void testConstructorFromEmpty() {
		final var assertion = mock(Assertion.class);
		IuTestLogger.expect(SamlAssertion.class.getName(), Level.FINE, "SAML2 assertion ");
		final var samlAssertion = assertDoesNotThrow(() -> new SamlAssertion(assertion));
		assertTrue(samlAssertion.getAttributes().isEmpty());
		assertNull(samlAssertion.getAuthnInstant());
		assertNull(samlAssertion.getNotBefore());
		assertNull(samlAssertion.getNotOnOrAfter());
	}

}
