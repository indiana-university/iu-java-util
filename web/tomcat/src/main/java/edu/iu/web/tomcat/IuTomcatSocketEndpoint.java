package edu.iu.web.tomcat;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.apache.juli.logging.Log;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SocketEvent;
import org.apache.tomcat.util.net.SocketProcessorBase;
import org.apache.tomcat.util.net.SocketWrapperBase;

import edu.iu.web.WebUpgradeSocketWrapper;

public class IuTomcatSocketEndpoint extends AbstractEndpoint<WebUpgradeSocketWrapper, WebUpgradeSocketWrapper> {

	@Override
	public Handler<WebUpgradeSocketWrapper> getHandler() {
		Handler<WebUpgradeSocketWrapper> handler = super.getHandler();
		if (handler != null)
			return handler;

		// Tomcat expects non-null on close, even if resources have already been
		// released.
		return new Handler<WebUpgradeSocketWrapper>() {
			@Override
			public SocketState process(SocketWrapperBase<WebUpgradeSocketWrapper> socket, SocketEvent status) {
				return SocketState.CLOSED;
			}

			@Override
			public Object getGlobal() {
				return null;
			}

//			@Override
//			public Set<WebUpgradeSocketWrapper> getOpenSockets() {
//				return Collections.emptySet();
//			}

			@Override
			public void release(SocketWrapperBase<WebUpgradeSocketWrapper> socketWrapper) {
			}

			@Override
			public void pause() {
			}

			@Override
			public void recycle() {
			}
		};
	}

	@Override
	protected void createSSLContext(SSLHostConfig sslHostConfig) {
	}

	@Override
	protected InetSocketAddress getLocalAddress() throws IOException {
		return null;
	}

//	@Override
//	public boolean isAlpnSupported() {
//		return false;
//	}

//	@Override
//	protected boolean getDeferAccept() {
//		return false;
//	}

	@Override
	protected SocketProcessorBase<WebUpgradeSocketWrapper> createSocketProcessor(
			SocketWrapperBase<WebUpgradeSocketWrapper> socketWrapper, SocketEvent event) {
		return null;
	}

	@Override
	public void bind() throws Exception {
	}

	@Override
	public void unbind() throws Exception {
	}

	@Override
	public void startInternal() throws Exception {
	}

	@Override
	public void stopInternal() throws Exception {
	}

	@Override
	protected Log getLog() {
		return null;
	}

	@Override
	protected void doCloseServerSocket() throws IOException {
	}

	@Override
	protected WebUpgradeSocketWrapper serverSocketAccept() throws Exception {
		return null;
	}

	@Override
	protected boolean setSocketOptions(WebUpgradeSocketWrapper socket) {
		return false;
	}

	@Override
	protected void closeSocket(WebUpgradeSocketWrapper socket) {
	}

	@Override
	protected void destroySocket(WebUpgradeSocketWrapper socket) {
	}

//	@Override
//	protected void setDefaultSslHostConfig(SSLHostConfig sslHostConfig) {
//		throw new UnsupportedOperationException();
//	}

}
