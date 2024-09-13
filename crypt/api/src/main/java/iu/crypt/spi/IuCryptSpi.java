package iu.crypt.spi;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.security.cert.X509Certificate;

import edu.iu.crypt.PemEncoded;
import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebSignature;
import edu.iu.crypt.WebSignedPayload;

/**
 * Defines methods to be provided by the implementation module.
 */
public interface IuCryptSpi {

	/**
	 * Implements {@link PemEncoded#getCertificateChain(URI)}.
	 * 
	 * @param uri {@link URI}
	 * @return Array of {@link X509Certificate}
	 */
	X509Certificate[] getCertificateChain(URI uri);

	/**
	 * Gets a {@link WebKey.Builder} instance.
	 * 
	 * @param type {@link WebKey.Type}
	 * @return {@link WebKey.Builder}
	 */
	WebKey.Builder<?> getJwkBuilder(WebKey.Type type);

	/**
	 * Implements {@link WebKey#parse(String)}
	 * 
	 * @param jwk Serialized {@link WebKey}
	 * @return {@link WebKey}
	 */
	WebKey parseJwk(String jwk);

	/**
	 * Implements {@link WebKey#parseJwks(String)}
	 * 
	 * @param jwks Serialized {@link WebKey} set
	 * @return {@link Iterable} of {@link WebKey}
	 */
	Iterable<? extends WebKey> parseJwks(String jwks);

	/**
	 * Implements {@link WebKey#readJwks(URI)}
	 * 
	 * @param jwks {@link WebKey} set {@link URI}
	 * @return {@link Iterable} of {@link WebKey}
	 */
	Iterable<? extends WebKey> readJwks(URI jwks);

	/**
	 * Implements {@link WebKey#readJwks(InputStream)}
	 * 
	 * @param jwks {@link InputStream}
	 * @return {@link Iterable} of {@link WebKey}
	 */
	Iterable<? extends WebKey> readJwks(InputStream jwks);

	/**
	 * Implements {@link WebKey#asJwks(Iterable)}
	 * 
	 * @param webKeys {@link Iterable} of {@link WebKey}
	 * @return Serialized {@link WebKey} set
	 */
	String asJwks(Iterable<? extends WebKey> webKeys);

	/**
	 * Implements {@link WebKey#writeJwks(Iterable, OutputStream)}
	 * 
	 * @param webKeys {@link Iterable} of {@link WebKey}
	 * @param out     {@link OutputStream}
	 */
	void writeJwks(Iterable<? extends WebKey> webKeys, OutputStream out);

	/**
	 * Implements {@link WebSignature#builder(Algorithm)}
	 * 
	 * @param algorithm {@link Algorithm}
	 * @return {@link WebSignature.Builder}
	 */
	WebSignature.Builder<?> getJwsBuilder(Algorithm algorithm);

	/**
	 * Implements {@link WebSignedPayload#parse(String)}
	 * 
	 * @param jws Serialized {@link WebSignedPayload}
	 * @return {@link WebSignedPayload}
	 */
	WebSignedPayload parseJws(String jws);

	/**
	 * Implements {@link WebEncryption#builder(Algorithm)}
	 * 
	 * @param encryption {@link Encryption}
	 * @param deflate    Deflate flag
	 * @return {@link WebEncryption.Builder}
	 */
	WebEncryption.Builder getJweBuilder(Encryption encryption, boolean deflate);

	/**
	 * Implements {@link WebEncryption#parse(String)}
	 * 
	 * @param jwe Serialized {@link WebEncryption}
	 * @return {@link WebEncryption}
	 */
	WebEncryption parseJwe(String jwe);

}
