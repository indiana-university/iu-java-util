package edu.iu.web.tomcat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.logging.Level;

import org.apache.catalina.Container;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Loader;
import org.apache.catalina.Service;
import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.test.IuTestLogger;
import edu.iu.type.IuComponent;

@SuppressWarnings("javadoc")
public class IuTomcatEngineTest {

	private static IuTomcatEngine engine;

	@BeforeEach
	void setUp() throws Throwable {
		IuTestLogger.allow("edu.iu.web.tomcat", Level.CONFIG);
		IuTestLogger.allow("org.apache.catalina.authenticator", Level.FINER);
		IuTestLogger.allow("org.apache.catalina.core", Level.FINE);
		IuTestLogger.allow("org.apache.catalina.core", Level.WARNING);
		IuTestLogger.allow("org.apache.catalina.core", Level.SEVERE);
		IuTestLogger.allow("org.apache.catalina.session", Level.FINER);
		IuTestLogger.allow("org.apache.catalina.session", Level.FINE);
		IuTestLogger.allow("org.apache.catalina.startup", Level.FINER);
		IuTestLogger.allow("org.apache.catalina.startup", Level.FINE);
		IuTestLogger.allow("org.apache.catalina.startup", Level.SEVERE);
		IuTestLogger.allow("org.apache.catalina.util", Level.FINE);
		IuTestLogger.allow("org.apache.catalina.util", Level.WARNING);
		IuTestLogger.allow("org.apache.tomcat.util", Level.FINE);
		IuTestLogger.allow("org.apache.jasper.compiler", Level.FINER);
		IuTestLogger.allow("org.apache.jasper.EmbeddedServletOptions", Level.SEVERE);
		IuTestLogger.allow("org.apache.jasper.servlet", Level.FINE);

		WebResourceRoot webResourceRoot = mock(WebResourceRoot.class);
		WebResource webResource = mock(WebResource.class);
		Loader loader = mock(Loader.class);
		when(loader.getClassLoader()).thenReturn(Thread.currentThread().getContextClassLoader());
		when(webResource.getURL()).thenReturn(null);
		when(webResource.isFile()).thenReturn(false);
		when(webResource.getCanonicalPath()).thenReturn(null);
		when(webResourceRoot.getResource("/WEB-INF/web.xml")).thenReturn(webResource);
		when(webResourceRoot.getState()).thenReturn(LifecycleState.STARTED);
		when(webResourceRoot.getResource(org.apache.catalina.startup.Constants.TomcatWebXml)).thenReturn(webResource);
		when(webResourceRoot.getResource(org.apache.tomcat.util.scan.Constants.WEB_INF_CLASSES))
				.thenReturn(webResource);
		when(webResourceRoot.listResources("/WEB-INF/classes")).thenReturn(new WebResource[0]);
		when(webResourceRoot.getResource("/")).thenReturn(webResource);
		engine = new IuTomcatEngine(IuComponent.Kind.MODULAR_JAR, "testContext", new File("/tmp"), webResourceRoot,
				loader);
	}

	@Test
	void startInternal_startsHostAndSetsStateToStarting() throws Throwable {
		engine.init();
		engine.start();
		assertEquals(LifecycleState.STARTED, engine.getState());
	}

	@Test
	void stopInternal_stopsHostAndSetsStateToStopping() throws Throwable {
		engine.init();
		engine.start();
		engine.stop();
		assertEquals(LifecycleState.STOPPED, engine.getState());
	}

	@Test
	void findChild_withExistingHost_returnsHost() throws Throwable {
		assertEquals(engine.findChild(engine.getDefaultHost()), engine.findChildren()[0]);
	}

	@Test
	void findChild_withNonExistingHost_returnsNull() throws Throwable {
		assertNull(engine.findChild("nonExistingHost"));
	}

	@Test
	void findChildren_returnsArrayWithHost() throws Throwable {
		assertArrayEquals(new Container[] { engine.findChildren()[0] }, engine.findChildren());
	}

	@Test
	void getDefaultHost_returnsLocalhost() throws Throwable {
		assertEquals("localhost", engine.getDefaultHost());
	}

	@Test
	void setDefaultHost_throwsUnsupportedOperationException() throws Throwable {
		assertThrows(UnsupportedOperationException.class, () -> engine.setDefaultHost("newHost"));
	}

	@Test
	void getJvmRoute_returnsNull() throws Throwable {
		assertNull(engine.getJvmRoute());
	}

	@Test
	void setJvmRoute_throwsUnsupportedOperationException() throws Throwable {
		assertThrows(UnsupportedOperationException.class, () -> engine.setJvmRoute("newRoute"));
	}

	@Test
	void getService_returnsService() throws Throwable {
		assertNotNull(engine.getService());
	}

	@Test
	void setService_withDifferentService_throwsUnsupportedOperationException() throws Throwable {
		Service newService = mock(Service.class);
		assertThrows(UnsupportedOperationException.class, () -> engine.setService(newService));
	}

	@Test
	void toString_returnsEngineName() throws Throwable {
		assertEquals("IuTomcatEngine[testContext-engine]", engine.toString());
	}

	@Test
	void getAdapter_returnsAdapter() throws Throwable {
		assertNull(engine.getAdapter());
	}

	@Test
	void destroyInternal_doesNothing() throws Throwable {
		assertDoesNotThrow(() -> engine.destroyInternal());
	}

}
