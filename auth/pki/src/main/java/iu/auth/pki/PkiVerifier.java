package iu.auth.pki;

import java.security.cert.CertPathValidator;
import java.security.cert.CertificateFactory;
import java.util.List;
import java.util.Objects;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.crypt.WebKey;
import iu.auth.principal.PrincipalVerifier;

/**
 * Verifies {@link PkiPrincipal} identities.
 */
final class PkiVerifier implements PrincipalVerifier<PkiPrincipal> {
	private final boolean authoritative;
	private final String realm;

	/**
	 * Constructor.
	 * 
	 * @param authoritative authoritative flag, indicates private key possession
	 * @param realm         authentication realm
	 */
	PkiVerifier(boolean authoritative, String realm) {
		this.authoritative = authoritative;
		this.realm = realm;
	}

	@Override
	public Class<PkiPrincipal> getType() {
		return PkiPrincipal.class;
	}

	@Override
	public String getRealm() {
		return realm;
	}

	@Override
	public boolean isAuthoritative() {
		return authoritative;
	}

	@Override
	public void verify(PkiPrincipal pki, String realm) throws IuAuthenticationException {
		final var sub = pki.getSubject();
		final var wellKnown = sub.getPublicCredentials(WebKey.class).iterator().next();

		IuException.unchecked(() -> {
			final var certPath = CertificateFactory.getInstance("X.509")
					.generateCertPath(List.of(wellKnown.getCertificateChain()));
			CertPathValidator.getInstance("PKIX").validate(certPath, PkiSpi.getPKIXParameters(realm));
		});

		if (authoritative) {
			final var privIter = sub.getPrivateCredentials(WebKey.class).iterator();
			if (!privIter.hasNext())
				throw new IllegalArgumentException("missing private key");

			final var key = privIter.next();
			Objects.requireNonNull(key.getPrivateKey(), "missing private key");
			IuObject.once(wellKnown, key.wellKnown());
		}
	}

}
