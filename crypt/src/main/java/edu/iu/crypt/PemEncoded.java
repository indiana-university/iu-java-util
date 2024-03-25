package edu.iu.crypt;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Queue;

import edu.iu.IuCacheMap;
import edu.iu.IuException;
import edu.iu.IuStream;
import edu.iu.IuText;
import edu.iu.client.IuHttp;
import edu.iu.crypt.WebKey.Type;

/**
 * Reads PEM-encoded key and/or certificate data.
 */
public final class PemEncoded {

	private static Map<URI, X509Certificate[]> CERT_CACHE = new IuCacheMap<>(Duration.ofMinutes(15L));

	/**
	 * Enumerates encoded key type.
	 */
	public enum KeyType {
		/**
		 * Private key.
		 */
		PRIVATE_KEY,

		/**
		 * Public key.
		 */
		PUBLIC_KEY,

		/**
		 * X509 certificate.
		 */
		CERTIFICATE;
	}

	/**
	 * Reads PEM-encoded key and/or certificate data.
	 * 
	 * @param in input stream of PEM-encoded key and/or certificate data, multiple
	 *           entries may be concatenated
	 * @return Parsed PEM-encoded data
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc4945#section-6.1">RFC-4945
	 *      Section 6.1</a>
	 */
	public static Iterator<PemEncoded> parse(InputStream in) {
		return IuException.unchecked(() -> parse(IuText.utf8(IuStream.read(in))));
	}

	/**
	 * Parses PEM-encoded key and/or certificate data.
	 * 
	 * @param pemEncoded PEM-encoded key and/or certificate data, may be
	 *                   concatenated
	 * @return Parsed PEM-encoded data
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc4945#section-6.1">RFC-4945
	 *      Section 6.1</a>
	 */
	public static Iterator<PemEncoded> parse(String pemEncoded) {
		final var length = pemEncoded.length();
		return new Iterator<PemEncoded>() {
			private int start = 0;
			private int end = -1;

			@Override
			public boolean hasNext() {
				if (end < start) {
					// * 11 chars: "-----BEGIN "
					// * 10-11 chars: key type
					// * 5 chars: "-----"
					// => 27 chars
					if (start + 27 > length)
						return false;
					else if (!"-----BEGIN ".equals(pemEncoded.substring(start, start + 11)))
						end = pemEncoded.length();
					else {
						start += 11;
						final var endOfKeyType = pemEncoded.indexOf("-----", start);
						final var keyType = pemEncoded.substring(start, endOfKeyType);
						start += keyType.length() + 5;

						int endOfKey = pemEncoded.indexOf("-----END " + keyType + "-----", start);
						if (endOfKey == -1)
							end = length;
						else
							end = endOfKey;
					}
				}

				return true;
			}

			@Override
			public PemEncoded next() {
				if (!hasNext())
					throw new NoSuchElementException();

				final var sb = new StringBuilder(pemEncoded.substring(start, end));
				for (var i = 0; i < sb.length(); i++)
					if (Character.isWhitespace(sb.charAt(i)))
						sb.deleteCharAt(i--);

				final KeyType keyType;
				try {
					if (start < 26)
						keyType = KeyType.CERTIFICATE;
					else {
						keyType = KeyType.valueOf(
								pemEncoded.substring(pemEncoded.lastIndexOf("-----BEGIN ", start) + 11, start - 5)
										.replace(' ', '_'));
						if (end >= length)
							throw new IllegalArgumentException(
									"Missing -----END " + keyType.name().replace('_', ' ') + "-----");
					}
				} finally {
					final var nextStart = pemEncoded.indexOf("-----BEGIN ", end);
					if (nextStart == -1)
						start = length + 1;
					else
						start = nextStart;
				}

				return new PemEncoded(keyType, Base64.getDecoder().decode(sb.toString()));
			}
		};
	}

	/**
	 * Reads a certificate chain from a URI.
	 * 
	 * @param uri {@link URI}
	 * @return certificate chain
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc4945#section-6.1">RFC-4945 PKI
	 *      Section 6.1</a>
	 */
	public static X509Certificate[] getCertificateChain(URI uri) {
		var chain = CERT_CACHE.get(uri);
		if (chain == null)
			CERT_CACHE.put(uri, chain = getCertificateChain(
					IuException.unchecked(() -> IuHttp.get(uri, IuHttp.validate(PemEncoded::parse, IuHttp.OK)))));
		return chain;
	}

	/**
	 * Converts parsed PEM data to a certificate chain.
	 * 
	 * @param json array of PEM-encoded 
	 * @return certificate chain
	 */
	public static X509Certificate[] certChainFromJson(JsonValue json) {

	}

	/**
	 * Converts parsed PEM data to a certificate chain.
	 * 
	 * @param pem PEM encoded certificate chain
	 * @return certificate chain
	 */
	public static X509Certificate[] getCertificateChain(Iterator<PemEncoded> pem) {
		return IuException.unchecked(() -> {
			final var certFactory = CertificateFactory.getInstance("X.509");
			final Queue<X509Certificate> c = new ArrayDeque<>();
			while (pem.hasNext()) {
				final var n = pem.next();
				if (!PemEncoded.KeyType.CERTIFICATE.equals(n.keyType))
					throw new IllegalArgumentException();
				c.offer((X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(n.encoded)));
			}
			return c.toArray(new X509Certificate[c.size()]);
		});
	}

	private final KeyType keyType;
	private final byte[] encoded;

	/**
	 * Gets the key type.
	 * 
	 * @return {@link KeyType}
	 */
	public KeyType getKeyType() {
		return keyType;
	}

	/**
	 * Gets the key as a public key when {@link #keyType} is
	 * {@link KeyType#PUBLIC_KEY}.
	 * 
	 * @param type JWK key type
	 * @return public key
	 */
	public PublicKey asPublic(Type type) {
		if (!keyType.equals(KeyType.PUBLIC_KEY))
			throw new IllegalStateException();
		return IuException
				.unchecked(() -> KeyFactory.getInstance(type.kty).generatePublic(new X509EncodedKeySpec(encoded)));
	}

	/**
	 * Gets the key as a private key when {@link #keyType} is
	 * {@link KeyType#PRIVATE_KEY}.
	 * 
	 * @param type JWK key type
	 * @return private key
	 */
	public PrivateKey asPrivate(Type type) {
		if (!keyType.equals(KeyType.PRIVATE_KEY))
			throw new IllegalStateException();
		return IuException
				.unchecked(() -> KeyFactory.getInstance(type.kty).generatePrivate(new PKCS8EncodedKeySpec(encoded)));
	}

	/**
	 * Gets the certificate when {@link #keyType} is {@link KeyType#CERTIFICATE}.
	 * 
	 * @param type JWK key type
	 * @return private key
	 */
	public X509Certificate asCertificate() {
		if (!keyType.equals(KeyType.CERTIFICATE))
			throw new IllegalStateException();
		return IuException.unchecked(() -> (X509Certificate) CertificateFactory.getInstance("X.509")
				.generateCertificate(new ByteArrayInputStream(encoded)));
	}

	private PemEncoded(KeyType keyType, byte[] encoded) {
		this.keyType = keyType;
		this.encoded = encoded;
	}

}
