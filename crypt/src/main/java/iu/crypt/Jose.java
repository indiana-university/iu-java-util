package iu.crypt;

import java.util.Base64;
import java.util.Objects;

import edu.iu.IuException;
import edu.iu.crypt.WebEncryptionHeader;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebSignatureHeader;
import jakarta.json.JsonObject;

/**
 * Provides {@link WebSignatureHeader} and {@link WebEncryptionHeader}
 * processing utilities.
 */
final class Jose {

	/**
	 * Gets the key to use for signing or encryption.
	 * 
	 * @param jose header
	 * @return encryption or signing key
	 */
	static WebKey getKey(WebSignatureHeader jose) {
		final var kid = jose.getKeyId();

		var key = jose.getKey();
		if (key != null)
			if (kid != null && !kid.equals(key.getId()))
				throw new IllegalArgumentException("kid");
			else
				return key;

		return WebKey.readJwk(jose.getKeySetUri(), kid);
	}

	/**
	 * Gets the JOSE shared unprotected header as JSON.
	 * 
	 * @param header header
	 * @return {@link JsonObject}
	 */
	static JsonObject getShared(WebSignatureHeader header) {
		final var p = header.getProtectedParameters();
		final var b = JsonP.PROVIDER.createObjectBuilder();

		if (!p.contains("alg"))
			b.add("alg", header.getAlgorithm().alg);

		if ((header instanceof WebEncryptionHeader) && !p.contains("enc"))
			b.add("enc", ((WebEncryptionHeader) header).getEncryption().enc);

		if (!p.contains("typ")) {
			final var typ = header.getType();
			if (typ != null)
				b.add("typ", typ);
		}
		if (!p.contains("cty")) {
			final var cty = header.getContentType();
			if (cty != null)
				b.add("cty", cty);
		}

		if (!p.contains("kid")) {
			final var kid = header.getKeyId();
			if (kid != null)
				b.add("kid", kid);
		}
		if (!p.contains("jku")) {
			final var jku = header.getKeySetUri();
			if (jku != null)
				b.add("jku", jku.toString());
		}
		if (!p.contains("jwk")) {
			final var jwk = header.getKey();
			if (jwk != null) {
				final var jwkb = JsonP.PROVIDER.createObjectBuilder();
				Jwk.writeJwk(jwkb, WebKey.wellKnown(jwk));
				b.add("jwk", jwkb);
			}
		}

		if (!p.contains("x5u")) {
			final var x5u = header.getCertificateUri();
			if (x5u != null)
				b.add("x5u", x5u.toString());
		}
		if (!p.contains("x5c")) {
			final var x5c = header.getCertificateChain();
			if (x5c != null) {
				final var x5cb = JsonP.PROVIDER.createArrayBuilder();
				for (final var cert : x5c) {
					// RFC-7517 JWK 4.7: Base64 _not_ URL encoder, with padding
					x5cb.add(Base64.getEncoder().encodeToString(IuException.unchecked(cert::getEncoded)));
				}
				b.add("x5c", x5cb);
			}
		}
		if (!p.contains("x5t")) {
			final var x5t = header.getCertificateThumbprint();
			if (x5t != null)
				b.add("x5t", EncodingUtils.base64Url(x5t));
		}
		if (!p.contains("x5t#S256")) {
			final var x5t = header.getCertificateSha256Thumbprint();
			if (x5t != null)
				b.add("x5t#S256", EncodingUtils.base64Url(x5t));
		}

		return b.build();
	}

	/**
	 * Gets the JOSE per-recipient unprotected header as JSON.
	 * 
	 * @param header header
	 * @return {@link JsonObject}
	 */
	static JsonObject getPerRecipient(WebSignatureHeader header) {
		final var p = header.getProtectedParameters();
		final var c = header.getCriticalExtendedParameters();

		final var b = JsonP.PROVIDER.createObjectBuilder();
		final var x = header.getExtendedParameters();
		if (x != null)
			for (final var e : x.entrySet())
				if (!p.contains(e.getKey()) && (c == null || c.contains(e.getKey())))
					// TODO: single-value conversion
					b.add(e.getKey(), (String) e.getValue());

		return b.build();
	}

	/**
	 * Gets the JOSE protected header as JSON.
	 * 
	 * @param header header
	 * @return {@link JsonObject}
	 */
	static JsonObject getProtected(WebSignatureHeader header) {
		final var b = JsonP.PROVIDER.createObjectBuilder();
		for (final var name : header.getProtectedParameters())
			switch (name) {
			case "alg":
				b.add("alg", header.getAlgorithm().alg);
				break;
			case "enc":
				b.add("enc", ((WebEncryptionHeader) header).getEncryption().enc);
				break;
			case "typ":
				b.add("typ", header.getType());
				break;
			case "cty":
				b.add("cty", header.getContentType());
				break;

			case "kid":
				b.add("kid", header.getKeyId());
				break;
			case "jku":
				b.add("jku", header.getKeySetUri().toString());
				break;
			case "jwk": {
				final var jwkb = JsonP.PROVIDER.createObjectBuilder();
				Jwk.writeJwk(jwkb, WebKey.wellKnown(header.getKey()));
				b.add("jwk", jwkb);
				break;
			}

			case "x5u":
				b.add("x5u", header.getCertificateUri().toString());
				break;
			case "x5c": {
				final var x5cb = JsonP.PROVIDER.createArrayBuilder();
				for (final var cert : header.getCertificateChain())
					// RFC-7517 JWK 4.7: Base64 _not_ URL encoder, with padding
					x5cb.add(Base64.getEncoder().encodeToString(IuException.unchecked(cert::getEncoded)));
				b.add("x5c", x5cb);
				break;
			}
			case "x5t":
				b.add("x5t", EncodingUtils.base64Url(header.getCertificateThumbprint()));
				break;
			case "x5t#S256":
				b.add("x5t#S256", EncodingUtils.base64Url(header.getCertificateSha256Thumbprint()));
				break;

			case "crit": {
				final var critb = JsonP.PROVIDER.createArrayBuilder();
				header.getCriticalExtendedParameters().forEach(critb::add);
				b.add("crit", critb);
				break;
			}

			default: // TODO: single-value conversion
				b.add(name, (String) Objects.requireNonNull(header.getExtendedParameters().get(name)));
				break;
			}

		return b.build();
	}

	private Jose() {
	}
}
