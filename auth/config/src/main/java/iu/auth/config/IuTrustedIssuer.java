package iu.auth.config;

import java.security.cert.X509Certificate;

import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.config.IuPrivateKeyPrincipal;

/**
 * Represents a configured private key principal trusted as a token issuer.
 */
public interface IuTrustedIssuer extends IuAuthConfig {

	/**
	 * Gets a verifiable {@link IuPrincipalIdentity} that corresponds to a
	 * configured private key principal, if the private key was registered as
	 * trusted.
	 * 
	 * <p>
	 * If the private key is held locally by the incoming config, the principal
	 * returned by this method will verify as authoritative. If a
	 * {@link X509Certificate certificate} in the private key's well-known
	 * certificate chain is held, but not the private key itself, the principal
	 * returned will verify as non-authoritative.
	 * </p>
	 * 
	 * @param privateKeyPrincipal private key principal configuration
	 * @return Verifiable {@link IuPrincipalIdentity} if trusted; else null
	 */
	IuPrincipalIdentity getPrincipal(IuPrivateKeyPrincipal privateKeyPrincipal);

}
