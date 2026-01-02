/*
 * Copyright © 2026 Indiana University
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;
import org.opensaml.core.xml.schema.XSString;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Attribute;
import org.opensaml.saml.saml2.core.AttributeStatement;
import org.opensaml.saml.saml2.core.AuthnStatement;
import org.opensaml.saml.saml2.core.Conditions;
import org.w3c.dom.Element;

import edu.iu.IdGenerator;
import edu.iu.client.IuJsonAdapter;
import edu.iu.test.IuTestLogger;
import iu.auth.session.SessionAdapterFactory;

@SuppressWarnings("javadoc")
public class SamlPostAuthenticationIT {

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testAssertionSerialization() {
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

		final Queue<StoredSamlAssertion> assertions = new ArrayDeque<>();
		try (final var mockXmlDomUtil = mockStatic(XmlDomUtil.class)) {
			final var content = IdGenerator.generateId();
			mockXmlDomUtil.when(() -> XmlDomUtil.getContent(dom)).thenReturn(content);
			IuTestLogger.expect(SamlAssertion.class.getName(), Level.FINE, "SAML2 assertion " + content);

			assertions.add(new SamlAssertion(assertion));
		}

		final var realm = IdGenerator.generateId();
		final var principalName = IdGenerator.generateId();
		final var postAuth = mock(SamlPostAuthentication.class);
		when(postAuth.getName()).thenReturn(principalName);
		when(postAuth.getAuthTime()).thenReturn(iat);
		when(postAuth.getExpires()).thenReturn(exp);
		when(postAuth.getAssertions()).thenReturn(assertions);
		when(postAuth.getRealm()).thenReturn(realm);

		final var adapterFactory = new SessionAdapterFactory<>(SamlPostAuthentication.class);
		final var adapter = ((IuJsonAdapter) adapterFactory.apply(SamlPostAuthentication.class));
		final var json = adapter.toJson(postAuth);
		final var postAuthFromJson = (SamlPostAuthentication) adapter.fromJson(json);
		assertEquals(principalName, postAuthFromJson.getName());
		assertEquals(iat, postAuthFromJson.getAuthTime());
		assertEquals(exp, postAuthFromJson.getExpires());
		assertEquals(realm, postAuthFromJson.getRealm());
		final var assertionFromJson = postAuthFromJson.getAssertions().iterator().next();
		assertEquals(nbf, assertionFromJson.getNotBefore());
		assertEquals(exp, assertionFromJson.getNotOnOrAfter());
		assertEquals(iat, assertionFromJson.getAuthnInstant());
		assertEquals(value, assertionFromJson.getAttributes().get(name));
		assertEquals(value, assertionFromJson.getAttributes().get(friendlyName));
		
	}

}
