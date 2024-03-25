package iu.crypt;

import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Objects;

import edu.iu.IuCrypt;
import edu.iu.IuException;
import edu.iu.crypt.PemEncoded;

/**
 * Common base class for JSON web security object builders.
 * 
 * @param <B> builder type
 */
abstract class CertificateReferenceBuilder<B extends CertificateReferenceBuilder<B>> {

	private URI certificateUri;
	private X509Certificate[] certificateChain;
	private byte[] certificateThumbprint;
	private byte[] certificateSha256Thumbprint;

	/**
	 * Next builder reference.
	 * 
	 * @return this
	 */
	abstract protected B next();

	/**
	 * Sets certificate URI
	 * 
	 * @param uri certificate URI
	 * @return this
	 */
	public B cert(URI uri) {
		acceptCertChain(PemEncoded.getCertificateChain(uri));
		this.certificateUri = uri;
		return next();
	}

	/**
	 * Sets certificate chain
	 * 
	 * @param chain certificate chain
	 * @return this
	 */
	public B cert(X509Certificate... chain) {
		acceptCertChain(chain);
		this.certificateChain = chain;
		return next();
	}

	/**
	 * Sets certificate thumbprint
	 * 
	 * @param certificateThumbprint cetiticate thumbprint
	 * @return certificate thumbprint
	 */
	public B x5t(byte[] certificateThumbprint) {
		Objects.requireNonNull(certificateThumbprint);

		final var cert = getCert();
		if (cert != null //
				&& !Arrays.equals(certificateThumbprint, IuCrypt.sha1(IuException.unchecked(cert::getEncoded))))
			throw new IllegalArgumentException("SHA-1 thumbprint mismatch");

		this.certificateThumbprint = certificateThumbprint;
		return next();
	}

	/**
	 * Sets certificate SHA-256 thumbprint
	 * 
	 * @param certificateSha256Thumbprint certificate SHA-256 thumbprint
	 * @return this
	 */
	public B x5t256(byte[] certificateSha256Thumbprint) {
		Objects.requireNonNull(certificateSha256Thumbprint);

		final var cert = getCert();
		if (cert != null //
				&& !Arrays.equals(certificateSha256Thumbprint, IuCrypt.sha256(IuException.unchecked(cert::getEncoded))))
			throw new IllegalArgumentException("SHA-256 thumbprint mismatch");

		this.certificateSha256Thumbprint = certificateSha256Thumbprint;
		return next();
	}

	/**
	 * Verifies set-once behavior and matches thumbprints against encoded checksums.
	 * 
	 * <p>
	 * Does not set the certificate chain.
	 * </p>
	 * 
	 * @param certChain certificate chain
	 */
	protected void acceptCertChain(X509Certificate[] certChain) {
		if (this.certificateChain != null //
				&& !Arrays.equals(certChain, this.certificateChain))
			throw new IllegalStateException("Certificate chain mismatch");

		final var cert = certChain[0];
		if (certificateThumbprint != null //
				&& !Arrays.equals(certificateThumbprint, IuCrypt.sha1(IuException.unchecked(cert::getEncoded))))
			throw new IllegalArgumentException("SHA-1 thumbprint mismatch");
		if (certificateSha256Thumbprint != null //
				&& !Arrays.equals(certificateSha256Thumbprint, IuCrypt.sha256(IuException.unchecked(cert::getEncoded))))
			throw new IllegalArgumentException("SHA-256 thumbprint mismatch");
	}

	/**
	 * Gets certificate URI
	 * 
	 * @return certificate URI
	 */
	URI certificateUri() {
		return certificateUri;
	}

	/**
	 * Gets certificate chain
	 * 
	 * @return certificate chain
	 */
	X509Certificate[] certificateChain() {
		return certificateChain;
	}

	/**
	 * Gets certificate thumbprint
	 * 
	 * @return certificate thumbprint
	 */
	byte[] certificateThumbprint() {
		return certificateThumbprint;
	}

	/**
	 * Gets certificate SHA-256 thumbprint
	 * 
	 * @return certificate SHA-256 thumbprint
	 */
	byte[] certificateSha256Thumbprint() {
		return certificateSha256Thumbprint;
	}

	private X509Certificate getCert() {
		if (this.certificateChain != null)
			return certificateChain[0];
		else if (this.certificateUri != null)
			return PemEncoded.getCertificateChain(certificateUri)[0];
		else
			return null;
	}

}
