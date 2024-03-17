package iu.crypt;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAMultiPrimePrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;

import edu.iu.IuCacheMap;
import edu.iu.IuCrypt;
import edu.iu.IuException;
import edu.iu.client.IuHttp;
import edu.iu.crypt.WebKey;

/**
 * Adds basic object behavior to {@link WebKey}.
 */
public class BaseWebKey implements WebKey {

	private static Map<URI, Iterable<WebKey>> JWKS_CACHE = new IuCacheMap<>(Duration.ofMinutes(15L));
	private static Map<URI, X509Certificate[]> CERT_CACHE = new IuCacheMap<>(Duration.ofMinutes(15L));

	/**
	 * Gets well known key set by URI.
	 * 
	 * @param jwks  Well-known key set URI
	 * @param keyId Key ID
	 * @return {@link WebKey}
	 */
	public static Iterable<WebKey> readJwks(URI jwks) {
		return IuException.unchecked(() -> IuHttp.get(jwks, IuHttp.validate(Jwk::readJwks, IuHttp.OK)));
	}

	/**
	 * Gets web key from a well known key set by URI and id.
	 * 
	 * @param uri   Well-known key set URI
	 * @param keyId Key ID
	 * @return {@link WebKey}
	 */
	public static WebKey readJwk(URI uri, String keyId) {
		var jwks = JWKS_CACHE.get(uri);
		if (jwks == null)
			JWKS_CACHE.put(uri, jwks = WebKey.readJwks(uri));

		for (final var jwk : jwks)
			if (keyId.equals(jwk.getId()))
				return jwk;

		throw new IllegalArgumentException();
	}

	/**
	 * Gets a certificate chain by URI.
	 * 
	 * @param uri Well-known certificate chain URI.
	 * @return certificate chain
	 */
	public static X509Certificate[] getCertificateChain(URI uri) {
		var chain = CERT_CACHE.get(uri);
		if (chain == null)
			CERT_CACHE.put(uri, chain = IuException.unchecked(() -> {
				final var certFactory = CertificateFactory.getInstance("X.509");
				final Queue<X509Certificate> c = new ArrayDeque<>();
				final var pem = IuHttp.get(uri, IuHttp.validate(PemEncoded::parse, IuHttp.OK));
				while (pem.hasNext()) {
					final var n = pem.next();
					if (!PemEncoded.KeyType.CERTIFICATE.equals(n.keyType))
						throw new IllegalArgumentException();
					c.offer((X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(n.encoded)));
				}
				return c.toArray(new X509Certificate[c.size()]);
			}));
		return chain;
	}

	/**
	 * Creates a web key for a locally managed certificate.
	 * 
	 * @param id         key ID
	 * @param algorithm  algorithm
	 * @param type       key type
	 * @param use        public key use
	 * @param privateKey private key
	 * @param certChain  certificate chain, must include at least one certificate
	 *                   with public key related to the given private key
	 * @return {@link WebKey}
	 */
	public static WebKey from(String id, Algorithm algorithm, Type type, Use use, PrivateKey privateKey,
			X509Certificate[] certChain) {
		return new BaseWebKey() {
			@Override
			public String getId() {
				return id;
			}

			@Override
			public Algorithm getAlgorithm() {
				return algorithm;
			}

			@Override
			public Type getType() {
				return type;
			}

			@Override
			public Use getUse() {
				return use;
			}

			@Override
			public PrivateKey getPrivateKey() {
				return privateKey;
			}

			@Override
			public X509Certificate[] getCertificateChain() {
				return certChain;
			}

			@Override
			public byte[] getCertificateThumbprint() {
				return IuCrypt.sha1(IuException.unchecked(certChain[0]::getEncoded));
			}

			@Override
			public byte[] getCertificateSha256Thumbprint() {
				return IuCrypt.sha256(IuException.unchecked(certChain[0]::getEncoded));
			}
		};
	}

	/**
	 * Creates a web key from a public/private key pair.
	 * 
	 * @param id        key ID
	 * @param algorithm algorithm
	 * @param type      key type
	 * @param use       public key use
	 * @param keyPair   public/private key pair
	 * @return {@link WebKey}
	 */
	public static WebKey from(String id, Algorithm algorithm, Type type, Use use, KeyPair keyPair) {
		return new BaseWebKey() {
			@Override
			public String getId() {
				return id;
			}

			@Override
			public Algorithm getAlgorithm() {
				return algorithm;
			}

			@Override
			public Type getType() {
				return type;
			}

			@Override
			public Use getUse() {
				return use;
			}

			@Override
			public PrivateKey getPrivateKey() {
				return keyPair.getPrivate();
			}

			@Override
			public PublicKey getPublicKey() {
				return keyPair.getPublic();
			}
		};
	}

	/**
	 * Creates a web key from PEM-encoded key and/or certificate data.
	 * 
	 * @param id         key ID
	 * @param algorithm  algorithm
	 * @param type       key type
	 * @param use        public key use
	 * @param pemEncoded PEM-encoded key and/or certificate data, may be
	 *                   concatenated
	 * @return {@link WebKey}
	 */
	public static WebKey from(String id, Algorithm algorithm, Type type, Use use, String pemEncoded) {
		return IuException.unchecked(() -> {
			final var certFactory = CertificateFactory.getInstance("X.509");
			final KeyFactory keyFactory;
			switch (type) {
			case EC_P256:
			case EC_P384:
			case EC_P521:
				keyFactory = KeyFactory.getInstance("EC");
				break;

			case RSA:
				keyFactory = KeyFactory.getInstance("RSA");
				break;

			case RSASSA_PSS:
				keyFactory = KeyFactory.getInstance("RSASSA-PSS");
				break;

			default:
				throw new IllegalArgumentException();
			}

			final var pemIterator = PemEncoded.parse(pemEncoded);

			PrivateKey privateKey = null;
			PublicKey publicKey = null;
			final Queue<X509Certificate> certificateChain = new ArrayDeque<>();
			while (pemIterator.hasNext()) {
				final var segment = pemIterator.next();
				switch (segment.keyType) {
				case PUBLIC_KEY:
					if (publicKey != null)
						throw new IllegalArgumentException();
					else
						publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(segment.encoded));
					break;

				case PRIVATE_KEY:
					if (privateKey != null)
						throw new IllegalArgumentException();
					else
						privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(segment.encoded));
					break;

				default:
					certificateChain.offer((X509Certificate) certFactory
							.generateCertificate(new ByteArrayInputStream(segment.encoded)));
				}
			}

			if (publicKey == null)
				if (privateKey instanceof RSAPrivateCrtKey) {
					final var crt = (RSAPrivateCrtKey) privateKey;
					publicKey = keyFactory
							.generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
				} else if (privateKey instanceof RSAMultiPrimePrivateCrtKeySpec) {
					final var crt = (RSAMultiPrimePrivateCrtKeySpec) privateKey;
					publicKey = keyFactory
							.generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
				} else if (certificateChain.isEmpty())
					throw new IllegalArgumentException();
				else
					publicKey = certificateChain.iterator().next().getPublicKey();

			if (!certificateChain.isEmpty() && !publicKey.equals(certificateChain.iterator().next().getPublicKey()))
				throw new IllegalArgumentException();

			if ((privateKey instanceof RSAPrivateKey) //
					&& !((RSAPrivateKey) privateKey).getModulus().equals(((RSAPublicKey) publicKey).getModulus()))
				throw new IllegalArgumentException();

			if ((privateKey instanceof ECPrivateKey) //
					&& !((ECPrivateKey) privateKey).getParams().equals(((ECPublicKey) publicKey).getParams()))
				throw new IllegalArgumentException();

			final var pub = publicKey;
			final var priv = privateKey;
			return new BaseWebKey() {
				@Override
				public String getId() {
					return id;
				}

				@Override
				public Algorithm getAlgorithm() {
					return algorithm;
				}

				@Override
				public Type getType() {
					return type;
				}

				@Override
				public Use getUse() {
					return use;
				}

				@Override
				public PrivateKey getPrivateKey() {
					return priv;
				}

				@Override
				public PublicKey getPublicKey() {
					return pub;
				}

				@Override
				public X509Certificate[] getCertificateChain() {
					if (certificateChain.isEmpty())
						return null;
					else
						return certificateChain.toArray(new X509Certificate[certificateChain.size()]);
				}

				@Override
				public byte[] getCertificateThumbprint() {
					if (certificateChain.isEmpty())
						return null;
					else
						return IuCrypt.sha1(IuException.unchecked(certificateChain.iterator().next()::getEncoded));
				}

				@Override
				public byte[] getCertificateSha256Thumbprint() {
					if (certificateChain.isEmpty())
						return null;
					else
						return IuCrypt.sha256(IuException.unchecked(certificateChain.iterator().next()::getEncoded));
				}
			};
		});
	}

	@Override
	public String toString() {
		return Jwk.asJwk(this);
	}

}
