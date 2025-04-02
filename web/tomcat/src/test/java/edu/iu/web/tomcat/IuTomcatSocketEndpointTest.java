
package edu.iu.web.tomcat;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.io.IOException;

import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SocketEvent;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.web.WebUpgradeSocketWrapper;

public class IuTomcatSocketEndpointTest {

    private IuTomcatSocketEndpoint socketEndpoint;
    private SocketWrapperBase<WebUpgradeSocketWrapper> socketWrapper;
    private SSLHostConfig sslHostConfig;

    @BeforeEach
    void setUp() {
        socketEndpoint = spy(new IuTomcatSocketEndpoint());
        socketWrapper = mock(SocketWrapperBase.class);
        sslHostConfig = mock(SSLHostConfig.class);
    }

    @Test
    void getHandler_returnsNonNullHandlerWithCorrectReturnsFromHandlerMethods() {
    	final var handler = socketEndpoint.getHandler();
        assertNotNull(handler);
        assertEquals(SocketState.CLOSED, handler.process(socketWrapper, SocketEvent.OPEN_READ));
        assertNull(handler.getGlobal());
        assertDoesNotThrow(() -> handler.release(socketWrapper));
        assertDoesNotThrow(() -> handler.pause());
        assertDoesNotThrow(() -> handler.recycle());
    }
    
    @Test
    void getHandler_returnsSuperHandlerIfSet() {
    	final var mockHandler = mock(AbstractEndpoint.Handler.class);
    	socketEndpoint.setHandler(mockHandler);
		final var handler1 = socketEndpoint.getHandler();
		assertEquals(mockHandler, handler1);
    }

    @Test
    void createSSLContext_doesNotThrowException() {
        assertDoesNotThrow(() -> socketEndpoint.createSSLContext(sslHostConfig));
    }

    @Test
    void getLocalAddress_returnsNull() throws IOException {
        assertNull(socketEndpoint.getLocalAddress());
    }

    @Test
    void createSocketProcessor_returnsNull() {
        assertNull(socketEndpoint.createSocketProcessor(socketWrapper, SocketEvent.OPEN_READ));
    }

    @Test
    void bind_doesNotThrowException() {
        assertDoesNotThrow(() -> socketEndpoint.bind());
    }

    @Test
    void unbind_doesNotThrowException() {
        assertDoesNotThrow(() -> socketEndpoint.unbind());
    }

    @Test
    void startInternal_doesNotThrowException() {
        assertDoesNotThrow(() -> socketEndpoint.startInternal());
    }

    @Test
    void stopInternal_doesNotThrowException() {
        assertDoesNotThrow(() -> socketEndpoint.stopInternal());
    }

    @Test
    void doCloseServerSocket_doesNotThrowException() throws IOException {
        assertDoesNotThrow(() -> socketEndpoint.doCloseServerSocket());
    }

    @Test
    void serverSocketAccept_returnsNull() throws Exception {
        assertNull(socketEndpoint.serverSocketAccept());
    }

    @Test
    void setSocketOptions_returnsFalse() {
        assertFalse(socketEndpoint.setSocketOptions(mock(WebUpgradeSocketWrapper.class)));
    }

    @Test
    void closeSocket_doesNotThrowException() {
        assertDoesNotThrow(() -> socketEndpoint.closeSocket(mock(WebUpgradeSocketWrapper.class)));
    }

    @Test
    void destroySocket_doesNotThrowException() {
        assertDoesNotThrow(() -> socketEndpoint.destroySocket(mock(WebUpgradeSocketWrapper.class)));
    }
    
    @Test
	void getLog_returnsNull() {
		assertNull(socketEndpoint.getLog());
	}
    
}
