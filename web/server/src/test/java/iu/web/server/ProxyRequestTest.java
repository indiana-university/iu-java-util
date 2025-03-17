package iu.web.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuWebUtils;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class ProxyRequestTest {

	@Test
	public void testRemoteAllowedNoProxy() {
		final var uri = URI.create(IdGenerator.generateId());
		final var remoteAddr = IdGenerator.generateId();
		final var proxyRequest = new ProxyRequest(false, Set.of(), uri,
				InetSocketAddress.createUnresolved(remoteAddr, 0));
		assertEquals(uri, proxyRequest.requestUri());
		assertEquals(remoteAddr, proxyRequest.remoteAddress().getHostName());

		IuTestLogger.expect(ProxyRequest.class.getName(), Level.FINE,
				"remote client " + remoteAddr + "/<unresolved>:0 not in allowed proxy set");
		assertFalse(proxyRequest.isRemoteAllowed());
		assertFalse(proxyRequest.isRemoteAllowed());
	}

	@Test
	public void testRemoteAllowedProxyNotMatch() {
		final var uri = URI.create(IdGenerator.generateId());
		final var remoteAddr = IdGenerator.generateId();
		final var allowed = IdGenerator.generateId();
		final var proxyRequest = new ProxyRequest(true, Set.of(allowed), uri,
				InetSocketAddress.createUnresolved(remoteAddr, 0));
		assertEquals(uri, proxyRequest.requestUri());
		assertEquals(remoteAddr, proxyRequest.remoteAddress().getHostName());

		IuTestLogger.expect(ProxyRequest.class.getName(), Level.INFO,
				"remote client " + remoteAddr + "/<unresolved>:0 not in allowed proxy set");
		assertFalse(proxyRequest.isRemoteAllowed());
		assertFalse(proxyRequest.isRemoteAllowed());
	}

	@Test
	public void testRemoteAllowedProxyMatch() {
		final var uri = URI.create(IdGenerator.generateId());
		final var remoteAddr = IdGenerator.generateId();
		final var proxyRequest = new ProxyRequest(true, Set.of(remoteAddr), uri,
				InetSocketAddress.createUnresolved(remoteAddr, 0));
		assertEquals(uri, proxyRequest.requestUri());
		assertEquals(remoteAddr, proxyRequest.remoteAddress().getHostName());

		assertTrue(proxyRequest.isRemoteAllowed());
		assertTrue(proxyRequest.isRemoteAllowed());
	}

	@Test
	public void testSetRemoteAddressIgnoreNull() {
		final var uri = URI.create(IdGenerator.generateId());
		final var remoteAddr = IdGenerator.generateId();
		final var proxyRequest = new ProxyRequest(true, Set.of(remoteAddr), uri,
				InetSocketAddress.createUnresolved(remoteAddr, 0));
		proxyRequest.setRemoteAddress((String) null);
		assertEquals(remoteAddr, proxyRequest.remoteAddress().getHostString());
	}

	@Test
	public void testSetRemoteAddressParseToNullUnexpected() {
		final var uri = URI.create(IdGenerator.generateId());
		final var proxyAddr = IdGenerator.generateId();
		final var proxyAddress = InetSocketAddress.createUnresolved(proxyAddr, 0);
		final var proxyRequest = new ProxyRequest(false, Set.of(proxyAddr), uri, proxyAddress);
		assertTrue(proxyRequest.isRemoteAllowed());

		final var remoteAddr = IdGenerator.generateId();
		try (final var mockIuWebUtils = mockStatic(IuWebUtils.class)) {
			IuTestLogger.expect(ProxyRequest.class.getName(), Level.FINE, "unknown proxy not allowed");
			proxyRequest.setRemoteAddress(remoteAddr);
		}
		assertEquals(proxyAddress, proxyRequest.remoteAddress());
		assertFalse(proxyRequest.isRemoteAllowed());
	}

	@Test
	public void testSetRemoteAddressParseToNull() {
		final var uri = URI.create(IdGenerator.generateId());
		final var proxyAddr = IdGenerator.generateId();
		final var proxyAddress = InetSocketAddress.createUnresolved(proxyAddr, 0);
		final var proxyRequest = new ProxyRequest(true, Set.of(proxyAddr), uri, proxyAddress);
		assertTrue(proxyRequest.isRemoteAllowed());

		final var remoteAddr = IdGenerator.generateId();
		try (final var mockIuWebUtils = mockStatic(IuWebUtils.class)) {
			IuTestLogger.expect(ProxyRequest.class.getName(), Level.INFO, "unknown proxy not allowed");
			proxyRequest.setRemoteAddress(remoteAddr);
			proxyRequest.setRemoteAddress(remoteAddr);
		}
		assertEquals(proxyAddress, proxyRequest.remoteAddress());
		assertFalse(proxyRequest.isRemoteAllowed());
	}

	@Test
	public void testSetRemoteAddressParseToValue() {
		final var uri = URI.create(IdGenerator.generateId());
		final var proxyAddr = IdGenerator.generateId();
		final var proxyRequest = new ProxyRequest(true, Set.of(proxyAddr), uri,
				InetSocketAddress.createUnresolved(proxyAddr, 0));
		assertTrue(proxyRequest.isRemoteAllowed());

		final var remoteAddr = IdGenerator.generateId();
		final var remoteAddress = InetSocketAddress.createUnresolved(remoteAddr, 0);
		try (final var mockIuWebUtils = mockStatic(IuWebUtils.class)) {
			mockIuWebUtils.when(() -> IuWebUtils.parseNodeIdentifier(remoteAddr)).thenReturn(remoteAddress);
			IuTestLogger.expect(ProxyRequest.class.getName(), Level.INFO,
					"replacing remote address " + proxyAddr + "/<unresolved>:0 with " + remoteAddress);
			proxyRequest.setRemoteAddress(remoteAddr);
		}
		assertEquals(remoteAddress, proxyRequest.remoteAddress());
		assertFalse(proxyRequest.isRemoteAllowed());
	}

	@Test
	public void testSetRemoteAddressParseToNextProxy() {
		final var uri = URI.create(IdGenerator.generateId());
		final var proxyAddr = IdGenerator.generateId();
		final var proxyAddress = InetSocketAddress.createUnresolved(proxyAddr, 0);
		final var nextAddr = IdGenerator.generateId();
		final var nextAddress = InetSocketAddress.createUnresolved(nextAddr, 0);
		final var remoteAddr = IdGenerator.generateId();
		final var remoteAddress = InetSocketAddress.createUnresolved(remoteAddr, 0);

		final var proxyRequest = new ProxyRequest(true, Set.of(proxyAddr, nextAddr), uri, proxyAddress);
		assertTrue(proxyRequest.isRemoteAllowed());
		try (final var mockIuWebUtils = mockStatic(IuWebUtils.class)) {
			mockIuWebUtils.when(() -> IuWebUtils.parseNodeIdentifier(nextAddr)).thenReturn(nextAddress);
			IuTestLogger.expect(ProxyRequest.class.getName(), Level.INFO,
					"replacing remote address " + proxyAddress + " with " + nextAddress);
			proxyRequest.setRemoteAddress(nextAddress);
			assertTrue(proxyRequest.isRemoteAllowed());

			mockIuWebUtils.when(() -> IuWebUtils.parseNodeIdentifier(remoteAddr)).thenReturn(remoteAddress);
			IuTestLogger.expect(ProxyRequest.class.getName(), Level.INFO,
					"replacing remote address " + nextAddress + " with " + remoteAddress);
			proxyRequest.setRemoteAddress(remoteAddr);
		}

		assertEquals(remoteAddress, proxyRequest.remoteAddress());
		assertFalse(proxyRequest.isRemoteAllowed());
	}

	@Test
	public void testSetRemoteAddressDeniedNoProxy() {
		final var uri = URI.create(IdGenerator.generateId());
		final var proxyAddr = IdGenerator.generateId();
		final var proxyAddress = InetSocketAddress.createUnresolved(proxyAddr, 0);
		final var remoteAddr = IdGenerator.generateId();
		final var remoteAddress = InetSocketAddress.createUnresolved(remoteAddr, 0);

		final var proxyRequest = new ProxyRequest(true, Set.of(), uri, proxyAddress);
		proxyRequest.setRemoteAddress(proxyAddress);
		IuTestLogger.expect(ProxyRequest.class.getName(), Level.INFO,
				"remote client " + proxyAddress + " not in allowed proxy set");
		proxyRequest.setRemoteAddress(remoteAddress);
		assertEquals(proxyAddress, proxyRequest.remoteAddress());
	}

	@Test
	public void testHandleXForwardedNullNoop() {
		final var host = IdGenerator.generateId();
		final var uri = URI.create("test://" + host);
		final var remoteAddr = IdGenerator.generateId();
		final var remoteAddress = InetSocketAddress.createUnresolved(remoteAddr, 0);
		final var proxyRequest = new ProxyRequest(true, Set.of(remoteAddr), uri, remoteAddress);
		proxyRequest.handleXForwardedHost(null, null);
		assertEquals(URI.create("test://" + host), proxyRequest.requestUri());
	}

	@Test
	public void testHandleXForwardedNotAllowed() {
		final var host = IdGenerator.generateId();
		final var uri = URI.create("test://" + host);
		final var name = IdGenerator.generateId();
		final var value = IdGenerator.generateId();
		final var remoteAddr = IdGenerator.generateId();
		final var remoteAddress = InetSocketAddress.createUnresolved(remoteAddr, 0);
		final var proxyRequest = new ProxyRequest(true, Set.of(), uri, remoteAddress);
		IuTestLogger.expect(ProxyRequest.class.getName(), Level.INFO,
				"remote client " + remoteAddress + " not in allowed proxy set");
		proxyRequest.handleXForwardedHost(name, List.of(value));
		assertEquals(URI.create("test://" + host), proxyRequest.requestUri());
	}

	@Test
	public void testHandleXForwarded() {
		final var host = IdGenerator.generateId();
		final var uri = URI.create("test://" + host);
		final var name = IdGenerator.generateId();
		final var value = IdGenerator.generateId();
		final var remoteAddr = IdGenerator.generateId();
		final var remoteAddress = InetSocketAddress.createUnresolved(remoteAddr, 0);
		final var proxyRequest = new ProxyRequest(true, Set.of(remoteAddr), uri, remoteAddress);
		proxyRequest.handleXForwardedHost(name, List.of(host));
		IuTestLogger.expect(ProxyRequest.class.getName(), Level.INFO,
				"replaced host authority in " + uri + " with test://" + value);
		proxyRequest.handleXForwardedHost(name, List.of(value));
		assertEquals(URI.create("test://" + value), proxyRequest.requestUri());
	}

	@Test
	public void testHandleXForwardedInvalid() {
		final var host = IdGenerator.generateId();
		final var uri = URI.create("test://" + host);
		final var name = IdGenerator.generateId();
		final var value = IdGenerator.generateId();
		final var remoteAddr = IdGenerator.generateId();
		final var remoteAddress = InetSocketAddress.createUnresolved(remoteAddr, 0);
		final var proxyRequest = new ProxyRequest(true, Set.of(remoteAddr), uri, remoteAddress);
		IuTestLogger.expect(ProxyRequest.class.getName(), Level.WARNING, "invalid input in Forwarded header",
				IllegalArgumentException.class,
				e -> ("additional " + name + " value seen, ignoring all headers").equals(e.getMessage()));
		proxyRequest.handleXForwardedHost(name, List.of(value, IdGenerator.generateId()));
		assertEquals(URI.create("test://" + host), proxyRequest.requestUri());
	}

	@Test
	public void testHandleXForwardedForNullNoop() {
		final var host = IdGenerator.generateId();
		final var uri = URI.create("test://" + host);
		final var remoteAddr = IdGenerator.generateId();
		final var remoteAddress = InetSocketAddress.createUnresolved(remoteAddr, 0);
		final var proxyRequest = new ProxyRequest(true, Set.of(remoteAddr), uri, remoteAddress);
		proxyRequest.handleXForwardedFor(null, null);
		assertEquals(remoteAddress, proxyRequest.remoteAddress());
	}

	@Test
	public void testHandleXForwardedForNotAllowed() {
		final var host = IdGenerator.generateId();
		final var uri = URI.create("test://" + host);
		final var name = IdGenerator.generateId();
		final var value = IdGenerator.generateId();
		final var remoteAddr = IdGenerator.generateId();
		final var remoteAddress = InetSocketAddress.createUnresolved(remoteAddr, 0);
		final var proxyRequest = new ProxyRequest(true, Set.of(), uri, remoteAddress);
		IuTestLogger.expect(ProxyRequest.class.getName(), Level.INFO,
				"remote client " + remoteAddress + " not in allowed proxy set");
		proxyRequest.handleXForwardedFor(name, List.of(value));
		assertEquals(remoteAddress, proxyRequest.remoteAddress());
	}

	@Test
	public void testHandleXForwardedForSingleIp() {
		final var host = IdGenerator.generateId();
		final var uri = URI.create("test://" + host);
		final var name = IdGenerator.generateId();
		final var proxyAddr = IdGenerator.generateId();
		final var proxyAddress = InetSocketAddress.createUnresolved(proxyAddr, 0);
		final var remoteAddr = "::1";
		final var remoteInetAddress = mock(InetAddress.class);
		when(remoteInetAddress.getHostAddress()).thenReturn(remoteAddr);
		final var remoteAddress = new InetSocketAddress(remoteInetAddress, 0);
		final var proxyRequest = new ProxyRequest(true, Set.of(proxyAddr), uri, proxyAddress);
		IuTestLogger.expect(ProxyRequest.class.getName(), Level.INFO,
				"remote client " + remoteAddress + " not in allowed proxy set");
		try (final var mockIuWebUtils = mockStatic(IuWebUtils.class)) {
			mockIuWebUtils.when(() -> IuWebUtils.getInetAddress(remoteAddr)).thenReturn(remoteInetAddress);
			proxyRequest.handleXForwardedFor(name, List.of(remoteAddr));
		}
		assertEquals(remoteAddress, proxyRequest.remoteAddress());
	}

}
