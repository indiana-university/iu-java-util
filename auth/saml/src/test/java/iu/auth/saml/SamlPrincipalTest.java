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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Queue;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuIterable;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;

@SuppressWarnings("javadoc")
public class SamlPrincipalTest {

	/*
	 * @Test public void testEquals() { final List<SamlPrincipal> principals = new
	 * ArrayList<>(); final var uri_1 = URI.create("test://ldp/" +
	 * IdGenerator.generateId());
	 * 
	 * final var names = List.of("foo", "bar"); final var realms =
	 * List.of(IdGenerator.generateId(), IdGenerator.generateId()); for (final var
	 * realm : realms) {
	 * 
	 * for (final var name : names) { final Map<String, Object> claims = new
	 * LinkedHashMap<>();
	 * 
	 * principals.add(new SamlPrincipal(name, name, name + "@iu.edu",
	 * uri_1.toString(), realm, claims)); } }
	 * 
	 * for (var i = 0; i < principals.size(); i++) for (var j = 0; j <
	 * principals.size(); j++) { final var pi = principals.get(i); final var pj =
	 * principals.get(j); if (i == j) { assertNotEquals(pi, new Object());
	 * assertEquals(pi, pj); assertEquals(pi.hashCode(), pj.hashCode()); } else {
	 * assertNotEquals(pi, pj); assertNotEquals(pj, pi);
	 * assertNotEquals(pi.hashCode(), pj.hashCode()); } } }
	 */

	@Test
	public void testPrincipal() {
		final Queue<SamlAssertion> samlAssertions = new ArrayDeque<>();
		final var entityId = "test://ldp/";
		final var realm = IdGenerator.generateId();
		final var principalName = "foo";
		final var issueInstant = Instant.now();
		final var authnInstant = Instant.now();
		final var expires = authnInstant.plus(Duration.ofHours(12L));
		final var assertions = IuIterable.stream(samlAssertions).toArray(SamlAssertion[]::new);

		final var builder = IuJson.object() //
				.add("iss", entityId) //
				.add("aud", realm) //
				.add("sub", principalName) //
				.add("iat", issueInstant.getEpochSecond()) //
				.add("exp", expires.getEpochSecond()) //
				.add("auth_time", authnInstant.getEpochSecond()); //
		IuJson.add(builder, "urn:oasis:names:tc:SAML:2.0:assertion", () -> assertions,
				IuJsonAdapter.of(SamlAssertion[].class, SamlAssertion.JSON));

		SamlPrincipal samlPrincipal = new SamlPrincipal(realm, entityId, principalName, issueInstant, authnInstant,
				expires, samlAssertions);

		assertEquals(builder.build().toString(), samlPrincipal.toString());

		assertEquals(principalName, samlPrincipal.getName());
		final var subject = samlPrincipal.getSubject();
		assertSame(samlPrincipal, subject.getPrincipals().iterator().next());
		assertEquals(1, subject.getPrincipals().size());
		assertTrue(subject.getPrivateCredentials().isEmpty());
		assertTrue(subject.getPublicCredentials().isEmpty());

	}

}
