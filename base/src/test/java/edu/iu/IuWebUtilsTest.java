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
package edu.iu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuWebUtilsTest {

	@Test
	public void testIsRoot() throws URISyntaxException {
		assertTrue(IuWebUtils.isRootOf(new URI("foo:bar"), new URI("foo:bar")));
		assertFalse(IuWebUtils.isRootOf(new URI("foo:bar"), new URI("foo:baz")));
		assertFalse(IuWebUtils.isRootOf(new URI("foo:/bar"), new URI("foo:/baz")));
		assertFalse(IuWebUtils.isRootOf(new URI("foo:/bar"), new URI("/baz")));
		assertFalse(IuWebUtils.isRootOf(new URI("bar:/foo"), new URI("foo:/bar")));
		assertFalse(IuWebUtils.isRootOf(new URI("foo://bar/baz"), new URI("foo://baz/bar")));
		assertTrue(IuWebUtils.isRootOf(new URI("foo://bar/baz"), new URI("foo://bar/baz/foo")));
		assertTrue(IuWebUtils.isRootOf(new URI("foo://bar/baz/"), new URI("foo://bar/baz/foo")));
		assertFalse(IuWebUtils.isRootOf(new URI("foo://bar/baz"), new URI("foo://bar/bazfoo")));
		assertTrue(IuWebUtils.isRootOf(new URI("foo://bar"), new URI("foo://bar/baz")));
	}

	@Test
	public void testEmptyString() {
		assertTrue(assertQueryString("?").isEmpty());
	}

	@Test
	public void testSingleParamQueryString() {
		final var parsed = assertQueryString("foo=bar");
		assertEquals("bar", parsed.get("foo").iterator().next());
	}

	@Test
	public void testSingleParamWithNoValue() {
		final var parsed = IuWebUtils.parseQueryString("foo");
		assertEquals("", parsed.get("foo").iterator().next());
		assertEquals("foo=", IuWebUtils.createQueryString(parsed));
	}

	@Test
	public void testParamWithNoValueThenAnotherWith() {
		final var parsed = IuWebUtils.parseQueryString("foo&bar=baz&foo&baz=bar&foo");
		assertEquals("", parsed.get("foo").iterator().next());
		assertEquals("baz", parsed.get("bar").iterator().next());
		assertEquals("bar", parsed.get("baz").iterator().next());
		assertEquals("foo=&foo=&foo=&bar=baz&baz=bar", IuWebUtils.createQueryString(parsed));
	}

	@Test
	public void testComplexQueryString() {
		final var parsed = assertQueryString("foo=bar&bar=baz&bar=foo");
		assertEquals("bar", parsed.get("foo").iterator().next());
		final var bar = parsed.get("bar").iterator();
		assertEquals("baz", bar.next());
		assertEquals("foo", bar.next());
	}

	@Test
	public void testHeaderWithNoNamedElements() {
		final var parsed = IuWebUtils.parseHeader("a b c");
		assertEquals("a b c", parsed.get(""));
	}

	@Test
	public void testHeaderWithNamedElements() {
		final var parsed = IuWebUtils.parseHeader("a; b; c=d; e");
		assertEquals("a", parsed.get(""));
		assertEquals("", parsed.get("b"));
		assertEquals("d", parsed.get("c"));
		assertEquals("", parsed.get("e"));
	}

	@Test
	public void testNormalizeHeaderName() {
		assertEquals("Content-Type", IuWebUtils.normalizeHeaderName("cONtENt-tYPe"));
		assertThrows(IllegalArgumentException.class, () -> IuWebUtils.normalizeHeaderName("!@#$"));
	}

	@Test
	public void testDescribeStatus() {
		assertEquals("100 CONTINUE", IuWebUtils.describeStatus(100));
		assertEquals("101 SWITCHING PROTOCOLS", IuWebUtils.describeStatus(101));
		assertEquals("200 OK", IuWebUtils.describeStatus(200));
		assertEquals("201 CREATED", IuWebUtils.describeStatus(201));
		assertEquals("202 ACCEPTED", IuWebUtils.describeStatus(202));
		assertEquals("203 NON AUTHORITATIVE INFORMATION", IuWebUtils.describeStatus(203));
		assertEquals("204 NO CONTENT", IuWebUtils.describeStatus(204));
		assertEquals("205 RESET CONTENT", IuWebUtils.describeStatus(205));
		assertEquals("206 PARTIAL CONTENT", IuWebUtils.describeStatus(206));
		assertEquals("300 MULTIPLE CHOICES", IuWebUtils.describeStatus(300));
		assertEquals("301 MOVED PERMANENTLY", IuWebUtils.describeStatus(301));
		assertEquals("302 FOUND", IuWebUtils.describeStatus(302));
		assertEquals("303 SEE OTHER", IuWebUtils.describeStatus(303));
		assertEquals("304 NOT MODIFIED", IuWebUtils.describeStatus(304));
		assertEquals("305 USE PROXY", IuWebUtils.describeStatus(305));
		assertEquals("307 TEMPORARY REDIRECT", IuWebUtils.describeStatus(307));
		assertEquals("400 BAD REQUEST", IuWebUtils.describeStatus(400));
		assertEquals("401 UNAUTHORIZED", IuWebUtils.describeStatus(401));
		assertEquals("402 PAYMENT REQUIRED", IuWebUtils.describeStatus(402));
		assertEquals("403 FORBIDDEN", IuWebUtils.describeStatus(403));
		assertEquals("404 NOT FOUND", IuWebUtils.describeStatus(404));
		assertEquals("405 METHOD NOT ALLOWED", IuWebUtils.describeStatus(405));
		assertEquals("406 NOT ACCEPTABLE", IuWebUtils.describeStatus(406));
		assertEquals("407 PROXY AUTHENTICATION REQUIRED", IuWebUtils.describeStatus(407));
		assertEquals("408 REQUEST TIMEOUT", IuWebUtils.describeStatus(408));
		assertEquals("409 CONFLICT", IuWebUtils.describeStatus(409));
		assertEquals("410 GONE", IuWebUtils.describeStatus(410));
		assertEquals("411 LENGTH REQUIRED", IuWebUtils.describeStatus(411));
		assertEquals("412 PRECONDITION FAILED", IuWebUtils.describeStatus(412));
		assertEquals("413 REQUEST ENTITY TOO LARGE", IuWebUtils.describeStatus(413));
		assertEquals("414 REQUEST URI TOO LONG", IuWebUtils.describeStatus(414));
		assertEquals("415 UNSUPPORTED MEDIA TYPE", IuWebUtils.describeStatus(415));
		assertEquals("416 REQUESTED RANGE NOT SATISFIABLE", IuWebUtils.describeStatus(416));
		assertEquals("417 EXPECTATION FAILED", IuWebUtils.describeStatus(417));
		assertEquals("500 INTERNAL SERVER ERROR", IuWebUtils.describeStatus(500));
		assertEquals("501 NOT IMPLEMENTED", IuWebUtils.describeStatus(501));
		assertEquals("502 BAD GATEWAY", IuWebUtils.describeStatus(502));
		assertEquals("503 SERVICE UNAVAILABLE", IuWebUtils.describeStatus(503));
		assertEquals("504 GATEWAY TIMEOUT", IuWebUtils.describeStatus(504));
		assertEquals("505 HTTP VERSION NOT SUPPORTED", IuWebUtils.describeStatus(505));
		assertEquals("34 UNKNOWN", IuWebUtils.describeStatus(34));
	}

	@Test
	public void testInetAddress() {
		try (final var mockInetAddress = mockStatic(InetAddress.class)) {
			final var a = mock(InetAddress.class);
			mockInetAddress.when(() -> InetAddress.getByName("foo")).thenReturn(a);
			assertSame(a, IuWebUtils.getInetAddress("foo"));
			assertSame(a, IuWebUtils.getInetAddress("foo"));
			mockInetAddress.verify(() -> InetAddress.getByName("foo"));
		}
	}

	@Test
	public void testCidrRange() throws Throwable {
		assertTrue(IuWebUtils.isInetAddressInRange(IuWebUtils.getInetAddress("10.0.1.255"), "10.0.0.0/23"));
		assertTrue(IuWebUtils.isInetAddressInRange(IuWebUtils.getInetAddress("10.0.0.0"), "10.0.0.0/23"));
		assertFalse(IuWebUtils.isInetAddressInRange(IuWebUtils.getInetAddress("10.0.2.10"), "10.0.0.0/23"));
		assertTrue(IuWebUtils.isInetAddressInRange(IuWebUtils.getInetAddress("10.0.0.0"), "10.0.0.0"));
		assertTrue(IuWebUtils.isInetAddressInRange(IuWebUtils.getInetAddress("10.0.128.64"), "10.0.0.0/16"));
		assertFalse(IuWebUtils.isInetAddressInRange(IuWebUtils.getInetAddress("10.1.128.64"), "10.0.0.0/16"));
	}

	@Test
	public void testVchar() {
		assertFalse(IuWebUtils.vchar(' '));
		assertTrue(IuWebUtils.vchar('!'));
		assertTrue(IuWebUtils.vchar('~'));
		assertFalse(IuWebUtils.vchar((char) 127));
	}

	@Test
	public void testAlpha() {
		assertFalse(IuWebUtils.alpha('@'));
		assertTrue(IuWebUtils.alpha('A'));
		assertTrue(IuWebUtils.alpha('Z'));
		assertFalse(IuWebUtils.alpha('['));
		assertFalse(IuWebUtils.alpha('`'));
		assertTrue(IuWebUtils.alpha('a'));
		assertTrue(IuWebUtils.alpha('z'));
		assertFalse(IuWebUtils.alpha('{'));
	}

	@Test
	public void testDigit() {
		assertFalse(IuWebUtils.digit('/'));
		assertTrue(IuWebUtils.digit('0'));
		assertTrue(IuWebUtils.digit('9'));
		assertFalse(IuWebUtils.digit(':'));
	}

	@Test
	public void testObsText() {
		assertFalse(IuWebUtils.obsText((char) 0x7f));
		assertTrue(IuWebUtils.obsText((char) 0x80));
		assertTrue(IuWebUtils.obsText((char) 0xff));
		assertFalse(IuWebUtils.obsText((char) 0x100));
	}

	@Test
	public void testToken68() {
		assertEquals(0, IuWebUtils.token68("", 0));
		assertEquals(6, IuWebUtils.token68("foobar", 0));
		assertEquals(6, IuWebUtils.token68("f00b~r", 0));
		assertEquals(4, IuWebUtils.token68("f00b@r", 0));
		assertEquals(5, IuWebUtils.token68("f00b=r", 0));
		assertEquals(8, IuWebUtils.token68("f00b~r==", 0));
	}

	@Test
	public void testTchar() {
		assertTrue(IuWebUtils.tchar('A'));
		assertTrue(IuWebUtils.tchar('0'));
		assertTrue(IuWebUtils.tchar('-'));
		assertFalse(IuWebUtils.tchar('='));
	}

	@Test
	public void testToken() {
		assertEquals(0, IuWebUtils.token("", 0));
		assertEquals(6, IuWebUtils.token("foobar", 0));
		assertEquals(6, IuWebUtils.token("f00b~r", 0));
		assertEquals(4, IuWebUtils.token("f00b@r", 0));
		assertEquals(4, IuWebUtils.token("f00b=r", 0));
		assertEquals(5, IuWebUtils.token("f00ba=", 0));
		assertEquals(6, IuWebUtils.token("f00b~r==", 0));
	}

	@Test
	public void testBws() {
		assertEquals(0, IuWebUtils.bws("", 0));
		assertEquals(2, IuWebUtils.bws("  ", 0));
		assertEquals(1, IuWebUtils.bws("\t", 0));
		assertEquals(1, IuWebUtils.bws("\ta", 0));
		assertEquals(0, IuWebUtils.bws("foobar", 0));
		assertEquals(2, IuWebUtils.bws("  foobar", 0));
		assertEquals(1, IuWebUtils.bws("\tfoobar", 0));
	}

	@Test
	public void testSp() {
		assertEquals(0, IuWebUtils.sp("", 0));
		assertEquals(2, IuWebUtils.sp("  ", 0));
		assertEquals(1, IuWebUtils.sp(" ", 0));
		assertEquals(1, IuWebUtils.sp(" a", 0));
		assertEquals(0, IuWebUtils.sp("foobar", 0));
		assertEquals(2, IuWebUtils.sp("  foobar", 0));
		assertEquals(1, IuWebUtils.sp(" foobar", 0));
	}

	@Test
	public void testQdtext() {
		assertTrue(IuWebUtils.qdtext('\t'));
		assertTrue(IuWebUtils.qdtext(' '));
		assertTrue(IuWebUtils.qdtext('!'));
		assertFalse(IuWebUtils.qdtext('\"'));
		assertTrue(IuWebUtils.qdtext('#'));
		assertTrue(IuWebUtils.qdtext('['));
		assertFalse(IuWebUtils.qdtext('\\'));
		assertFalse(IuWebUtils.qdtext('\"'));
		assertTrue(IuWebUtils.qdtext(']'));
		assertTrue(IuWebUtils.qdtext('~'));
		assertFalse(IuWebUtils.qdtext((char) 0x7f));
		assertTrue(IuWebUtils.qdtext('\200'));
	}

	@Test
	public void testQuotedPair() {
		assertEquals(0, IuWebUtils.quotedPair("", 0));
		assertEquals(0, IuWebUtils.quotedPair("\\", 0));
		assertEquals(0, IuWebUtils.quotedPair("ab", 0));
		assertEquals(0, IuWebUtils.quotedPair("\\\f", 0));
		assertEquals(2, IuWebUtils.quotedPair("\\\t", 0));
		assertEquals(2, IuWebUtils.quotedPair("\\ ", 0));
		assertEquals(2, IuWebUtils.quotedPair("\\\"", 0));
		assertEquals(2, IuWebUtils.quotedPair("\\\200", 0));
	}

	@Test
	public void testQuotedString() {
		assertEquals(0, IuWebUtils.quotedString("", 0));
		assertEquals(0, IuWebUtils.quotedString("foobar", 0));
		assertEquals(0, IuWebUtils.quotedString("\"foobar", 0));
		assertEquals(0, IuWebUtils.quotedString("\"foobar\\", 0));
		assertEquals(0, IuWebUtils.quotedString("\"foobar\\\"", 0));
		assertEquals(8, IuWebUtils.quotedString("\"foobar\"", 0));
		assertEquals(10, IuWebUtils.quotedString("\"foo\\\"bar\"", 0));
	}

	@Test
	public void testCreateChallenge() {
		final var realm = IdGenerator.generateId();
		assertThrows(IllegalArgumentException.class, () -> IuWebUtils.createChallenge("Basic", Map.of()));
		assertThrows(IllegalArgumentException.class,
				() -> IuWebUtils.createChallenge("Basic\0", Map.of("realm", realm)));
		assertThrows(IllegalArgumentException.class,
				() -> IuWebUtils.createChallenge("Basic", Map.of("realm", realm + "\f")));
		assertEquals("Basic realm=\"" + realm + " \t\"",
				IuWebUtils.createChallenge("Basic", Map.of("realm", realm + " \t")));

		final Map<String, String> params = new LinkedHashMap<>();
		params.put("realm", realm);
		assertEquals("Basic realm=\"" + realm + "\"", IuWebUtils.createChallenge("Basic", params));

		params.put("foo/bar", "");
		assertEquals("Basic realm=\"" + realm + "\" foo/bar", IuWebUtils.createChallenge("Basic", params));
		params.put("foo/bar", " ");
		assertThrows(IllegalArgumentException.class, () -> IuWebUtils.createChallenge("Basic", params));
		params.remove("foo/bar");

		params.put("foo\fbar", "");
		assertThrows(IllegalArgumentException.class, () -> IuWebUtils.createChallenge("Basic", params));
		params.remove("foo\fbar");

		params.put("foobar", "");
		assertEquals("Basic realm=\"" + realm + "\" foobar", IuWebUtils.createChallenge("Basic", params));
	}

	@Test
	public void testParseChallenge() {
		final var realm = IdGenerator.generateId();
		var wwwAuth = IuWebUtils.parseAuthenticateHeader("Basic realm=\"" + realm + "\"");
		var challenge = wwwAuth.next();
		assertEquals("Basic", challenge.getAuthScheme());
		assertEquals(realm, challenge.getRealm());
		assertTrue(challenge.getParameters().isEmpty());
		assertFalse(wwwAuth.hasNext());

		final var realm2 = IdGenerator.generateId();
		wwwAuth = IuWebUtils.parseAuthenticateHeader(
				"Basic realm=\"" + realm + "\"\t, Bearer realm=\"" + realm2 + "\" scope=\"foobar\"");
		challenge = wwwAuth.next();
		assertEquals("Basic", challenge.getAuthScheme());
		assertEquals(realm, challenge.getRealm());
		assertTrue(challenge.getParameters().isEmpty());
		assertTrue(wwwAuth.hasNext());
		challenge = wwwAuth.next();
		assertEquals("Bearer", challenge.getAuthScheme());
		assertEquals(realm2, challenge.getRealm());
		assertEquals(Map.of("scope", "foobar"), challenge.getParameters());
		assertFalse(wwwAuth.hasNext());

		wwwAuth = IuWebUtils.parseAuthenticateHeader("Basic realm=\"" + realm + "\" foo/bar");
		challenge = wwwAuth.next();
		assertEquals("Basic", challenge.getAuthScheme());
		assertEquals(realm, challenge.getRealm());
		assertEquals(Map.of("foo/bar", ""), challenge.getParameters());
		assertFalse(wwwAuth.hasNext());

		wwwAuth = IuWebUtils.parseAuthenticateHeader("Basic realm=" + realm);
		challenge = wwwAuth.next();
		assertEquals("Basic", challenge.getAuthScheme());
		assertEquals(realm, challenge.getRealm());
		assertTrue(challenge.getParameters().isEmpty());
		assertFalse(wwwAuth.hasNext());

		wwwAuth = IuWebUtils.parseAuthenticateHeader("Basic");
		challenge = wwwAuth.next();
		assertEquals("Basic", challenge.getAuthScheme());
		assertNull(challenge.getRealm());
		assertTrue(challenge.getParameters().isEmpty());
		assertFalse(wwwAuth.hasNext());

		wwwAuth = IuWebUtils.parseAuthenticateHeader("Basic realm");
		challenge = wwwAuth.next();
		assertEquals("Basic", challenge.getAuthScheme());
		assertEquals("", challenge.getRealm());
		assertTrue(challenge.getParameters().isEmpty());
		assertFalse(wwwAuth.hasNext());

		assertThrows(IllegalArgumentException.class, () -> IuWebUtils.parseAuthenticateHeader("/").next());
		assertThrows(IllegalArgumentException.class, () -> IuWebUtils.parseAuthenticateHeader("Basic realm ").next());
		assertEquals("expected quoted-string at 12", assertThrows(IllegalArgumentException.class,
				() -> IuWebUtils.parseAuthenticateHeader("Basic realm=\"foobar").next()).getMessage());
		assertEquals("invalid auth-param at 47", assertThrows(IllegalArgumentException.class,
				() -> IuWebUtils.parseAuthenticateHeader("Basic realm=\"" + realm + "\" \f").next()).getMessage());
	}

	private Map<String, ? extends Iterable<String>> assertQueryString(String qs) {
		final var parsed = IuWebUtils.parseQueryString(qs);
		if (qs.startsWith("?"))
			qs = qs.substring(1);
		assertEquals(qs, IuWebUtils.createQueryString(parsed));
		return parsed;
	}

}
