package edu.iu.web.tomcat;

import java.io.IOException;
import java.security.cert.X509Certificate;

import org.apache.tomcat.util.net.SSLSupport;

import edu.iu.web.WebUpgradeSSLSupport;

public class IuTomcatSslSupport implements SSLSupport {

	private WebUpgradeSSLSupport delegate;

	public IuTomcatSslSupport(WebUpgradeSSLSupport delegate) {
		this.delegate = delegate;
	}

	@Override
	public String getCipherSuite() throws IOException {
		return delegate.getCipherSuite();
	}

	@Override
	public X509Certificate[] getPeerCertificateChain() throws IOException {
		return delegate.getPeerCertificateChain();
	}

	@Override
	public Integer getKeySize() throws IOException {
		return delegate.getKeySize();
	}

	@Override
	public String getSessionId() throws IOException {
		return delegate.getSessionId();
	}

	@Override
	public String getProtocol() throws IOException {
		return delegate.getProtocol();
	}

	@Override
	public String getRequestedProtocols() throws IOException {
		return delegate.getRequestedProtocols();
	}

	@Override
	public String getRequestedCiphers() throws IOException {
		return delegate.getRequestedCiphers();
	}

}
