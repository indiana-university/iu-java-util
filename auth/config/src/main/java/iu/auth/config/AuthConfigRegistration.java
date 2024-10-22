package iu.auth.config;

import java.net.URI;
import java.util.function.Consumer;
import java.util.function.Function;

import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuVault;

/**
 * Provides storage and type translation metadata for an interface involved in
 * configuring authentication and/or authorization.
 * 
 * @param <T> Configuration interface
 */
public final class AuthConfigRegistration<T> {

	/**
	 * Builder for {@link AuthConfigRegistration}.
	 * 
	 * @param <T> Configuration interface.
	 */
	public static final class Builder<T> {
		private final Class<T> configInterface;
		private String prefix;
		private Consumer<? super T> verifier;
		private Function<URI, T> uriResolver;
		private IuJsonAdapter<T> adapter;
		private IuVault[] vault;

		/**
		 * Constructor.
		 * 
		 * @param configInterface
		 */
		private Builder(Class<T> configInterface) {
			this.configInterface = configInterface;
		}

		/**
		 * Sets the configuration prefix.
		 * 
		 * @param prefix
		 * @return this
		 */
		public Builder<T> prefix(String prefix) {
			this.prefix = prefix;
			return this;
		}

		/**
		 * Sets the configuration verifier.
		 * 
		 * @param verifier
		 * @return this
		 */
		public Builder<T> verifier(Consumer<? super T> verifier) {
			this.verifier = verifier;
			return this;
		}

		/**
		 * Sets the URI resolver.
		 * 
		 * @param uriResolver
		 * @return this
		 */
		public Builder<T> uriResolver(Function<URI, T> uriResolver) {
			this.uriResolver = uriResolver;
			return this;
		}

		/**
		 * Sets the JSON adapter.
		 * 
		 * @param adapter
		 * @return this
		 */
		public Builder<T> adapter(IuJsonAdapter<T> adapter) {
			this.adapter = adapter;
			return this;
		}

		/**
		 * Sets the vault.
		 * 
		 * @param vault
		 * @return this
		 */
		public Builder<T> vault(IuVault... vault) {
			this.vault = vault;
			return this;
		}

		/**
		 * Builds the registration.
		 * 
		 * @return registration
		 */
		public AuthConfigRegistration<T> build() {
			return new AuthConfigRegistration<>(this);
		}
	}

	/** Configuration interface */
	final Class<T> configInterface;

	/** Configuration prefix */
	final String prefix;

	/** Configuration verifier */
	final Consumer<? super T> verifier;

	/** URI resolver */
	final Function<URI, T> uriResolver;

	/** JSON adapter */
	final IuJsonAdapter<T> adapter;

	/** Vault */
	final IuVault[] vault;

	private AuthConfigRegistration(Builder<T> builder) {
		configInterface = builder.configInterface;
		prefix = builder.prefix;
		verifier = builder.verifier;
		uriResolver = builder.uriResolver;
		adapter = builder.adapter;
		vault = builder.vault;
	}

}
