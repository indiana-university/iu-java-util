package edu.iu.web.tomcat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.security.cert.X509Certificate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.web.WebUpgradeSSLSupport;

public class IuTomcatSslSupportTest {

	private IuTomcatSslSupport sslSupport;
	private WebUpgradeSSLSupport delegate;

	@BeforeEach
	void setUp() {
		delegate = mock(WebUpgradeSSLSupport.class);
		sslSupport = new IuTomcatSslSupport(delegate);
	}

	@Test
	void getCipherSuite_returnsCipherSuiteFromDelegate() throws IOException {
		when(delegate.getCipherSuite()).thenReturn("TLS_AES_128_GCM_SHA256");
		assertEquals("TLS_AES_128_GCM_SHA256", sslSupport.getCipherSuite());
	}

	@Test
	void getPeerCertificateChain_returnsPeerCertificateChainFromDelegate() throws IOException {
		X509Certificate[] certificates = new X509Certificate[0];
		when(delegate.getPeerCertificateChain()).thenReturn(certificates);
		assertArrayEquals(certificates, sslSupport.getPeerCertificateChain());
	}

	@Test
	void getKeySize_returnsKeySizeFromDelegate() throws IOException {
		when(delegate.getKeySize()).thenReturn(2048);
		assertEquals(2048, sslSupport.getKeySize());
	}

	@Test
	void getSessionId_returnsSessionIdFromDelegate() throws IOException {
		when(delegate.getSessionId()).thenReturn("session123");
		assertEquals("session123", sslSupport.getSessionId());
	}

	@Test
	void getProtocol_returnsProtocolFromDelegate() throws IOException {
		when(delegate.getProtocol()).thenReturn("TLSv1.3");
		assertEquals("TLSv1.3", sslSupport.getProtocol());
	}

	@Test
	void getRequestedProtocols_returnsRequestedProtocolsFromDelegate() throws IOException {
		when(delegate.getRequestedProtocols()).thenReturn("TLSv1.2,TLSv1.3");
		assertEquals("TLSv1.2,TLSv1.3", sslSupport.getRequestedProtocols());
	}

	@Test
	void getRequestedCiphers_returnsRequestedCiphersFromDelegate() throws IOException {
		when(delegate.getRequestedCiphers()).thenReturn("TLS_AES_128_GCM_SHA256,TLS_AES_256_GCM_SHA384");
		assertEquals("TLS_AES_128_GCM_SHA256,TLS_AES_256_GCM_SHA384", sslSupport.getRequestedCiphers());
	}

}
