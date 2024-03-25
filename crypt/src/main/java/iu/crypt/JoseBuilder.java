/*
 * Copyright © 2024 Indiana University
 * All rights reserved.
 *
 * BSD 3-Clause License
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * 
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * 
 * - Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package iu.crypt;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.client.IuJson;
import edu.iu.crypt.WebCryptoHeader.Builder;
import edu.iu.crypt.WebCryptoHeader.Extension;
import edu.iu.crypt.WebCryptoHeader.Param;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

/**
 * Builds a web signature or encryption header.
 * 
 * @param <B> builder type
 */
public abstract class JoseBuilder<B extends JoseBuilder<B>> extends WebKeyReferenceBuilder<B> implements Builder<B> {
	static {
		IuObject.assertNotOpen(JoseBuilder.class);
	}

	private static final Map<String, Extension<?>> EXTENSIONS = new HashMap<>();

	/**
	 * Registers an extension.
	 * 
	 * @param parameterName parameter name
	 * @param extension     extension
	 */
	public static synchronized <T> void register(String parameterName, Extension<T> extension) {
		if (Param.from(parameterName) != null)
			throw new IllegalArgumentException("Must not be a standard regsitered parameter name");
		if (EXTENSIONS.containsKey(parameterName))
			throw new IllegalArgumentException("Already registered");

		EXTENSIONS.put(parameterName, extension);
	}

	/**
	 * Gets a registered extension.
	 * 
	 * @param <T>           parameter type
	 * @param parameterName parameter name
	 * @return extension registered for the named parameter
	 */
	@SuppressWarnings("unchecked")
	static <T> Extension<T> getExtension(String parameterName) {
		return (Extension<T>) EXTENSIONS.get(parameterName);
	}

	private URI keySetUri;
	private boolean silent;
	private Jwk key;
	private String type;
	private String contentType;
	private Set<String> crit = new LinkedHashSet<>();
	private byte[] partyUInfo;
	private byte[] partyVInfo;
	private JsonObjectBuilder ext = IuJson.object();

	/**
	 * Default constructor.
	 */
	protected JoseBuilder() {
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
	public B apu(byte[] apu) {
		if (!Objects.requireNonNull(algorithm(), "algorithm required before apu").encryptionParams
				.contains(Param.PARTY_UINFO))
			throw new IllegalArgumentException("apu not understood for " + algorithm());

		if (partyUInfo == null)
			partyUInfo = apu;
		else if (!Arrays.equals(partyUInfo, apu))
			throw new IllegalStateException("agreement PartyUInfo already set to a different value");

		return next();
	}

	@Override
	public B apv(byte[] apv) {
		Objects.requireNonNull(apv);
		if (!Objects.requireNonNull(algorithm(), "algorithm required before apv").encryptionParams
				.contains(Param.PARTY_VINFO))
			throw new IllegalArgumentException("apu not understood for " + algorithm());

		if (partyVInfo == null)
			partyVInfo = apv;
		else if (!Arrays.equals(partyVInfo, apv))
			throw new IllegalStateException("agreement PartyVInfo already set to a different value");

		return next();
	}

	@Override
	public B crit(String... name) {
		IuIterable.iter(name).forEach(crit::add);
		return next();
	}

	@Override
	public <T> B ext(String parameterName, T value) {
		if (Param.from(parameterName) != null)
			throw new IllegalArgumentException("Must not be a standard registered parameter name");

		final var extension = Objects.requireNonNull(getExtension(parameterName),
				"extension not registered for " + parameterName);

		ext.add(parameterName, extension.toJson(value));
		return next();
	}

	/**
	 * Adds an encryption parameter.
	 * 
	 * <p>
	 * For use only during {@link JweRecipientBuilder#build(Jwe, byte[])}
	 * invocation.
	 * </p>
	 * 
	 * @param name  parameter name
	 * @param value parameter value
	 * @return this
	 */
	B enc(String name, JsonValue value) {
		ext.add(name, value);
		return next();
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
	 * Gets agreement PartyUInfo data
	 * 
	 * @return agreement PartyUInfo data
	 */
	byte[] partyUInfo() {
		return partyUInfo;
	}

	/**
	 * Gets agreement PartyVInfo data
	 * 
	 * @return agreement PartyVInfo data
	 */
	byte[] partyVInfo() {
		return partyVInfo;
	}

	/**
	 * Gets ext
	 * 
	 * @return ext
	 */
	JsonObjectBuilder ext() {
		return ext;
	}

}
