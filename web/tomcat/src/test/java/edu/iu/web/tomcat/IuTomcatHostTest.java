package edu.iu.web.tomcat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.logging.Level;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.test.IuTestLogger;

public class IuTomcatHostTest {

	private IuTomcatHost host;
	private Context context;
	private File appBase;

	@BeforeEach
	void setUp() {
		context = mock(Context.class);
		when(context.getName()).thenReturn("context");
		appBase = new File("appBase");
		host = new IuTomcatHost(context, appBase);
	}

	@Test
	void findChild_withExistingChild_returnsChild() {
		assertEquals(context, host.findChild(context.getName()));
	}

	@Test
	void findChild_withNonExistingChild_returnsNull() {
		assertNull(host.findChild("nonExistingChild"));
	}

	@Test
	void findChildren_returnsArrayWithContext() {
		assertArrayEquals(new Context[] { context }, host.findChildren());
	}

	@Test
	void getAppBase_returnsAppBaseAsString() {
		assertEquals(appBase.toString(), host.getAppBase());
	}

	@Test
	void getAppBaseFile_returnsAppBaseFile() {
		assertEquals(appBase, host.getAppBaseFile());
	}

	@Test
	void startInternal_startsContextAndSetsStateToStarted() throws LifecycleException {
		IuTestLogger.allow("org.apache.catalina", Level.FINE);
		when(context.getState()).thenReturn(LifecycleState.STARTED);
		host.init();
		host.start();
		assertEquals(LifecycleState.STARTED, host.getState());
	}

	@Test
	void startInternal_withContextNotStarted_throwsIllegalStateException() throws LifecycleException {
		when(context.getState()).thenReturn(LifecycleState.FAILED);
		assertThrows(IllegalStateException.class, () -> host.startInternal());
	}

	@Test
	void stopInternal_stopsContextAndSetsStateToStopped() throws LifecycleException {
		IuTestLogger.allow("org.apache.catalina", Level.FINE);
		when(context.getState()).thenReturn(LifecycleState.STARTED);
		host.init();
		host.start();
		host.stop();
		assertEquals(LifecycleState.STOPPED, host.getState());
	}

	@Test
	void getXmlBase_returnsNull() {
		assertNull(host.getXmlBase());
	}

	@Test
	void setXmlBase_throwsUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> host.setXmlBase("xmlBase"));
	}

	@Test
	void getConfigBaseFile_returnsNull() {
		assertNull(host.getConfigBaseFile());
	}

	@Test
	void setAppBase_throwsUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> host.setAppBase("appBase"));
	}

	@Test
	void getAutoDeploy_returnsFalse() {
		assertEquals(false, host.getAutoDeploy());
	}

	@Test
	void setAutoDeploy_throwsUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> host.setAutoDeploy(true));
	}

	@Test
	void getConfigClass_returnsNull() {
		assertNull(host.getConfigClass());
	}

	@Test
	void setConfigClass_throwsUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> host.setConfigClass("configClass"));
	}

	@Test
	void getDeployOnStartup_returnsFalse() {
		assertEquals(false, host.getDeployOnStartup());
	}

	@Test
	void setDeployOnStartup_throwsUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> host.setDeployOnStartup(true));
	}

	@Test
	void getDeployIgnore_returnsNull() {
		assertEquals(null, host.getDeployIgnore());
	}

	@Test
	void getDeployIgnorePattern_returnsNull() {
		assertNull(host.getDeployIgnorePattern());
	}

	@Test
	void setDeployIgnore_throwsUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> host.setDeployIgnore("deployIgnore"));
	}

	@Test
	void getStartStopExecutor_returnsNull() {
		assertNull(host.getStartStopExecutor());
	}

	@Test
	void getCreateDirs_returnsFalse() {
		assertEquals(false, host.getCreateDirs());
	}

	@Test
	void setCreateDirs_throwsUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> host.setCreateDirs(true));
	}

	@Test
	void getUndeployOldVersions_returnsFalse() {
		assertEquals(false, host.getUndeployOldVersions());
	}

	@Test
	void setUndeployOldVersions_throwsUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> host.setUndeployOldVersions(true));
	}

	@Test
	void addAlias_throwsUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> host.addAlias("alias"));
	}

	@Test
	void findAliases_returnsEmptyStringArray() {
		assertArrayEquals(new String[0], host.findAliases());
	}

	@Test
	void removeAlias_throwsUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> host.removeAlias("alias"));
	}

	@Test
	void initInternal_doesNothing() {
		assertDoesNotThrow(() -> host.initInternal());
	}

	@Test
	void destroyInternal_throwsUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> host.destroyInternal());
	}

	@Test
	void getLegacyAppBase_returnsNull() {
		assertNull(host.getLegacyAppBase());
	}

	@Test
	void getLegacyAppBaseFile_returnsNull() {
		assertNull(host.getLegacyAppBaseFile());
	}

	@Test
	void setLegacyAppBase_throwsUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> host.setLegacyAppBase("legacyAppBase"));
	}

	@Test
	void toString_returnsHostName() {
		assertEquals("IuTomcatHost[localhost]", host.toString());
	}

}
