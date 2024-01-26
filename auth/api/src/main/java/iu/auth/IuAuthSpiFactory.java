package iu.auth;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.WeakHashMap;

/**
 * Maintains singleton instances of SPI interfaces related to API authorization
 * and authentication.
 * 
 * <p>
 * SPI providers <em>must</em> be visible to the same class loader that loaded
 * the service interface.
 * </p>
 */
public class IuAuthSpiFactory {

	private static class Provider<P> {
		P instance;
	}

	private static final Map<Class<?>, Provider<?>> PROVIDERS = new WeakHashMap<>();

	/**
	 * Gets a singleton instance of a service interface.
	 * 
	 * @param <P>              service type
	 * @param serviceInterface service interface
	 * @return singleton instance
	 */
	@SuppressWarnings("unchecked")
	public static <P> P get(Class<P> serviceInterface) {
		final Provider<P> provider;
		synchronized (PROVIDERS) {
			if (PROVIDERS.containsKey(serviceInterface))
				provider = (Provider<P>) PROVIDERS.get(serviceInterface);
			else
				PROVIDERS.put(serviceInterface, provider = new Provider<>());
		}

		synchronized (provider) {
			if (provider.instance == null)
				provider.instance = ServiceLoader.load(serviceInterface, serviceInterface.getClassLoader()).findFirst()
						.get();
		}

		return provider.instance;
	}

	private IuAuthSpiFactory() {
	}

}
