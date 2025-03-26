
package edu.iu.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class WebResponseTest {

	private WebResponse webResponse;

	@BeforeEach
	void setUp() {
		webResponse = mock(WebResponse.class, CALLS_REAL_METHODS);
	}

	@Test
	void getUpgradeHandler_returnsNullForNormalHttp() {
		when(webResponse.getUpgradeHandler()).thenReturn(null);
		assertNull(webResponse.getUpgradeHandler());
	}

	@Test
	void getUpgradeHandler_returnsWebUpgradeHandler() {
		WebUpgradeHandler handler = mock(WebUpgradeHandler.class);
		when(webResponse.getUpgradeHandler()).thenReturn(handler);
		assertEquals(handler, webResponse.getUpgradeHandler());
	}
}
