package edu.iu.web.tomcat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.apache.tomcat.util.net.ApplicationBufferHandler;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SendfileDataBase;
import org.apache.tomcat.util.net.SendfileState;
import org.apache.tomcat.util.net.SocketBufferHandler;
import org.apache.tomcat.util.net.SocketWrapperBase;

import edu.iu.web.WebUpgradeSocketWrapper;

public class IuTomcatSocketWrapper extends SocketWrapperBase<WebUpgradeSocketWrapper> {

	public IuTomcatSocketWrapper(WebUpgradeSocketWrapper endpointWrapper) {
		super(endpointWrapper, new IuTomcatSocketEndpoint());
		this.socketBufferHandler = new SocketBufferHandler(16384, 16384, true);
	}

	@Override
	public int read(boolean block, byte[] b, int off, int len) throws IOException {
		return getSocket().read(block, b, off, len);
	}

	@Override
	public int read(boolean block, ByteBuffer to) throws IOException {
		return getSocket().read(block, to);
	}

	@Override
	public boolean isReadyForRead() throws IOException {
		return getSocket().isReadyForRead();
	}

	@Override
	public void setAppReadBufHandler(ApplicationBufferHandler handler) {
		getSocket().setAppReadBufHandler(new IuTomcatWebAppBufferHandler(handler));
	}

	@Override
	protected void doClose() {
		getSocket().close();
	}

	@Override
	public boolean isClosed() {
		return getSocket().isClosed();
	}

	@Override
	protected void doWrite(boolean block) throws IOException {
		super.doWrite(block);
		getSocket().flush(block);
	}

	@Override
	protected void doWrite(boolean block, ByteBuffer from) throws IOException {
		getSocket().doWrite(block, from);
	}

	// UNSUPPORTED ASYNC AND SSL OPERATIONS

	@Override
	public boolean hasAsyncIO() {
		return false;
	}

	@Override
	protected <A> SocketWrapperBase<WebUpgradeSocketWrapper>.OperationState<A> newOperationState(boolean read,
			ByteBuffer[] buffers, int offset, int length, BlockingMode block, long timeout, TimeUnit unit, A attachment,
			CompletionCheck check, CompletionHandler<Long, ? super A> handler, Semaphore semaphore,
			VectoredIOCompletionHandler<A> completion) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void registerReadInterest() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void registerWriteInterest() {
		throw new UnsupportedOperationException();
	}

	@Override
	protected void populateRemoteHost() {
		throw new UnsupportedOperationException();
	}

	@Override
	protected void populateRemoteAddr() {
		throw new UnsupportedOperationException();
	}

	@Override
	protected void populateRemotePort() {
		throw new UnsupportedOperationException();
	}

	@Override
	protected void populateLocalName() {
		throw new UnsupportedOperationException();
	}

	@Override
	protected void populateLocalAddr() {
		throw new UnsupportedOperationException();
	}

	@Override
	protected void populateLocalPort() {
		throw new UnsupportedOperationException();
	}

	@Override
	public SendfileDataBase createSendfileData(String filename, long pos, long length) {
		throw new UnsupportedOperationException();
	}

	@Override
	public SendfileState processSendfile(SendfileDataBase sendfileData) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void doClientAuth(SSLSupport sslSupport) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public SSLSupport getSslSupport() {
		throw new UnsupportedOperationException();
	}

	@Override
	protected boolean flushNonBlocking() throws IOException {
		return false;
	}

}
