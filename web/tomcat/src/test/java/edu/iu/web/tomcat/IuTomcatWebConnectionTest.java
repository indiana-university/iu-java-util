package edu.iu.web.tomcat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import edu.iu.web.WebUpgradeConnection;
import edu.iu.web.WebUpgradeReadListener;
import edu.iu.web.WebUpgradeWriteListener;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;

public class IuTomcatWebConnectionTest {

	private WebUpgradeConnection connection = mock(WebUpgradeConnection.class);

	@Test
	void close_closesConnection() throws Exception {
		IuTomcatWebConnection webConnection = new IuTomcatWebConnection(connection);
		webConnection.close();
		verify(connection).close();
	}

	@Test
	void getInputStream_returnsServletInputStream() throws Exception {
		try (IuTomcatWebConnection webConnection = new IuTomcatWebConnection(connection)) {
			ServletInputStream inputStream = webConnection.getInputStream();
			assertNotNull(inputStream);
		}
	}

	@Test
	void getOutputStream_returnsServletOutputStream() throws Exception {
		try (IuTomcatWebConnection webConnection = new IuTomcatWebConnection(connection)) {
			ServletOutputStream outputStream = webConnection.getOutputStream();
			assertNotNull(outputStream);
		}
	}

	@Test
	void inputStream_read_returnsData() throws Exception {
		when(connection.read()).thenReturn(1);
		try (IuTomcatWebConnection webConnection = new IuTomcatWebConnection(connection)) {
			ServletInputStream inputStream = webConnection.getInputStream();
			assertEquals(1, inputStream.read());
		}
	}

	@Test
	void inputStream_isReady_returnsTrueWhenReady() throws Exception {
		when(connection.isReadReady()).thenReturn(true);
		try (IuTomcatWebConnection webConnection = new IuTomcatWebConnection(connection)) {
			ServletInputStream inputStream = webConnection.getInputStream();
			assertTrue(inputStream.isReady());
		}
	}

	@Test
	void inputStream_isFinished_returnsTrueWhenFinished() throws Exception {
		when(connection.isFinished()).thenReturn(true);
		try (IuTomcatWebConnection webConnection = new IuTomcatWebConnection(connection)) {
			ServletInputStream inputStream = webConnection.getInputStream();
			assertTrue(inputStream.isFinished());
		}
	}

	@Test
	void outputStream_write_writesData() throws Exception {
		try (IuTomcatWebConnection webConnection = new IuTomcatWebConnection(connection)) {
			ServletOutputStream outputStream = webConnection.getOutputStream();
			outputStream.write(1);
			verify(connection).write(1);
		}
	}

	@Test
	void outputStream_isReady_returnsTrueWhenReady() throws Exception {
		when(connection.isWriteReady()).thenReturn(true);
		try (IuTomcatWebConnection webConnection = new IuTomcatWebConnection(connection)) {
			ServletOutputStream outputStream = webConnection.getOutputStream();
			assertTrue(outputStream.isReady());
		}
	}

	@Test
	void inputStream_setReadListener_setsReadListener() throws Exception {
		try (IuTomcatWebConnection webConnection = new IuTomcatWebConnection(connection)) {
			ServletInputStream inputStream = webConnection.getInputStream();
			ReadListener listener = mock(ReadListener.class);
			inputStream.setReadListener(listener);
			verify(connection).setReadListener(any(WebUpgradeReadListener.class));
		}
	}

	@Test
	void outputStream_setWriteListener_setsWriteListener() throws Exception {
		try (IuTomcatWebConnection webConnection = new IuTomcatWebConnection(connection)) {
			ServletOutputStream outputStream = webConnection.getOutputStream();
			WriteListener listener = mock(WriteListener.class);
			outputStream.setWriteListener(listener);
			verify(connection).setWriteListener(any(WebUpgradeWriteListener.class));
		}
	}

	@Test
	void inputStream_outputStream_read_write() throws Exception {
		try (IuTomcatWebConnection webConnection = new IuTomcatWebConnection(connection)) {
			ServletInputStream inputStream = webConnection.getInputStream();
			ReadListener readListener = mock(ReadListener.class);
			inputStream.setReadListener(readListener);
			ArgumentCaptor<WebUpgradeReadListener> readListenerCaptor = ArgumentCaptor
					.forClass(WebUpgradeReadListener.class);
			verify(connection).setReadListener(readListenerCaptor.capture());
			ServletOutputStream outputStream = webConnection.getOutputStream();
			WriteListener writeListener = mock(WriteListener.class);
			outputStream.setWriteListener(writeListener);
			ArgumentCaptor<WebUpgradeWriteListener> writeListenerCaptor = ArgumentCaptor
					.forClass(WebUpgradeWriteListener.class);
			verify(connection).setWriteListener(writeListenerCaptor.capture());

			WebUpgradeReadListener upgradeReadListener = readListenerCaptor.getValue();
			upgradeReadListener.onDataAvailable();
			verify(readListener).onDataAvailable();
			upgradeReadListener.onAllDataRead();
			verify(readListener).onAllDataRead();
			upgradeReadListener.onError(new Exception());
			verify(readListener).onError(any());

			WebUpgradeWriteListener upgradeWriteListener = writeListenerCaptor.getValue();
			upgradeWriteListener.onWritePossible();
			verify(writeListener).onWritePossible();
			upgradeWriteListener.onError(new Exception());
			verify(writeListener).onError(any());

		}
	}

}
