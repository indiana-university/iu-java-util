package edu.iu.web.tomcat;

import java.nio.ByteBuffer;

import org.apache.tomcat.util.net.ApplicationBufferHandler;

import edu.iu.web.WebApplicationBufferHandler;

public class IuTomcatWebAppBufferHandler implements WebApplicationBufferHandler {

	private final ApplicationBufferHandler delegate;

	public IuTomcatWebAppBufferHandler(ApplicationBufferHandler delegate) {
		this.delegate = delegate;
	}

	@Override
	public void setByteBuffer(ByteBuffer buffer) {
		delegate.setByteBuffer(buffer);
	}

	@Override
	public ByteBuffer getByteBuffer() {
		return delegate.getByteBuffer();
	}

	@Override
	public void expand(int size) {
		delegate.expand(size);
	}

}
