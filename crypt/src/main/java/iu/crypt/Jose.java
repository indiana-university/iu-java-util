package iu.crypt;

import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import edu.iu.IuCrypt;
import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.client.IuJson;
import edu.iu.crypt.WebEncryptionHeader;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Use;
import edu.iu.crypt.WebSignatureHeader;
import jakarta.json.JsonObject;

/**
 * Provides {@link WebSignatureHeader} and {@link WebEncryptionHeader}
 * processing utilities.
 */
public final class Jose implements WebEncryptionHeader {

	private static final Set<String> STANDARD_PARAMS = EnumSet.allOf(Param.class).stream().map(a -> a.name)
			.collect(Collectors.toSet());

	/**
	 * Gets the key to use for signing or encryption.
	 * 
	 * @param jose header
	 * @return encryption or signing key
	 */
	public static WebKey getKey(WebSignatureHeader jose) {
		final var kid = jose.getKeyId();

		var key = jose.getKey();
		if (key != null)
			if (kid != null && !kid.equals(key.getId()))
				throw new IllegalArgumentException("kid");
			else
				return key;

		if (kid != null) {
			final var jwks = jose.getKeySetUri();
			if (jwks != null)
				return WebKey.readJwk(jose.getKeySetUri(), kid);
		}

		var cert = jose.getCertificateChain();
		if (cert == null) {
			final var certUri = jose.getCertificateUri();
			if (certUri != null)
				cert = WebKey.getCertificateChain(certUri);
		}
		if (cert != null) {
			final var t = jose.getCertificateThumbprint();
			if (t != null && !Arrays.equals(t, IuCrypt.sha1(IuException.unchecked(cert[0]::getEncoded))))
				throw new IllegalArgumentException();

			final var t2 = jose.getCertificateSha256Thumbprint();
			if (t2 != null && !Arrays.equals(t2, IuCrypt.sha256(IuException.unchecked(cert[0]::getEncoded))))
				throw new IllegalArgumentException();

			return WebKey.from(kid, jose.getAlgorithm(), cert);
		}

		return null;
	}

	/**
	 * Creates a JOSE header from a serialized protected header.
	 * 
	 * @param protectedHeader protected header data
	 * @return JOSE header
	 */
	static Jose from(JsonObject protectedHeader) {
		return from(protectedHeader, null, null);
	}

	/**
	 * Creates a JOSE header from a serialized protected header.
	 * 
	 * @param protectedHeader    protected header data
	 * @param sharedHeader       unprotected shared header data
	 * @param perRecipientHeader unprotected per-recipient header data
	 * @return JOSE header
	 */
	static Jose from(JsonObject protectedHeader, JsonObject sharedHeader, JsonObject perRecipientHeader) {
		if (sharedHeader == null && perRecipientHeader == null)
			return new Jose(protectedHeader);

		final var b = IuJson.object(protectedHeader);
		if (sharedHeader != null)
			sharedHeader.forEach((n, v) -> {
				if (protectedHeader.containsKey(n))
					throw new IllegalArgumentException();
				b.add(n, v);
			});
		if (perRecipientHeader != null)
			perRecipientHeader.forEach((n, v) -> {
				if (protectedHeader.containsKey(n) //
						|| (sharedHeader != null && sharedHeader.containsKey(n)))
					throw new IllegalArgumentException();
				b.add(n, v);
			});

		return new Jose(b.build());
	}

	private final Algorithm algorithm;
	private final Encryption encryption;
	private final boolean deflate;

	private final String keyId;
	private final WebKey key;
	private final URI keySetUri;

	private final String type;
	private final String contentType;

	private final URI certificateUri;
	private final X509Certificate[] certificateChain;
	private final byte[] certificateThumbprint;
	private final byte[] certificateSha256Thumbprint;

	private final Set<String> criticalExtendedParameters;
	private final Map<String, ?> extendedParameters;

	/**
	 * Copy/validate constructor.
	 * 
	 * @param header header to copy
	 */
	Jose(WebSignatureHeader header) {
		algorithm = header.getAlgorithm();
		if (header instanceof WebEncryptionHeader) {
			final var enc = (WebEncryptionHeader) header;
			encryption = enc.getEncryption();
			deflate = enc.isDeflate();
		} else {
			encryption = null;
			deflate = false;
		}

		keyId = header.getKeyId();
		key = header.getKey();
		keySetUri = header.getKeySetUri();

		type = header.getType();
		contentType = header.getContentType();

		certificateUri = header.getCertificateUri();
		certificateChain = header.getCertificateChain();
		certificateThumbprint = header.getCertificateThumbprint();
		certificateSha256Thumbprint = header.getCertificateSha256Thumbprint();

		final var ext = header.getExtendedParameters();
		if (ext != null)
			extendedParameters = Collections.unmodifiableMap(new LinkedHashMap<>(ext));
		else
			extendedParameters = Collections.emptyMap();

		final var crit = header.getCriticalExtendedParameters();
		if (crit != null)
			criticalExtendedParameters = Collections.unmodifiableSet(new LinkedHashSet<>(crit));
		else
			criticalExtendedParameters = Collections.emptySet();

		validate();
	}

	private Jose(JsonObject jose) {
		algorithm = Objects.requireNonNull(IuJson.text(jose, "alg", Algorithm::from));
		encryption = IuJson.text(jose, "enc", Encryption::from);
		deflate = "DEF".equals(IuJson.text(jose, "zip"));

		keyId = IuJson.text(jose, "kid");
		keySetUri = IuJson.text(jose, "jku", URI::create);
		key = IuJson.get(jose, "jwk", v -> new Jwk(v.asJsonObject()));

		type = IuJson.text(jose, "typ");
		contentType = IuJson.text(jose, "cty");

		certificateUri = IuJson.text(jose, "x5u", URI::create);
		certificateThumbprint = IuJson.text(jose, "x5t", EncodingUtils::base64Url);
		certificateSha256Thumbprint = IuJson.text(jose, "x5t#S256", EncodingUtils::base64Url);
		certificateChain = IuJson.get(jose, "x5c", v -> CertUtils.decodeCertificateChain(v.asJsonArray()));

		final Map<String, Object> params = new LinkedHashMap<>();
		for (final var e : jose.entrySet())
			if (!STANDARD_PARAMS.contains(e.getKey()))
				params.put(e.getKey(), IuJson.toJava(e.getValue()));
		extendedParameters = Collections.unmodifiableMap(params);
		criticalExtendedParameters = IuJson.get(jose, "crit", Collections.emptySet(),
				v -> v.asJsonArray().stream().map(IuJson::asText).collect(Collectors.toUnmodifiableSet()));

		validate();
	}

	private void validate() {
		if ((encryption != null || deflate) && algorithm.use.equals(Use.SIGN))
			throw new IllegalArgumentException();

		if (keyId != null && key != null && !keyId.equals(key.getId()))
			throw new IllegalArgumentException();

		if (certificateChain != null) {
			final var cert = certificateChain[0];
			if (key != null && !key.getPublicKey().equals(cert.getPublicKey()))
				throw new IllegalArgumentException();
			if (certificateThumbprint != null && !Arrays.equals(certificateThumbprint,
					IuException.unchecked(() -> IuCrypt.sha1(cert.getEncoded()))))
				throw new IllegalArgumentException();
			if (certificateSha256Thumbprint != null && !Arrays.equals(certificateSha256Thumbprint,
					IuException.unchecked(() -> IuCrypt.sha256(cert.getEncoded()))))
				throw new IllegalArgumentException();
		}

		if (!criticalExtendedParameters.isEmpty())
			// TODO: implement ECDH, PBES2, JWT
			throw new IllegalArgumentException("not understood " + criticalExtendedParameters);
	}

	@Override
	public Algorithm getAlgorithm() {
		return algorithm;
	}

	@Override
	public String getKeyId() {
		return keyId;
	}

	@Override
	public URI getKeySetUri() {
		return keySetUri;
	}

	@Override
	public WebKey getKey() {
		return key;
	}

	@Override
	public String getType() {
		return type;
	}

	@Override
	public String getContentType() {
		return contentType;
	}

	@Override
	public Set<String> getCriticalExtendedParameters() {
		return criticalExtendedParameters;
	}

	@Override
	public Map<String, ?> getExtendedParameters() {
		return extendedParameters;
	}

	@Override
	public URI getCertificateUri() {
		return certificateUri;
	}

	@Override
	public X509Certificate[] getCertificateChain() {
		return certificateChain;
	}

	@Override
	public byte[] getCertificateThumbprint() {
		return certificateThumbprint;
	}

	@Override
	public byte[] getCertificateSha256Thumbprint() {
		return certificateSha256Thumbprint;
	}

	@Override
	public Encryption getEncryption() {
		return encryption;
	}

	@Override
	public boolean isDeflate() {
		return deflate;
	}

	@Override
	public String toString() {
		return toJson(null).toString();
	}

	/**
	 * Gets the JOSE header as JSON.
	 * 
	 * @param p accepts standard param name and returns true to include the
	 *          parameter; else false
	 * @return {@link JsonObject}
	 */
	JsonObject toJson(Predicate<String> p) {
		final var b = IuJson.object();
		for (final var param : IuIterable.iter(Param.values()))
			if ((p == null || p.test(param.name)) && param.isPresent(this))
				switch (param) {
				case ALGORITHM:
					b.add(param.name, algorithm.alg);
					break;
				case ENCRYPTION:
					b.add(param.name, encryption.enc);
					break;
				case DEFALATE:
					b.add(param.name, "DEF");
					break;

				case TYPE:
					b.add(param.name, type);
					break;
				case CONTENT_TYPE:
					b.add(param.name, contentType);
					break;

				case KEY_ID:
					b.add(param.name, keyId);
					break;
				case KEY_SET_URI:
					b.add(param.name, keySetUri.toString());
					break;
				case KEY: {
					final var jwkb = IuJson.object();
					Jwk.writeJwk(jwkb, WebKey.wellKnown(key));
					b.add(param.name, jwkb);
					break;
				}

				case CERTIFICATE_URI:
					b.add(param.name, certificateUri.toString());
					break;
				case CERTIFICATE_CHAIN: {
					final var x5cb = IuJson.array();
					for (final var cert : certificateChain)
						x5cb.add(EncodingUtils.base64(IuException.unchecked(cert::getEncoded)));
					b.add(param.name, x5cb);
					break;
				}
				case CERTIFICATE_THUMBPRINT:
					b.add(param.name, EncodingUtils.base64Url(certificateThumbprint));
					break;
				case CERTIFICATE_SHA256_THUMBPRINT:
					b.add(param.name, EncodingUtils.base64Url(certificateSha256Thumbprint));
					break;

				case CRITICAL_PARAMS: {
					final var critb = IuJson.array();
					criticalExtendedParameters.forEach(critb::add);
					b.add(param.name, critb);
					break;
				}
				}

		if (extendedParameters != null)
			for (final var e : extendedParameters.entrySet())
				IuJson.add(b, p, e.getKey(), e::getValue);

		return b.build();
	}

}
