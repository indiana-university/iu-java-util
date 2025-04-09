package edu.iu.web.tomcat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.logging.Level;
import java.util.regex.Pattern;

import org.apache.coyote.http11.upgrade.InternalHttpUpgradeHandler;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SocketEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import edu.iu.UnsafeRunnable;
import edu.iu.test.IuTestLogger;
import edu.iu.web.WebUpgradeConnection;
import edu.iu.web.WebUpgradeSSLSupport;
import edu.iu.web.WebUpgradeSocketWrapper;
import jakarta.servlet.http.HttpUpgradeHandler;

@SuppressWarnings("javadoc")
public class IuTomcatUpgradeHandlerTest {

	@Test
	void init_initializesUpgradeHandler() {
		WebUpgradeConnection connection = mock(WebUpgradeConnection.class);
		HttpUpgradeHandler upgradeHandler = mock(HttpUpgradeHandler.class);
		IuTomcatUpgradeHandler handler = new IuTomcatUpgradeHandler("/test", upgradeHandler);
		ArgumentCaptor<UnsafeRunnable> taskCaptor = ArgumentCaptor.forClass(UnsafeRunnable.class);
		IuTomcatUpgradeHandler handlerSpy = spy(handler);
		handlerSpy.init(connection);
		verify(handlerSpy).bind(any(Boolean.class), taskCaptor.capture());
		verify(upgradeHandler).init(any(IuTomcatWebConnection.class));
		final var pattern = Pattern.compile("\\AHttpUpgradeHandler.* init /test\\Z");
		assertTrue(pattern.matcher(taskCaptor.getValue().toString()).matches());
	}

	@Test
	void destroy_destroysUpgradeHandler() {
		HttpUpgradeHandler upgradeHandler = mock(HttpUpgradeHandler.class);
		IuTomcatUpgradeHandler handler = new IuTomcatUpgradeHandler("/test", upgradeHandler);
		ArgumentCaptor<UnsafeRunnable> taskCaptor = ArgumentCaptor.forClass(UnsafeRunnable.class);
		IuTomcatUpgradeHandler handlerSpy = spy(handler);
		handlerSpy.destroy();
		verify(handlerSpy).bind(any(Boolean.class), taskCaptor.capture());
		verify(upgradeHandler).destroy();
		final var pattern = Pattern.compile("\\AHttpUpgradeHandler.* destroy /test\\Z");
		assertTrue(pattern.matcher(taskCaptor.getValue().toString()).matches());
	}

	@Test
	void upgradeDispatch_withInternalHandler_returnsSocketState() {
		IuTestLogger.allow("edu.iu.web.tomcat.IuTomcatUpgradeHandler", Level.FINE);
		InternalHttpUpgradeHandler internalHandler = mock(InternalHttpUpgradeHandler.class);
		when(internalHandler.upgradeDispatch(SocketEvent.OPEN_READ)).thenReturn(SocketState.OPEN);
		IuTomcatUpgradeHandler handler = new IuTomcatUpgradeHandler("/test", internalHandler);
		ArgumentCaptor<UnsafeRunnable> taskCaptor = ArgumentCaptor.forClass(UnsafeRunnable.class);
		IuTomcatUpgradeHandler handlerSpy = spy(handler);
		assertEquals("OPEN", handlerSpy.upgradeDispatch("OPEN_READ"));
		verify(handlerSpy).bind(any(Boolean.class), taskCaptor.capture());
		final var pattern = Pattern.compile("\\AInternalHttpUpgradeHandler.* dispatch OPEN_READ /test\\Z");
		assertTrue(pattern.matcher(taskCaptor.getValue().toString()).matches());
	}

	@Test
	void upgradeDispatch_withNonInternalHandler_throwsUnsupportedOperationException() {
		HttpUpgradeHandler upgradeHandler = mock(HttpUpgradeHandler.class);
		IuTomcatUpgradeHandler handler = new IuTomcatUpgradeHandler("/test", upgradeHandler);
		assertThrows(UnsupportedOperationException.class, () -> handler.upgradeDispatch("OPEN_READ"));
	}

	@Test
	void setSocketWrapper_withInternalHandler_setsSocketWrapper() {
		InternalHttpUpgradeHandler internalHandler = mock(InternalHttpUpgradeHandler.class);
		WebUpgradeSocketWrapper socketWrapper = mock(WebUpgradeSocketWrapper.class);
		IuTomcatUpgradeHandler handler = new IuTomcatUpgradeHandler("/test", internalHandler);
		handler.setSocketWrapper(socketWrapper);
		verify(internalHandler).setSocketWrapper(any(IuTomcatSocketWrapper.class));
	}

	@Test
	void setSocketWrapper_withNonInternalHandler_throwsUnsupportedOperationException() {
		HttpUpgradeHandler upgradeHandler = mock(HttpUpgradeHandler.class);
		WebUpgradeSocketWrapper socketWrapper = mock(WebUpgradeSocketWrapper.class);
		IuTomcatUpgradeHandler handler = new IuTomcatUpgradeHandler("/test", upgradeHandler);
		assertThrows(UnsupportedOperationException.class, () -> handler.setSocketWrapper(socketWrapper));
	}

	@Test
	void setSslSupport_withInternalHandler_setsSslSupport() {
		InternalHttpUpgradeHandler internalHandler = mock(InternalHttpUpgradeHandler.class);
		WebUpgradeSSLSupport sslSupport = mock(WebUpgradeSSLSupport.class);
		IuTomcatUpgradeHandler handler = new IuTomcatUpgradeHandler("/test", internalHandler);
		handler.setSslSupport(sslSupport);
		verify(internalHandler).setSslSupport(any(IuTomcatSslSupport.class));
	}

	@Test
	void setSslSupport_withNonInternalHandler_throwsUnsupportedOperationException() {
		HttpUpgradeHandler upgradeHandler = mock(HttpUpgradeHandler.class);
		WebUpgradeSSLSupport sslSupport = mock(WebUpgradeSSLSupport.class);
		IuTomcatUpgradeHandler handler = new IuTomcatUpgradeHandler("/test", upgradeHandler);
		assertThrows(UnsupportedOperationException.class, () -> handler.setSslSupport(sslSupport));
	}

	@Test
	void pause_withInternalHandler_pausesHandler() {
		InternalHttpUpgradeHandler internalHandler = mock(InternalHttpUpgradeHandler.class);
		IuTomcatUpgradeHandler handler = new IuTomcatUpgradeHandler("/test", internalHandler);
		handler.pause();
		verify(internalHandler).pause();
	}

	@Test
	void pause_withNonInternalHandler_throwsUnsupportedOperationException() {
		HttpUpgradeHandler upgradeHandler = mock(HttpUpgradeHandler.class);
		IuTomcatUpgradeHandler handler = new IuTomcatUpgradeHandler("/test", upgradeHandler);
		assertThrows(UnsupportedOperationException.class, () -> handler.pause());
	}

	@Test
	void timeoutAsync_withInternalHandler_timesOutHandler() {
		InternalHttpUpgradeHandler internalHandler = mock(InternalHttpUpgradeHandler.class);
		IuTomcatUpgradeHandler handler = new IuTomcatUpgradeHandler("/test", internalHandler);
		handler.timeoutAsync(1000L);
		verify(internalHandler).timeoutAsync(1000L);
	}

	@Test
	void timeoutAsync_withNonInternalHandler_throwsUnsupportedOperationException() {
		HttpUpgradeHandler upgradeHandler = mock(HttpUpgradeHandler.class);
		IuTomcatUpgradeHandler handler = new IuTomcatUpgradeHandler("/test", upgradeHandler);
		assertThrows(UnsupportedOperationException.class, () -> handler.timeoutAsync(1000L));
	}

	@Test
	void bind_throwsExceptionIfTaskThrowsException() {
		IuTomcatUpgradeHandler handler = new IuTomcatUpgradeHandler("/test", null);
		UnsafeRunnable task = () -> {
			throw new RuntimeException("test");
		};
		assertThrows(RuntimeException.class, () -> handler.bind(true, task));
	}

	@Test
	void bind_throwsExceptionIfTaskThrowsError() {
		IuTomcatUpgradeHandler handler = new IuTomcatUpgradeHandler("/test", null);
		UnsafeRunnable task = () -> {
			throw new Error("test");
		};
		assertThrows(Error.class, () -> handler.bind(true, task));
	}

	@Test
	void bind_throwsIllegalStateExceptionIfTaskThrowsOtherThrowable() {
		IuTomcatUpgradeHandler handler = new IuTomcatUpgradeHandler("/test", null);
		UnsafeRunnable task = () -> {
			throw new Throwable("test");
		};
		assertThrows(IllegalStateException.class, () -> handler.bind(true, task));
	}
}
