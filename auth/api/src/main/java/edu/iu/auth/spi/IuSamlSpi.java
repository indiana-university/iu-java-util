package edu.iu.auth.spi;

import java.net.URI;
import java.util.function.Supplier;

import edu.iu.auth.saml.IuSamlSession;

/**
 * Application-facing SAML Service Provider (SP) SPI interface.
 */
public interface IuSamlSpi {

	/**
	 * Implements {@link IuSamlSession#create(URI, URI, Supplier)}.
	 * 
	 * @param entryPointUri application entry point URI
	 * @param postUri       HTTP POST Binding URI
	 * @param secretKey     Secret key supplier
	 * @return {@link IuSamlSession}
	 */
	IuSamlSession createSession(URI entryPointUri, URI postUri, Supplier<byte[]> secretKey);

	/**
	 * Implements {@link IuSamlSession#activate(String, Supplier)}.
	 * 
	 * @param sessionToken Tokenized session
	 * @param secretKey    Secret key supplier
	 * @return {@link IuSamlSession}
	 */
	IuSamlSession activateSession(String sessionToken, Supplier<byte[]> secretKey);

}
