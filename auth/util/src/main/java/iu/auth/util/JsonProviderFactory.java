package iu.auth.util;

import jakarta.json.spi.JsonProvider;

/**
 * Holds an instance of {@link JsonProvider}.
 * <p>
 * This class <em>may</em> be intentionally initialized by the authentication
 * bootstrap with the desired {@link JsonProvider} SPI provided by the current
 * thread's context.
 * </p>
 */
public class JsonProviderFactory {

	/**
	 * Singleton instance of {@link JsonProvider}.
	 */
	public static final JsonProvider JSON;

	static {
		final var current = Thread.currentThread();
		final var contextToRestore = current.getContextClassLoader();
		try {
			current.setContextClassLoader(JsonProvider.class.getClassLoader());
			JSON = JsonProvider.provider();
		} finally {
			current.setContextClassLoader(contextToRestore);
		}
	}

	private JsonProviderFactory() {
	}
}
