package iu.auth.config;

import java.util.Set;

import edu.iu.IuIterable;
import edu.iu.auth.config.IuOpenIdProviderMetadata;
import edu.iu.auth.config.IuPrivateKeyPrincipal;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Use;

/**
 * Provides public key metadata for an OIDC provider token issuer.
 */
public class OidcTokenIssuer implements IuPrivateKeyPrincipal {

	private final IuOpenIdProviderMetadata oidcProvider;

	/**
	 * Constructor.
	 * 
	 * @param oidcProvider OIDC provider metadata
	 */
	public OidcTokenIssuer(IuOpenIdProviderMetadata oidcProvider) {
		this.oidcProvider = oidcProvider;
	}

	private <T> T firstOrNull(Set<T> s) {
		if (s == null)
			return null;
		return s.stream().findFirst().orElse(null);
	}

	@Override
	public Algorithm getAlg() {
		return firstOrNull(oidcProvider.getIdTokenSigningAlgValuesSupported());
	}

	@Override
	public Algorithm getEncryptAlg() {
		return firstOrNull(oidcProvider.getIdTokenEncryptionAlgValuesSupported());
	}

	@Override
	public Encryption getEnc() {
		return firstOrNull(oidcProvider.getIdTokenEncryptionEncValuesSupported());
	}

	@Override
	public WebKey getJwk() {
		return IuIterable.filter(WebKey.readJwks(oidcProvider.getJwksUri()), jwk -> Use.SIGN.equals(jwk.getUse()))
				.iterator().next();
	}

}
