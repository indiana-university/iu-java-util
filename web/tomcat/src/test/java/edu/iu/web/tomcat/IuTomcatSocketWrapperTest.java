package edu.iu.web.tomcat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.tomcat.util.net.ApplicationBufferHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IuException;
import edu.iu.web.WebUpgradeSocketWrapper;

public class IuTomcatSocketWrapperTest {

	private IuTomcatSocketWrapper socketWrapper;
	private WebUpgradeSocketWrapper endpointWrapper;

	@BeforeEach
	void setUp() {
		endpointWrapper = mock(WebUpgradeSocketWrapper.class);
		socketWrapper = new IuTomcatSocketWrapper(endpointWrapper);
	}

	@Test
	void read_withBlockingAndByteArray_readsData() throws IOException {
		byte[] buffer = new byte[10];
		when(endpointWrapper.read(true, buffer, 0, 10)).thenReturn(10);
		assertEquals(10, socketWrapper.read(true, buffer, 0, 10));
	}

	@Test
	void read_withBlockingAndByteBuffer_readsData() throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate(10);
		when(endpointWrapper.read(true, buffer)).thenReturn(10);
		assertEquals(10, socketWrapper.read(true, buffer));
	}

	@Test
	void isReadyForRead_returnsTrueWhenReady() throws IOException {
		when(endpointWrapper.isReadyForRead()).thenReturn(true);
		assertTrue(socketWrapper.isReadyForRead());
	}

	@Test
	void setAppReadBufHandler_setsHandler() {
		ApplicationBufferHandler handler = mock(ApplicationBufferHandler.class);
		socketWrapper.setAppReadBufHandler(handler);
		verify(endpointWrapper).setAppReadBufHandler(any(IuTomcatWebAppBufferHandler.class));
	}

	@Test
	void doClose_closesSocket() {
		socketWrapper.doClose();
		verify(endpointWrapper).close();
	}

	@Test
	void isClosed_returnsTrueWhenClosed() {
		when(endpointWrapper.isClosed()).thenReturn(true);
		assertTrue(socketWrapper.isClosed());
	}

	@Test
	void doWrite_withBlocking_flushesData() throws IOException {
		socketWrapper.doWrite(true);
		verify(endpointWrapper).flush(true);
	}

	@Test
	void doWrite_withBlockingAndByteBuffer_flushesData() throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate(10);
		socketWrapper.doWrite(true, buffer);
		verify(endpointWrapper).doWrite(true, buffer);
	}

	@Test
	void hasAsyncIO_returnsFalse() {
		assertFalse(socketWrapper.hasAsyncIO());
	}

	@Test
	void newOperationState_throwsUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class,
				() -> socketWrapper.newOperationState(true, null, 0, 0, null, 0, null, null, null, null, null, null));
	}

	@Test
	void registerReadInterest_throwsUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> socketWrapper.registerReadInterest());
	}

	@Test
	void registerWriteInterest_throwsUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> socketWrapper.registerWriteInterest());
	}

	@Test
	void populateRemoteHost_throwsUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> socketWrapper.populateRemoteHost());
	}

	@Test
	void populateRemoteAddr_throwsUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> socketWrapper.populateRemoteAddr());
	}

	@Test
	void populateRemotePort_throwsUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> socketWrapper.populateRemotePort());
	}

	@Test
	void populateLocalAddr_throwsUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> socketWrapper.populateLocalAddr());
	}

	@Test
	void populateLocalName_throwsUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> socketWrapper.populateLocalName());
	}

	@Test
	void populateLocalPort_throwsUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> socketWrapper.populateLocalPort());
	}

	@Test
	void createSendfileData_throwsUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> socketWrapper.createSendfileData(null, 0, 0));
	}

	@Test
	void processSendfile_throwsUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> socketWrapper.processSendfile(null));
	}

	@Test
	void doClientAuth_throwsUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> socketWrapper.doClientAuth(null));
	}

	@Test
	void getSslSupport_throwsUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> socketWrapper.getSslSupport());
	}

	@Test
	void flushNonBlocking_returnsFalse() {
		assertFalse(IuException.unchecked(() -> socketWrapper.flushNonBlocking()));
	}

}
