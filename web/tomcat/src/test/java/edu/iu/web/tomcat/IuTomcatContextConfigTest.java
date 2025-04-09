
package edu.iu.web.tomcat;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuTomcatContextConfigTest {

	private IuTomcatContextConfig contextConfig;

	@BeforeEach
	void setUp() {
		contextConfig = new IuTomcatContextConfig();
	}

	@Test
	void applicationAnnotationsConfig_doesNotThrowException() {
		assertDoesNotThrow(() -> contextConfig.applicationAnnotationsConfig());
	}
}
