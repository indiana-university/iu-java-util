package edu.iu.logging;

import edu.iu.UnsafeRunnable;

public interface IuLoggingContext {

	default String getAuthenticatedPrincipal() {
		return null;
	}

	default String getCalledUrl() {
		return null;
	}

	default String getRemoteAddr() {
		return null;
	}

	default String getReqNum() {
		return null;
	}

	default String getUserPrincipal() {
		return null;
	}

	static void bound(IuLoggingContext context, UnsafeRunnable task) {
		
	}
	
	static IuLoggingContext getCurrentContext() {
		return null;
	}
}
