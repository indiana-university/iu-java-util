package iu.crypt;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.crypt.WebEncryptionHeader;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebSignatureHeader;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;

/**
 * Provides {@link WebSignatureHeader} and {@link WebEncryptionHeader}
 * processing utilities.
 */
final class Jose implements WebEncryptionHeader {

	private static final Set<String> STANDARD_PARAMS = Set.of( //
			"alg", "enc", "zip", "typ", "cty", "kid", "jku", "jwk", "x5u", "x5c", "x5t", "x5t#S256");

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

	/**
	 * Calculates a hash code over protected header values.
	 * 
	 * @param h header
	 * @return hash code
	 */
	static int hashCode(WebSignatureHeader h) {
		final var l = new ArrayList<>();
		final var p = h.getProtectedParameters();
		l.add(p);

		final var ps = new HashSet<>(p);
		if (ps.remove("alg"))
			l.add(h.getAlgorithm());
		if (ps.remove("enc"))
			l.add(((WebEncryptionHeader) h).getEncryption());
		if (ps.remove("zip"))
			l.add(((WebEncryptionHeader) h).isDeflate());
		if (ps.remove("cty"))
			l.add(h.getContentType());
		if (ps.remove("kid"))
			l.add(h.getKeyId());
		if (ps.remove("typ"))
			l.add(h.getType());
		if (ps.remove("x5c"))
			l.add(h.getCertificateChain());
		if (ps.remove("x5t#S256"))
			l.add(h.getCertificateSha256Thumbprint());
		if (ps.remove("x5t"))
			l.add(h.getCertificateThumbprint());
		if (ps.remove("x5u"))
			l.add(h.getCertificateUri());
		if (ps.remove("crit"))
			l.add(h.getCriticalExtendedParameters());
		if (ps.remove("jwk"))
			l.add(h.getKey());
		if (ps.remove("jku"))
			l.add(h.getKeySetUri());

		final var ext = h.getExtendedParameters();
		for (final var a : ps)
			l.add(ext.get(a));

		return IuObject.hashCode(l.toArray(new Object[l.size()]));
	}

	/**
	 * Determines whether or not two JOSE headers include the same protected header
	 * values.
	 * 
	 * @param h1 header
	 * @param h2 header
	 * @return true if all protected headers values match; else false
	 */
	static boolean equals(WebSignatureHeader h1, WebSignatureHeader h2) {
		if (h1 == h2)
			return true;
		if (h1 == null || h2 == null)
			return false;

		final var p = new HashSet<>(h1.getProtectedParameters());
		if (!IuObject.equals(p, h2.getProtectedParameters()))
			return false;

		if ((p.remove("alg") && !IuObject.equals(h1.getAlgorithm(), h2.getAlgorithm())) //
				|| (p.remove("enc") && !IuObject.equals(((WebEncryptionHeader) h1).getEncryption(),
						((WebEncryptionHeader) h2).getEncryption())) //
				|| (p.remove("zip") && !IuObject.equals(((WebEncryptionHeader) h1).isDeflate(),
						((WebEncryptionHeader) h2).isDeflate())) //
				|| (p.remove("cty") && !IuObject.equals(h1.getContentType(), h2.getContentType())) //
				|| (p.remove("kid") && !IuObject.equals(h1.getKeyId(), h2.getKeyId())) //
				|| (p.remove("typ") && !IuObject.equals(h1.getType(), h2.getType())) //
				|| (p.remove("x5c") && !IuObject.equals(h1.getCertificateChain(), h2.getCertificateChain())) //
				|| (p.remove("x5t#S256")
						&& !IuObject.equals(h1.getCertificateSha256Thumbprint(), h2.getCertificateSha256Thumbprint())) //
				|| (p.remove("x5t") && !IuObject.equals(h1.getCertificateThumbprint(), h2.getCertificateThumbprint())) //
				|| (p.remove("x5u") && !IuObject.equals(h1.getCertificateUri(), h2.getCertificateUri())) //
				|| (p.remove("crit")
						&& !IuObject.equals(h1.getCriticalExtendedParameters(), h2.getCriticalExtendedParameters())) //
				|| (p.remove("jwk") && !IuObject.equals(h1.getKey(), h2.getKey())) //
				|| (p.remove("jku") && !IuObject.equals(h1.getKeySetUri(), h2.getKeySetUri())))
			return false;

		if (p.isEmpty())
			return true;

		final var ext = h1.getExtendedParameters();
		final var otherExt = h2.getExtendedParameters();
		if (ext == null || otherExt == null)
			return false;
		for (final var param : p)
			if (!IuObject.equals(ext.get(param), otherExt.get(param)))
				return false;

		return true;
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
			return new Jose(protectedHeader, protectedHeader.keySet());

		final var b = JsonP.PROVIDER.createObjectBuilder(protectedHeader);
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

		return new Jose(b.build(), protectedHeader.keySet());
	}

	private final JsonObject jose;
	private final Set<String> protectionParameters;

	private Jose(JsonObject jose, Set<String> protectionParameters) {
		this.jose = jose;
		this.protectionParameters = protectionParameters;
	}

	@Override
	public Algorithm getAlgorithm() {
		return Algorithm.from(jose.getString("alg"));
	}

	@Override
	public Set<String> getProtectedParameters() {
		if (protectionParameters == null)
			return WebEncryptionHeader.super.getProtectedParameters();
		else
			return protectionParameters;
	}

	@Override
	public String getKeyId() {
		if (!jose.containsKey("kid"))
			return null;
		else
			return jose.getString("kid");
	}

	@Override
	public URI getKeySetUri() {
		if (!jose.containsKey("jku"))
			return null;
		else
			return URI.create(jose.getString("jku"));
	}

	@Override
	public WebKey getKey() {
		if (!jose.containsKey("jwk"))
			return null;
		else
			return new Jwk(jose.getJsonObject("jwk"));
	}

	@Override
	public String getType() {
		if (!jose.containsKey("typ"))
			return null;
		else
			return jose.getString("typ");
	}

	@Override
	public String getContentType() {
		if (!jose.containsKey("cty"))
			return null;
		else
			return jose.getString("cty");
	}

	@Override
	public Set<String> getCriticalExtendedParameters() {
		if (!jose.containsKey("crit"))
			return null;
		else {
			final var crit = jose.getJsonArray("crit");
			final Set<String> s = new LinkedHashSet<>();
			for (var i = 0; i < crit.size(); i++)
				s.add(crit.getString(i));
			return s;
		}
	}

	@Override
	public Map<String, ?> getExtendedParameters() {
		final Map<String, String> params = new LinkedHashMap<>();
		for (final var e : jose.entrySet())
			if (!STANDARD_PARAMS.contains(e.getKey()))
				params.put(e.getKey(), ((JsonString) e.getValue()).getString());
		return params;
	}

	@Override
	public URI getCertificateUri() {
		if (!jose.containsKey("x5u"))
			return null;
		else
			return URI.create(jose.getString("x5u"));
	}

	@Override
	public X509Certificate[] getCertificateChain() {
		if (jose.containsKey("x5c"))
			return IuException.unchecked(() -> {
				final var x5c = jose.getJsonArray("x5c");
				final var certFactory = CertificateFactory.getInstance("X.509");
				final Queue<X509Certificate> certs = new ArrayDeque<>(x5c.size());
				for (var i = 0; i < x5c.size(); i++)
					certs.offer((X509Certificate) certFactory.generateCertificate(
							new ByteArrayInputStream(Base64.getDecoder().decode(x5c.getString(i)))));
				return certs.toArray(new X509Certificate[certs.size()]);
			});
		else
			return null;
	}

	@Override
	public byte[] getCertificateThumbprint() {
		if (!jose.containsKey("x5t"))
			return null;
		else
			return EncodingUtils.base64Url(jose.getString("x5t"));
	}

	@Override
	public byte[] getCertificateSha256Thumbprint() {
		if (!jose.containsKey("x5t#S256"))
			return null;
		else
			return EncodingUtils.base64Url(jose.getString("x5t#S256"));
	}

	@Override
	public Encryption getEncryption() {
		if (!jose.containsKey("enc"))
			return null;
		else
			return Encryption.from(jose.getString("enc"));
	}

	@Override
	public boolean isDeflate() {
		return jose.containsKey("zip") && jose.getString("zip").equals("DEF");
	}

	@Override
	public int hashCode() {
		return hashCode(this);
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof WebSignatureHeader))
			return false;
		else
			return equals(this, (WebSignatureHeader) obj);
	}

	@Override
	public String toString() {
		final var b = JsonP.PROVIDER.createObjectBuilder();
		b.add("protected", getProtected(this));
		b.add("unprotected", getShared(this));
		b.add("header", getPerRecipient(this));
		return b.build().toString();
	}

}
