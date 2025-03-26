package edu.iu.web;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class WebUpgradeHandlerTest {

	private final WebUpgradeHandler handler = new WebUpgradeHandler() {
		@Override
		public boolean isInternal() {
			return false;
		}

		@Override
		public void init(WebUpgradeConnection connection) {
		}

		@Override
		public void destroy() {
		}
	};

	@Test
	void upgradeDispatch_throwsUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> handler.upgradeDispatch("status"));
	}

	@Test
	void setSocketWrapper_throwsUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> handler.setSocketWrapper(null));
	}

	@Test
	void setSslSupport_throwsUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> handler.setSslSupport(null));
	}

	@Test
	void pause_throwsUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> handler.pause());
	}

	@Test
	void timeoutAsync_throwsUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> handler.timeoutAsync(0L));
	}
}
