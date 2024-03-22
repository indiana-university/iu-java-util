package iu.crypt;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import edu.iu.IuCrypt;
import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.client.IuJson;
import edu.iu.crypt.WebCryptoHeader;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Use;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

/**
 * Provides {@link WebCryptoHeader} and {@link WebEncryptionHeader} processing
 * utilities.
 */
final class Jose implements WebCryptoHeader {

	/**
	 * Creates a JOSE header from a serialized protected header.
	 * 
	 * @param protectedHeader protected header data
	 * @return JOSE header
	 */
	static Jose from(JsonObject protectedHeader) {
		return new Jose(protectedHeader);
	}

	/**
	 * Creates a JOSE header from serialized headers.
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
					throw new IllegalArgumentException(n);
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

	private static X509Certificate[] decodeCertificateChain(JsonValue x5c) {
		final var certFactory = IuException.unchecked(() -> CertificateFactory.getInstance("X.509"));
		return x5c.asJsonArray().stream().map(IuJson::asText).map(EncodingUtils::base64)
				.map(a -> IuException.unchecked(
						() -> (X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(a))))
				.toArray(X509Certificate[]::new);
	}

	private final Algorithm algorithm;

	private final String keyId;
	private final Jwk key;
	private final boolean silent;
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
	 * Constructor.
	 * 
	 * @param builder provides source values
	 */
	Jose(JoseBuilder<?> builder) {
		algorithm = builder.algorithm();
		keyId = builder.id();
		silent = builder.silent();

		final var key = builder.key();
		if (key == null)
			this.key = null;
		else // __never__ include private/secret key in JOSE header
			this.key = key.wellKnown();
		
		keySetUri = builder.keySetUri();
		type = builder.type();
		contentType = builder.contentType();
		certificateUri = builder.certificateUri();
		certificateChain = builder.certificateChain();
		certificateThumbprint = builder.certificateThumbprint();
		certificateSha256Thumbprint = builder.certificateSha256Thumbprint();
		criticalExtendedParameters = Collections.unmodifiableSet(builder.crit());
		extendedParameters = Collections.unmodifiableMap(builder.ext());

		validate();
	}

	private Jose(JsonObject jose) {
		algorithm = Objects.requireNonNull(IuJson.text(jose, "alg", Algorithm::from));

		keyId = IuJson.text(jose, "kid");
		keySetUri = IuJson.text(jose, "jku", URI::create);
		key = IuJson.get(jose, "jwk", JwkBuilder::parse);
		silent = false;

		type = IuJson.text(jose, "typ");
		contentType = IuJson.text(jose, "cty");

		certificateUri = IuJson.text(jose, "x5u", URI::create);
		certificateThumbprint = IuJson.text(jose, "x5t", EncodingUtils::base64Url);
		certificateSha256Thumbprint = IuJson.text(jose, "x5t#S256", EncodingUtils::base64Url);
		certificateChain = IuJson.get(jose, "x5c", v -> decodeCertificateChain(v.asJsonArray()));

		final Map<String, Object> params = new LinkedHashMap<>();
		for (final var e : jose.entrySet()) {
			final var paramName = e.getKey();
			final var param = Param.from(paramName);
			if (param == null || !param.isUsedFor(Use.SIGN))
				// Encryption-only parameters not used for JWS signature are treated as
				// extended parameters
				params.put(e.getKey(), IuJson.toJava(e.getValue()));
		}
		extendedParameters = Collections.unmodifiableMap(params);
		criticalExtendedParameters = IuJson.get(jose, "crit", Collections.emptySet(),
				v -> v.asJsonArray().stream().map(IuJson::asText).collect(Collectors.toUnmodifiableSet()));

		validate();
	}

	private void validate() {
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

		if (!criticalExtendedParameters.isEmpty()) {
			switch (algorithm) {
			case A128GCMKW:
			case A192GCMKW:
			case A256GCMKW:
				if (!criticalExtendedParameters.equals(Set.of("iv", "tag")))
					throw new IllegalArgumentException("not understood " + criticalExtendedParameters);
				break;

			case ECDH_ES:
			case ECDH_ES_A128KW:
			case ECDH_ES_A192KW:
			case ECDH_ES_A256KW:
				if (!criticalExtendedParameters.equals(Set.of("epk")) //
						&& !criticalExtendedParameters.equals(Set.of("epk", "apu", "apv")))
					throw new IllegalArgumentException("not understood " + criticalExtendedParameters);
				break;

			default:
				throw new IllegalArgumentException("not understood " + criticalExtendedParameters);
			}
			// TODO: implement PBES2, JWT
		}
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
	public Jwk getKey() {
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
			if ((p == null //
					|| p.test(param.name)) //
					&& param.isPresent(this))
				switch (param) {
				case ALGORITHM:
					b.add(param.name, algorithm.alg);
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
				case KEY:
					if (!silent) {
						final var jwkb = IuJson.object();
						key.wellKnown().serializeTo(jwkb);
						b.add(param.name, jwkb);
					}
					break;

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

				case CRITICAL_PARAMS:
					if (!criticalExtendedParameters.isEmpty()) {
						final var critb = IuJson.array();
						criticalExtendedParameters.forEach(critb::add);
						b.add(param.name, critb);
						break;
					}

				default:
					// non-JWS params handled as extended
					break;
				}

		if (extendedParameters != null)
			for (final var e : extendedParameters.entrySet())
				IuJson.add(b, p, e.getKey(), e::getValue);

		return b.build();
	}

}
