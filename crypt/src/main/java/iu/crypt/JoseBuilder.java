package iu.crypt;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import edu.iu.client.IuJson;
import edu.iu.crypt.WebEncryptionHeader.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebSignatureHeader.Builder;

/**
 * Builds a web signature or encryption header.
 * 
 * @param <B> builder type
 */
abstract class JoseBuilder<B extends JoseBuilder<B>> extends WebKeyReferenceBuilder<B> implements Builder<B> {

	private final Encryption encryption;
	private final boolean deflate;
	private URI keySetUri;
	private boolean silent;
	private Jwk key;
	private String type;
	private String contentType;
	private Set<String> crit = new LinkedHashSet<>();
	private Map<String, Object> ext = new LinkedHashMap<>();

	/**
	 * Constructor.
	 * 
	 * @param algorithm  algorithm
	 * @param encryption encryption
	 * @param deflate    deflate
	 */
	JoseBuilder(Algorithm algorithm, Encryption encryption, boolean deflate) {
		algorithm(algorithm);
		this.encryption = encryption;
		this.deflate = deflate;
	}

	@Override
	public B algorithm(Algorithm algorithm) {
		super.algorithm(algorithm);
		return next();
	}

	@Override
	public B jwks(URI uri) {
		Objects.requireNonNull(uri);

		if (this.keySetUri == null)
			this.keySetUri = uri;
		else if (!uri.equals(this.keySetUri))
			throw new IllegalStateException("Key set URI already set");

		return next();
	}

	@Override
	public B jwk(WebKey key, boolean silent) {
		Objects.requireNonNull(key);

		if (this.key == null)
			// cast enforces that key was not implemented externally
			this.key = (Jwk) key;
		else if (!key.equals(this.key))
			throw new IllegalStateException("Key already set");

		return next();
	}

	@Override
	public B type(String type) {
		Objects.requireNonNull(type);

		if (this.type == null)
			this.type = type;
		else if (!type.equals(this.type))
			throw new IllegalStateException("Header type already set");

		return next();
	}

	@Override
	public B contentType(String contentType) {
		Objects.requireNonNull(type);

		if (this.contentType == null)
			this.contentType = contentType;
		else if (!contentType.equals(this.contentType))
			throw new IllegalStateException("Content type already set");

		return next();
	}

	@Override
	public B crit(String name, Object value) {
		value = IuJson.toJava(IuJson.toJson(Objects.requireNonNull(value)));

		if (!crit.contains(name)) {
			if (!ext.containsKey(name)) {
				ext.put(name, value);
				crit.add(name);
			} else
				throw new IllegalStateException(name + " already set as a non-critical value");
		} else if (value.equals(ext.get(name)))
			throw new IllegalStateException(name + " already set to a different value");

		return next();
	}

	@Override
	public B ext(String name, Object value) {
		value = IuJson.toJava(IuJson.toJson(Objects.requireNonNull(value)));

		if (crit.contains(name))
			throw new IllegalStateException(name + " already set as a critical value");
		else if (!ext.containsKey(name))
			ext.put(name, value);
		else if (value.equals(ext.get(name)))
			throw new IllegalStateException(name + " already set to a different value");

		return next();
	}

	/**
	 * Gets encryption
	 * 
	 * @return encryption
	 */
	Encryption encryption() {
		return encryption;
	}

	/**
	 * Gets deflate
	 * 
	 * @return deflate
	 */
	boolean deflate() {
		return deflate;
	}

	/**
	 * Gets silent
	 * 
	 * @return silent
	 */
	boolean silent() {
		return silent;
	}

	/**
	 * Gets keySetUri
	 * 
	 * @return keySetUri
	 */
	URI keySetUri() {
		return keySetUri;
	}

	/**
	 * Gets key
	 * 
	 * @return key
	 */
	Jwk key() {
		return key;
	}

	/**
	 * Gets type
	 * 
	 * @return type
	 */
	String type() {
		return type;
	}

	/**
	 * Gets contentType
	 * 
	 * @return contentType
	 */
	String contentType() {
		return contentType;
	}

	/**
	 * Gets crit
	 * 
	 * @return crit
	 */
	Set<String> crit() {
		return crit;
	}

	/**
	 * Gets ext
	 * 
	 * @return ext
	 */
	Map<String, ?> ext() {
		return ext;
	}
	
}
