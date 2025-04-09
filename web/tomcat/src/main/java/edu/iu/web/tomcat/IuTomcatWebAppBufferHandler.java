package edu.iu.web.tomcat;

import java.nio.ByteBuffer;

import org.apache.tomcat.util.net.ApplicationBufferHandler;

import edu.iu.web.WebApplicationBufferHandler;

/**
 * An implementation of the WebApplicationBufferHandler interface.
 */
public class IuTomcatWebAppBufferHandler implements WebApplicationBufferHandler {

	private final ApplicationBufferHandler delegate;

	/**
	 * Creates a new instance of IuTomcatWebAppBufferHandler.
	 * 
	 * @param delegate The delegate buffer handler.
	 */
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
