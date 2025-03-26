
package edu.iu.web.tomcat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.logging.Level;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.test.IuTestLogger;

public class IuTomcatContextTest {

	private IuTomcatContext context;

	@BeforeEach
	void setUp() {
		IuTestLogger.allow("org.apache.tomcat", Level.FINE);
		context = new IuTomcatContext("app", "env", "mod", "run", "comp", "preText", "url");
	}

	@Test
	void getApplication_returnsApplication() {
		assertEquals("app", context.getApplication());
	}

	@Test
	void getEnvironment_returnsEnvironment() {
		assertEquals("env", context.getEnvironment());
	}

	@Test
	void getModule_returnsModule() {
		assertEquals("mod", context.getModule());
	}

	@Test
	void getRuntime_returnsRuntime() {
		assertEquals("run", context.getRuntime());
	}

	@Test
	void getComponent_returnsComponent() {
		assertEquals("comp", context.getComponent());
	}

	@Test
	void getSupportPreText_returnsSupportPreText() {
		assertEquals("preText", context.getSupportPreText());
	}

	@Test
	void getSupportUrl_returnsSupportUrl() {
		assertEquals("url", context.getSupportUrl());
	}

	@Test
	void getSupportLabel_returnsNull() {
		assertNull(context.getSupportLabel());
	}

	@Test
	void getRootUri_returnsNull() {
		assertNull(context.getRootUri());
	}

	@Test
	void getOriginAllow_returnsNull() {
		assertNull(context.getOriginAllow());
	}

	@Test
	void getHandler_returnsNull() {
		assertNull(context.getHandler());
	}

	@Test
	void getLoader_returnsNull() {
		assertNull(context.getLoader());
	}

	@Test
	void emptyConstructor() {
		context = new IuTomcatContext();
		assertNull(context.getApplication());
		assertNull(context.getEnvironment());
		assertNull(context.getModule());
		assertNull(context.getRuntime());
		assertNull(context.getComponent());
		assertNull(context.getSupportPreText());
		assertNull(context.getSupportUrl());
	}
}
