
package edu.iu.web.tomcat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import org.apache.catalina.Container;
import org.apache.catalina.ContainerListener;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Pipeline;
import org.apache.catalina.Realm;
import org.apache.catalina.realm.NullRealm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class AbstractIuTomcatContainerTest {

	private TestContainer container;

	private class TestContainer extends AbstractIuTomcatContainer {

		@Override
		public Container findChild(String name) {
			return null;
		}

		@Override
		public Container[] findChildren() {
			return null;
		}

		@Override
		protected void initInternal() throws LifecycleException {
		}

		@Override
		protected void startInternal() throws LifecycleException {
		}

		@Override
		protected void stopInternal() throws LifecycleException {
		}

		@Override
		protected void destroyInternal() throws LifecycleException {
		}
	}

	@BeforeEach
	void setUp() {
		container = new TestContainer();
	}

	@Test
	void getLogger_returnsLogger() {
		assertNotNull(container.getLogger());
	}

	@Test
	void getLogName_returnsLogName() {
		assertEquals("edu.iu.web.tomcat.AbstractIuTomcatContainerTest$TestContainer", container.getLogName());
	}

	@Test
	void getObjectName_returnsNull() {
		assertNull(container.getObjectName());
	}

	@Test
	void getDomain_returnsNull() {
		assertNull(container.getDomain());
	}

	@Test
	void getMBeanKeyProperties_returnsNull() {
		assertNull(container.getMBeanKeyProperties());
	}

	@Test
	void setName_setsName_getName_returnsName() {
		container.setName("testName");
		assertEquals("testName", container.getName());
	}

	@Test
	void setParent_setsParent_getParent_returnsParent() {
		Container parent = mock(Container.class);
		container.setParent(parent);
		assertEquals(parent, container.getParent());
	}

	@Test
	void getRealm_returnsRealm() {
		assertEquals(NullRealm.class, container.getRealm().getClass());
	}

	@Test
	void setRealm_throwsUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> container.setRealm(mock(Realm.class)));
	}

	@Test
	void getPipeline_returnsPipeline() {
		Pipeline pipeline = container.getPipeline();
		assertNotNull(pipeline);
	}

	@Test
	void addContainerListener_addsListener() {
		ContainerListener listener = mock(ContainerListener.class);
		container.addContainerListener(listener);
		assertEquals(1, container.findContainerListeners().length);
	}

	@Test
	void removeContainerListener_removesListener() {
		ContainerListener listener = mock(ContainerListener.class);
		container.addContainerListener(listener);
		container.removeContainerListener(listener);
		assertEquals(0, container.findContainerListeners().length);
	}

	@Test
	void getCatalinaBase_doeNotReturnNull() {
		assertNotNull(container.getCatalinaBase());
	}

	@Test
	void getCatalinaHome_returnsNull() {
		assertNull(container.getCatalinaHome());
	}

	@Test
	void getCluster_returnsNull() {
		assertNull(container.getCluster());
	}

	@Test
	void setCluster_throwsUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> container.setCluster(null));
	}

	@Test
	void getBackgroundProcessorDelay_returnsZero() {
		assertEquals(0, container.getBackgroundProcessorDelay());
	}

	@Test
	void setBackgroundProcessorDelay_throwsUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> container.setBackgroundProcessorDelay(100));
	}

	@Test
	void getParentClassLoader_returnsNull() {
		assertNotNull(container.getParentClassLoader());
	}

	@Test
	void setParentClassLoader_throwsUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> container.setParentClassLoader(null));
	}

	@Test
	void backgroundProcess_throwsUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> container.backgroundProcess());
	}

	@Test
	void addChild_throwsUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> container.addChild(null));
	}

	@Test
	void removeChild_throwsUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> container.removeChild(null));
	}

	@Test
	void addContainerListener_removeContainerListener() {
		ContainerListener listener = mock(ContainerListener.class);
		container.addContainerListener(listener);
		assertEquals(1, container.findContainerListeners().length);
		container.addContainerListener(listener);
		container.removeContainerListener(listener);
		container.removeContainerListener(listener);
		assertEquals(0, container.findContainerListeners().length);
	}

	@Test
	void addPropertyChangeListener_throwsUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> container.addPropertyChangeListener(null));
	}

	@Test
	void removePropertyChangeListener_throwsUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> container.removePropertyChangeListener(null));
	}

	@Test
	void fireContainerEvent_throwsUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> container.fireContainerEvent(null, null));
	}

	@Test
	void logAccess_throwsUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> container.logAccess(null, null, 0, false));
	}

	@Test
	void getAccessLog_returnsNull() {
		assertNull(container.getAccessLog());
	}

	@Test
	void getStartStopThreads_returnsZero() {
		assertEquals(0, container.getStartStopThreads());
	}

	@Test
	void setStartStopThreads_throwsUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> container.setStartStopThreads(1));
	}

}
