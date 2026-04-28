package iu.oidc.client.config;

import java.net.URI;
import java.time.Duration;

import edu.iu.oidc.IuOidcProviderMetadata;

/**
 * Client view of an OIDC provider.
 */
public interface IuOidcProvider {

	/**
	 * Gets configured metadata.
	 * 
	 * @return {@link IuOidcProviderMetadata}; null to use other properties to pull
	 *         from a well-known self-published configuration source.
	 */
	IuOidcProviderMetadata getMetadata();

	/**
	 * Gets the issuer URI.
	 * 
	 * @return issuer URI; null when providing {@link #getMetadata() configured
	 *         metadata}
	 */
	URI getIssuer();

	/**
	 * Gets the metadata configuration URI.
	 * 
	 * @return metadata configuration URI; null when providing {@link #getMetadata()
	 *         configured metadata}
	 */
	default URI getMetadataUri() {
		final var issuer = getIssuer();
		if (issuer == null)
			return null;
		else
			return URI.create(issuer + "/.well-known/openid-configuration");
	}

	/**
	 * Gets the metadata refresh interval.
	 * 
	 * @return {@link Duration}; null when providing {@link #getMetadata()
	 *         configured metadata}
	 */
	default Duration getMetadataTtl() {
		if (getMetadata() != null)
			return null;
		else
			return Duration.ofMinutes(15L);
	}

}
