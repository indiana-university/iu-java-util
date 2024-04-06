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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.crypt.WebCryptoHeader;
import edu.iu.crypt.WebKey.Use;
import jakarta.json.JsonObject;

/**
 * Provides {@link WebCryptoHeader} and {@link WebEncryptionHeader} processing
 * utilities.
 */
public final class Jose extends JsonKeyReference<Jose> implements WebCryptoHeader {
	static {
		IuObject.assertNotOpen(Jose.class);
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
	static Jose from(JsonObject protectedHeader, JsonObject sharedHeader, JsonObject perRecipientHeader) {
		if (sharedHeader == null && perRecipientHeader == null)
			return new Jose(protectedHeader);

		final var b = IuJson.object();
		for (final var header : IuIterable.iter(protectedHeader, sharedHeader, perRecipientHeader))
			if (header != null)
				header.forEach(b::add);

		return new Jose(b.build());
	}

	/**
	 * Determines if a parameter name not registered for use with JWS is understood.
	 * 
	 * @param paramName parameter name
	 * @return true if the parameter name is not registered for JWS and understood
	 *         by this implementation.
	 */
	private static boolean isUnderstood(String paramName) {
		final var param = Param.from(paramName);
		if (param == null)
			return EXTENSIONS.containsKey(paramName);
		else
			return !param.isUsedFor(Use.SIGN);
	}

	private final Jwk key;
	private final URI keySetUri;
	private final String type;
	private final String contentType;
	private final Set<String> criticalParameters;
	private final JsonObject extendedParameters;
	private final Jwk wellKnownKey;

	/**
	 * Constructor.
	 * 
	 * @param jose header parameters
	 */
	Jose(JsonObject jose) {
		super(jose);
		keySetUri = IuJson.get(jose, "jku", IuJsonAdapter.of(URI.class));
		key = (Jwk) IuJson.get(jose, "jwk", Jwk.JSON);
		type = IuJson.get(jose, "typ");
		contentType = IuJson.get(jose, "cty");
		criticalParameters = IuJson.get(jose, "crit", IuJsonAdapter.of(Set.class, IuJsonAdapter.of(String.class)));

		final var extendedParametersBuilder = IuJson.object();
		for (final var parameterEntry : jose.entrySet()) {
			final var paramName = parameterEntry.getKey();
			IuJson.add(extendedParametersBuilder, paramName, parameterEntry.getValue(), () -> isUnderstood(paramName));
		}
		this.extendedParameters = extendedParametersBuilder.build();

		wellKnownKey = (Jwk) WebCryptoHeader.verify(this);
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
	public <T> T getExtendedParameter(String name) {
		final var param = Param.from(name);

		final IuJsonAdapter<T> adapter;
		if (param != null)
			adapter = param.json();
		else
			adapter = Jose.getExtension(name);

		return IuJson.get(extendedParameters, name, adapter);
	}

	@Override
	public String toString() {
		return toJson(a -> true).toString();
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
	 * Gets extended parameters as a JSON object.
	 * 
	 * @return {@link JsonObject}
	 */
	JsonObject extendedParameters() {
		return extendedParameters;
	}

	/**
	 * Gets the JOSE header as JSON.
	 * 
	 * @param nameFilter accepts standard or extended param name and returns true to
	 *                   include the parameter; else false
	 * @return {@link JsonObject}; null if no parameters match the filter
	 */
	JsonObject toJson(Predicate<String> nameFilter) {
		final var headerBuilder = IuJson.object();

		for (final var param : Param.values())
			if (!param.equals(Param.KEY) //
					&& param.isUsedFor(Use.SIGN) //
					&& nameFilter.test(param.name))
				IuJson.add(headerBuilder, param.name, () -> param.get(this), param.json());
		if (key != null)
			IuJson.add(headerBuilder, "jwk", () -> wellKnownKey, Jwk.JSON);

		for (final var extendedParameterEntry : extendedParameters.entrySet()) {
			final var name = extendedParameterEntry.getKey();
			IuJson.add(headerBuilder, name, extendedParameterEntry.getValue(), () -> nameFilter.test(name));
		}

		final var header = headerBuilder.build();
		if (header.isEmpty())
			return null;
		else
			return header;
	}

}
