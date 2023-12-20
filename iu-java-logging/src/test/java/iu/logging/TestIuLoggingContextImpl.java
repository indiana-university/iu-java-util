package iu.logging;

import edu.iu.logging.IuLoggingContext;

/**
 * Test implementation for IuLoggingContext.
 */
public class TestIuLoggingContextImpl implements IuLoggingContext {
	@Override
	public String getAuthenticatedPrincipal() {
		return "Test Authenticated Principal";
	}

	@Override
	public String getCalledUrl() {
		return "Test Called URL";
	}

	@Override
	public String getRemoteAddr() {
		return "Test Remote Address";
	}

	@Override
	public String getReqNum() {
		return "Test Request Number";
	}

	@Override
	public String getUserPrincipal() {
		return "Test User Principal";
	}
}
