package edu.iu.auth.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import edu.iu.IuObject;

/**
 * Authentication and authorization root configuration utility.
 */
public class AuthConfig {
	static {
		IuObject.assertNotOpen(AuthConfig.class);
	}

	private static final Map<String, IuAuthConfig> CONFIG = new HashMap<>();
	private static boolean sealed;

	/**
	 * Registers a configuration descriptor for an authentication realm.
	 * 
	 * <p>
	 * Only one verifier may be registered per realm
	 * </p>
	 * 
	 * @param config principal identity verifier
	 */
	public static synchronized void register(IuAuthConfig config) {
		if (sealed)
			throw new IllegalStateException("sealed");

		IuObject.assertNotOpen(config.getClass());
		IuObject.requireFinalImpl(config.getClass());

		final var realm = config.getRealm();
		if (CONFIG.containsKey(realm))
			throw new IllegalArgumentException("already configured");

		CONFIG.put(realm, config);
	}

	/**
	 * Gets the configuration registered for a realm.
	 * 
	 * @param <T>   configuration type
	 * @param realm authentication realm
	 * @return {@link IuAuthConfig} by realm
	 */
	@SuppressWarnings("unchecked")
	public static synchronized <T extends IuAuthConfig> T get(String realm) {
		if (sealed)
			return (T) Objects.requireNonNull(CONFIG.get(realm), "invalid realm");
		else
			throw new IllegalStateException("not sealed");
	}

	/**
	 * Finds configuration registered by interface.
	 * 
	 * @param <T>  configuration type
	 * @param type type
	 * @return {@link IuAuthConfig} by type
	 */
	public static synchronized <T extends IuAuthConfig> Iterable<T> get(Class<T> type) {
		if (sealed)
			return () -> CONFIG.values().stream().filter(a -> type.isInstance(a)).map(a -> type.cast(a)).iterator();
		else
			throw new IllegalStateException("not sealed");
	}

	/**
	 * Seals the authentication and authorization configuration.
	 * 
	 * <p>
	 * Until sealed, no per-realm configurations can be used. Once sealed, no new
	 * configurations can be registered. Configuration state is controlled by the
	 * auth module.
	 * </p>
	 */
	public static synchronized void seal() {
		sealed = true;
	}

	private AuthConfig() {
	}
}
