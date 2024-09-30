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
package iu.auth.session;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;

@SuppressWarnings("javadoc")
public class SessionTest {

	private Session session;
	private URI resourceUri;
	private Duration expires;

	interface SessionDetailInterface {
	}

	@BeforeEach
	void setUp() {
		resourceUri = URI.create(IdGenerator.generateId());
		expires = Duration.ofHours(1);
		session = new Session(resourceUri, expires);
	}

	@Test
	void testGetDetail() {
		final var detail = session.getDetail(SessionDetailInterface.class);
		assertNotNull(detail);
		assertTrue(Proxy.isProxyClass(detail.getClass()));
		assertInstanceOf(SessionDetail.class, Proxy.getInvocationHandler(detail));
	}

//	@Test
//	void testDetailKeyExistsInSession() {
//		session = new Session(resourceUri, expires);
//
//		Map<String, Map<String, Object>> details = new HashMap<>();
//		Map<String, Object> detailMap = new HashMap<>();
//
//		detailMap.put("givenName", "John");
//		detailMap.put("notThere", false);
//		details.put("SessionDetailInterface", detailMap);
//
////		final var builder = IuJson.object() //
////				.add("iat", 1625140800) //
////				.add("exp", 1625227200);//
////		IuJson.add(builder, "attributes", () -> details, IuJsonAdapter.basic());
////
//		Object detail = session.getDetail(SessionDetailInterface.class);
//		assertNotNull(detail);
//		assertEquals(SessionDetailInterface.class, detail.getClass().getInterfaces()[0]);
//	}

	@Test
	void testChangeFlagManipulation() {
		assertFalse(session.isChanged());
		session.setChanged(true);
		assertTrue(session.isChanged());
	}

	@Test
	void testGetExpires() {
		Instant expirationTime = session.getExpires();
		assertNotNull(expirationTime);
		assertTrue(expirationTime.isAfter(Instant.now()));
	}

//	@Test
//	void testGetIssueAt() {
//		Instant creationTime = session.getIssueAt();
//		assertNotNull(creationTime);
//		assertTrue(creationTime.isBefore(session.expires));
//	}

	@Test
	void testTokenizeWithValidParameters() {
		final var secretKey = new byte[32];
		final var issuerKey = WebKey.ephemeral(Algorithm.HS256);
		final var algorithm = WebKey.Algorithm.HS256;
		String token = session.tokenize(secretKey, issuerKey, algorithm);
		assertNotNull(token);
	}

//	@Test
//	void testCreateSessionFromValidJsonValue() {
//		final var builder = IuJson.object() //
//				.add("iat", 1625140800) //
//				.add("exp", 1625227200) //
//				.add("attributes", IuJson.object().build()).build();
//		session = new Session(builder);
//		assertNotNull(session);
//		assertEquals(Instant.ofEpochSecond(1625140800), session.getIssueAt());
//		assertEquals(Instant.ofEpochSecond(1625227200), session.getExpires());
//		assertTrue(session.details.isEmpty());
//	}
}
