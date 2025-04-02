
package edu.iu.web.tomcat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.coyote.Adapter;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class IuTomcatProtocolHandlerTest {

    private IuTomcatProtocolHandler protocolHandler;
    private Adapter adapter;
    private ScheduledExecutorService utilityExecutor;

    @BeforeEach
    void setUp() {
        protocolHandler = new IuTomcatProtocolHandler();
        adapter = mock(Adapter.class);
        utilityExecutor = mock(ScheduledExecutorService.class);
    }

    @Test
    void getPort_returnsZero() {
        assertEquals(0, protocolHandler.getPort());
    }

    @Test
    void setAdapter_setsAdapter() {
        protocolHandler.setAdapter(adapter);
        assertEquals(adapter, protocolHandler.getAdapter());
    }

    @Test
    void getExecutor_returnsNull() {
        assertNull(protocolHandler.getExecutor());
    }

    @Test
    void init_throwsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> protocolHandler.init());
    }

    @Test
    void start_throwsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> protocolHandler.start());
    }

    @Test
    void pause_throwsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> protocolHandler.pause());
    }

    @Test
    void resume_throwsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> protocolHandler.resume());
    }

    @Test
    void stop_throwsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> protocolHandler.stop());
    }

    @Test
    void destroy_throwsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> protocolHandler.destroy());
    }

    @Test
    void isSendfileSupported_returnsFalse() {
        assertFalse(protocolHandler.isSendfileSupported());
    }

    @Test
    void addSslHostConfig_throwsUnsupportedOperationException() {
        SSLHostConfig sslHostConfig = mock(SSLHostConfig.class);
        assertThrows(UnsupportedOperationException.class, () -> protocolHandler.addSslHostConfig(sslHostConfig));
    }

    @Test
    void findSslHostConfigs_returnsNull() {
        assertNull(protocolHandler.findSslHostConfigs());
    }

    @Test
    void addUpgradeProtocol_throwsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> protocolHandler.addUpgradeProtocol(null));
    }

    @Test
    void findUpgradeProtocols_returnsNull() {
        assertNull(protocolHandler.findUpgradeProtocols());
    }

    @Test
    void closeServerSocketGraceful_throwsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> protocolHandler.closeServerSocketGraceful());
    }

    @Test
    void setExecutor_throwsUnsupportedOperationException() {
        Executor executor = mock(Executor.class);
        assertThrows(UnsupportedOperationException.class, () -> protocolHandler.setExecutor(executor));
    }

    @Test
    void getUtilityExecutor_returnsUtilityExecutor() {
        protocolHandler.setUtilityExecutor(utilityExecutor);
        assertEquals(utilityExecutor, protocolHandler.getUtilityExecutor());
    }

    @Test
    void awaitConnectionsClose_returnsZero() {
        assertEquals(0, protocolHandler.awaitConnectionsClose(1000));
    }

    @Test
    void addSslHostConfigWithReplace_throwsUnsupportedOperationException() {
        SSLHostConfig sslHostConfig = mock(SSLHostConfig.class);
        assertThrows(UnsupportedOperationException.class, () -> protocolHandler.addSslHostConfig(sslHostConfig, true));
    }
}
