
package edu.iu.web.tomcat;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.spy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class IuTomcatInstanceManagerTest {

	private IuTomcatInstanceManager instanceManager;
	private ClassLoader classLoader;

	@BeforeEach
	void setUp() {
		classLoader = spy(this.getClass().getClassLoader());
		instanceManager = new IuTomcatInstanceManager(classLoader);
	}

	@Test
	void newInstance_createsNewInstance() throws Exception {
		Object instance = instanceManager.newInstance("java.lang.String");
		assertTrue(instance instanceof String);
	}

	@Test
	void newInstance_withClassLoader_createsNewInstance() throws Exception {
		Object instance = instanceManager.newInstance("java.lang.String", classLoader);
		assertTrue(instance instanceof String);
	}

	@Test
	void newInstance_withClass_createsNewInstance() throws Exception {
		Object instance = instanceManager.newInstance(String.class);
		assertTrue(instance instanceof String);
	}

	@Test
	void newInstance_withInvalidClassName_throwsClassNotFoundException() {
		assertThrows(ClassNotFoundException.class, () -> instanceManager.newInstance("invalid.ClassName"));
	}

	@Test
	void destroyInstance_doesNotThrowException() throws Exception {
		String instance = "test";
		assertDoesNotThrow(() -> instanceManager.destroyInstance(instance));
	}
}
