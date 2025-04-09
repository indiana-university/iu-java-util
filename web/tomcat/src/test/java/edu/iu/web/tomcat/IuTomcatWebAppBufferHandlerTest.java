
package edu.iu.web.tomcat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;

import org.apache.tomcat.util.net.ApplicationBufferHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuTomcatWebAppBufferHandlerTest {

	private IuTomcatWebAppBufferHandler bufferHandler;
	private ApplicationBufferHandler delegate;

	@BeforeEach
	void setUp() {
		delegate = mock(ApplicationBufferHandler.class);
		bufferHandler = new IuTomcatWebAppBufferHandler(delegate);
	}

	@Test
	void setByteBuffer_setsBufferInDelegate() {
		ByteBuffer buffer = ByteBuffer.allocate(10);
		bufferHandler.setByteBuffer(buffer);
		verify(delegate).setByteBuffer(buffer);
	}

	@Test
	void getByteBuffer_returnsBufferFromDelegate() {
		ByteBuffer buffer = ByteBuffer.allocate(10);
		when(delegate.getByteBuffer()).thenReturn(buffer);
		assertEquals(buffer, bufferHandler.getByteBuffer());
	}

	@Test
	void expand_expandsBufferInDelegate() {
		bufferHandler.expand(20);
		verify(delegate).expand(20);
	}

	@Test
	void getByteBuffer_whenDelegateReturnsNull_returnsNull() {
		when(delegate.getByteBuffer()).thenReturn(null);
		assertNull(bufferHandler.getByteBuffer());
	}

	@Test
	void expand_withNegativeSize_throwsIllegalArgumentException() {
		// TODO: is this test necessary?
		// Whether an exception is thrown depends on which implementation of
		// ApplicationBufferHandler is used.
		// For several classes that show as implementations of ApplicationBufferHandler
		// in the IDE, expand is a no-op.
		doThrow(IllegalArgumentException.class).when(delegate).expand(-1);
		assertThrows(IllegalArgumentException.class, () -> bufferHandler.expand(-1));
	}
}
