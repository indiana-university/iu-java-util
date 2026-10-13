/*
 * Copyright © 2026 Indiana University
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

import java.lang.reflect.Type;
import java.net.URI;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import edu.iu.IuObject;
import edu.iu.client.IuJsonProperties;
import edu.iu.crypt.WebCryptoHeader;
import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebEncryptionRecipient;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Use;
import edu.iu.crypt.WebSignature;
import jakarta.json.JsonObject;

/**
 * {@link WebCryptoHeader} implementation.
 */
public final class Jose extends JsonKeyReference<Jose> implements WebCryptoHeader {
	static {
		IuObject.assertNotOpen(Jose.class);
	}

	private static final Map<String, Extension<?>> EXTENSIONS = new ConcurrentHashMap<>();

	private static final Set<Param> NON_EXT_PARAMS = EnumSet.of(Param.ALGORITHM, Param.KEY_ID, Param.KEY_SET_URI,
			Param.KEY, Param.CERTIFICATE_URI, Param.CERTIFICATE_CHAIN, Param.CERTIFICATE_THUMBPRINT,
			Param.CERTIFICATE_SHA256_THUMBPRINT, Param.TYPE, Param.CONTENT_TYPE, Param.CRITICAL_PARAMS);

	/**
	 * Extension provider interface.
	 * 
	 * @param <T> value type
	 */
	public interface Extension<T> {

		/**
		 * Gets the extension type.
		 *
		 * @return type the parameter value converts as
		 */
		Type type();

		/**
		 * Validates an incoming parameter value.
		 * 
		 * @param value   value
		 * @param builder {@link WebSignature.Builder} or
		 *                {@link WebEncryptionRecipient.Builder}
		 * @throws IllegalArgumentException if the value is invalid
		 */
		default void validate(T value, WebCryptoHeader.Builder<?> builder) throws IllegalArgumentException {
		}

		/**
		 * Applies extended verification logic for processing {@link WebCryptoHeader}.
		 * 
		 * @param header JOSE header
		 * @throws IllegalStateException if the header verification fails
		 */
		default void verify(WebCryptoHeader header) throws IllegalStateException {
		}

		/**
		 * Applies extended verification logic for processing {@link WebSignature}.
		 * 
		 * @param signature JWS signature
		 * @throws IllegalStateException if the verification fails
		 */
		default void verify(WebSignature signature) throws IllegalStateException {
		}

		/**
		 * Applies extended verification logic for processing {@link WebEncryption}.
		 * 
		 * @param encryption JWE encrypted message
		 * @param recipient  JWE recipient, available via
		 *                   {@link WebEncryption#getRecipients()}
		 * @throws IllegalStateException if the verification fails
		 */
		default void verify(WebEncryption encryption, WebEncryptionRecipient recipient) throws IllegalStateException {
		}
	}

	/**
	 * Registers an extension.
	 *
	 * @param <T>           parameter type
	 * @param parameterName parameter name; <em>must not</em> be a registered
	 *                      parameter name enumerated by {@link Param},
	 *                      <em>should</em> be collision-resistant. Take care when
	 *                      using {@link Extension} to implement an <a href=
	 *                      "https://www.iana.org/assignments/jose/jose.xhtml#web-signature-encryption-header-parameters">IANA
	 *                      Registered Parameter</a> not enumerated by
	 *                      {@link Param}, since these may be implemented internally
	 *                      in a future release.
	 * @param extension     provider implementation
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc7515#section-4.2">RFC-7516 JWS
	 *      Section 4.2</a>
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc7516#section-4.2">RFC-7516 JWE
	 *      Section 4.2</a>
	 */
	public static synchronized <T> void register(String parameterName, Extension<T> extension) {
		if (Param.from(parameterName) != null)
			throw new IllegalArgumentException("Must not be a standard regsitered parameter name");
		if (EXTENSIONS.containsKey(parameterName))
			throw new IllegalArgumentException("Already registered");

		Objects.requireNonNull(extension.type(), "missing type");

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
		return Objects.requireNonNull((Extension<T>) EXTENSIONS.get(parameterName),
				"must understand extension " + parameterName);
	}

	/**
	 * Creates a JOSE header from serialized headers.
	 * 
	 * @param protectedHeader    protected header data
	 * @param sharedHeader       unprotected shared header data
	 * @param perRecipientHeader unprotected per-recipient header data
	 * @return JOSE header
	 */
	static Jose from(IuJsonProperties protectedHeader, IuJsonProperties sharedHeader,
			IuJsonProperties perRecipientHeader) {
		if (sharedHeader == null && perRecipientHeader == null)
			return new Jose(protectedHeader);
		else {
			final var builder = CryptJsonAdapters.builder();
			IuObject.convert(protectedHeader, builder::putAll);
			IuObject.convert(sharedHeader, builder::putAll);
			IuObject.convert(perRecipientHeader, builder::putAll);
			return new Jose(builder.build());
		}
	}

	private final Jwk key;
	private final URI keySetUri;
	private final String type;
	private final String contentType;
	private final Set<String> criticalParameters;
	private final Map<String, Object> extendedParameters;
	private final Jwk wellKnownKey;

	/**
	 * Constructor.
	 * 
	 * @param joseValue header parameters
	 */
	Jose(IuJsonProperties joseValue) {
		super(joseValue);

		keySetUri = joseValue.get("jku", URI.class);
		// only the public part of a key belongs in a header
		key = IuObject.convert((Jwk) joseValue.get("jwk", WebKey.class), Jwk::wellKnown);
		type = joseValue.get("typ", String.class);
		contentType = joseValue.get("cty", String.class);
		criticalParameters = IuObject.convert(joseValue.get("crit", String[].class), Set::of);

		// registered parameters beyond the common ones, and the extensions
		// understood; any other is ignored, unless critical, which verify rejects
		extendedParameters = new LinkedHashMap<>();
		for (final var name : joseValue.names()) {
			final var param = Param.from(name);
			final Type paramType;
			if (param == null) {
				final var extension = EXTENSIONS.get(name);
				if (extension == null)
					continue;
				paramType = extension.type();
			} else if (NON_EXT_PARAMS.contains(param))
				continue;
			else
				paramType = param.type;

			final var value = joseValue.get(name, paramType);
			if (value != null)
				extendedParameters.put(name, value);
		}

		wellKnownKey = (Jwk) WebCryptoHeader.verify(this);

		for (final var paramName : extendedParameters.keySet())
			if (Param.from(paramName) == null)
				getExtension(paramName).verify(this);
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
	public Set<String> getCriticalParameters() {
		return criticalParameters;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T getExtendedParameter(String name) {
		return (T) extendedParameters.get(name);
	}

	@Override
	public String toString() {
		return CryptJsonAdapters.JSONB.toJson(this);
	}

	/**
	 * Gets the verified well-known key resolved for this header.
	 * 
	 * @return well-known key
	 */
	Jwk wellKnown() {
		return wellKnownKey;
	}

	/**
	 * Gets the extended parameters.
	 * 
	 * @return extended parameters
	 */
	Map<String, Object> extendedParameters() {
		return extendedParameters;
	}

	/**
	 * Determines whether the header has a parameter.
	 *
	 * @param paramName registered or extended parameter name
	 * @return true if the parameter has a non-null value; else false
	 */
	boolean hasParam(String paramName) {
		final var param = Param.from(paramName);
		if (param == null)
			return extendedParameters.containsKey(paramName);
		else
			return param.get(this) != null;
	}

	/**
	 * Gets the JOSE header as JSON.
	 * 
	 * @param nameFilter accepts standard or extended param name and returns true to
	 *                   include the parameter; else false
	 * @return {@link JsonObject}; null if no parameters match the filter
	 */
	IuJsonProperties values(Predicate<String> nameFilter) {
		final var builder = CryptJsonAdapters.builder();

		for (final var param : Param.values())
			if (!param.equals(Param.KEY) //
					&& param.isUsedFor(Use.SIGN) //
					&& nameFilter.test(param.name))
				IuObject.convert(param.get(this), value -> builder.put(param.name, value));

		if (key != null && nameFilter.test("jwk"))
			builder.put("jwk", key);

		for (final var extendedParameterEntry : extendedParameters.entrySet())
			if (nameFilter.test(extendedParameterEntry.getKey()))
				builder.put(extendedParameterEntry.getKey(), extendedParameterEntry.getValue());

		return builder.isEmpty() ? null : builder.build();
	}

}
