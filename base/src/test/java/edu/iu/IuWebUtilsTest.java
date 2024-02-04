package edu.iu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.net.InetAddress;
import java.util.Map;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuWebUtilsTest {

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

	private Map<String, ? extends Iterable<String>> assertQueryString(String qs) {
		final var parsed = IuWebUtils.parseQueryString(qs);
		if (qs.startsWith("?"))
			qs = qs.substring(1);
		assertEquals(qs, IuWebUtils.createQueryString(parsed));
		return parsed;
	}

}
