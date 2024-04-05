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
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.crypt.PemEncoded;
import edu.iu.crypt.WebCryptoHeader;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Use;
import jakarta.json.JsonObject;

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
	static boolean isUnderstood(String paramName) {
		final var param = Param.from(paramName);
		if (param == null)
			try {
				JoseBuilder.getExtension(paramName);
				return true;
			} catch (NullPointerException e) {
				return false;
			}
		else
			return !param.isUsedFor(Use.SIGN);
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

	private final Set<String> criticalParameters;
	private final JsonObject extendedParameters;

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
		if (key == null //
				|| builder.silent())
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
		if (builder.crit().isEmpty())
			criticalParameters = null;
		else
			criticalParameters = Collections.unmodifiableSet(builder.crit());

		extendedParameters = IuJsonAdapter.of(Map.class).toJson(builder.ext()).asJsonObject();

		verify();
	}

	private Jose(JsonObject jose) {
		algorithm = IuJson.nonNull(jose, "alg", Algorithm.JSON);
		keyId = IuJson.get(jose, "kid", IuJsonAdapter.of(String.class));
		keySetUri = IuJson.get(jose, "jku", IuJsonAdapter.of(URI.class));
		key = IuJson.get(jose, "jwk", JwkBuilder.JSON);
		silent = false;

		type = IuJson.get(jose, "typ");
		contentType = IuJson.get(jose, "cty");

		certificateUri = IuJson.get(jose, "x5u", IuJsonAdapter.of(URI.class));
		certificateChain = IuJson.get(jose, "x5c", IuJsonAdapter.of(X509Certificate[].class, PemEncoded.CERT_JSON));
		certificateThumbprint = IuJson.get(jose, "x5t", UnpaddedBinary.JSON);
		certificateSha256Thumbprint = IuJson.get(jose, "x5t#S256", UnpaddedBinary.JSON);

		criticalParameters = IuJson.get(jose, "crit", IuJsonAdapter.of(Set.class, IuJsonAdapter.of(String.class)));

		final var extendedParametersBuilder = IuJson.object();
		for (final var parameterEntry : jose.entrySet()) {
			final var paramName = parameterEntry.getKey();
			IuJson.add(extendedParametersBuilder, paramName, parameterEntry.getValue(), //
					() -> isUnderstood(paramName));
		}
		this.extendedParameters = extendedParametersBuilder.build();

		verify();
	}

	private void verify() {
		if (keyId != null //
				&& key != null //
				&& !keyId.equals(key.getId()))
			throw new IllegalStateException("Key ID " + keyId + " doesn't match key");

		if (certificateChain != null) {
			final var cert = certificateChain[0];
			if (key != null //
					&& !key.matches(cert))
				throw new IllegalStateException("Certificate mismatch");

			if (certificateThumbprint != null //
					&& !Arrays.equals(certificateThumbprint,
							IuException.unchecked(() -> DigestUtils.sha1(cert.getEncoded()))))
				throw new IllegalStateException("Certificate SHA-1 thumbprint mismatch");

			if (certificateSha256Thumbprint != null //
					&& !Arrays.equals(certificateSha256Thumbprint,
							IuException.unchecked(() -> DigestUtils.sha256(cert.getEncoded()))))
				throw new IllegalStateException("Certificate SHA-256 thumbprint mismatch");
		}

		if (criticalParameters != null)
			for (final var paramName : criticalParameters) {
				final var param = Param.from(paramName);
				if (param == null) {
					if (!extendedParameters.containsKey(paramName))
						throw new IllegalStateException("Critical parameter " + paramName + " must be present");
				} else if (!param.isPresent(this))
					throw new IllegalStateException("Critical parameter " + paramName + " must be present");
			}

		final var encryptionParams = Objects.requireNonNull(algorithm, "algorithm is required").encryptionParams;
		if (encryptionParams != null)
			for (final var param : encryptionParams)
				if (param.required && !param.isPresent(this))
					throw new IllegalStateException("Parameter " + param.name + " must be present for " + algorithm);

		for (final var paramEntry : extendedParameters.entrySet()) {
			final var paramName = paramEntry.getKey();
			final var param = Param.from(paramName);
			if (param == null) {
				final var extension = JoseBuilder.getExtension(paramName);
				if (extension != null)
					extension.verify(this);
			} else if (encryptionParams == null //
					|| !encryptionParams.contains(param))
				throw new IllegalStateException("Parameter " + param.name + " not understood for " + algorithm);
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
	public Set<String> getCriticalParameters() {
		return criticalParameters;
	}

	@Override
	public <T> T getExtendedParameter(String name) {
		final var param = Param.from(name);
		final IuJsonAdapter<T> adapter;
		if (param != null)
			adapter = param.json();
		else // unreachable as null by input validation
			adapter = JoseBuilder.getExtension(name);

		return IuJson.get(extendedParameters, name, adapter);
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
		return toJson(a -> true).toString();
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
	 * Gets a well-known public key from minimum data in a JOSE header.
	 * 
	 * @param jose JOSE header
	 * @return well-known public key, null if a key could not be determined from the
	 *         header
	 */
	Jwk wellKnown() {
		if (key != null)
			return key;

		if (keyId != null && keySetUri != null)
			return IuIterable.filter(JwkBuilder.readJwks(keySetUri), a -> keyId.equals(a.getId())).iterator().next();

		final X509Certificate cert;
		if (certificateChain == null && certificateUri != null)
			cert = PemEncoded.getCertificateChain(certificateUri)[0];
		else
			cert = null;

		if (cert != null) {
			if (certificateThumbprint != null && !Arrays.equals(certificateThumbprint,
					IuException.unchecked(() -> DigestUtils.sha1(cert.getEncoded()))))
				throw new IllegalArgumentException();
			if (certificateSha256Thumbprint != null && !Arrays.equals(certificateSha256Thumbprint,
					IuException.unchecked(() -> DigestUtils.sha256(cert.getEncoded()))))
				throw new IllegalArgumentException();

			final var jwkb = new JwkBuilder();
			if (keyId != null)
				jwkb.id(keyId);
			return jwkb.algorithm(algorithm).cert(cert).build();
		}

		return null;
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

		for (final var param : IuIterable.iter(Param.values()))
			IuJson.add(headerBuilder, param.name, //
					() -> param.get(this), //
					() -> nameFilter.test(param.name) //
							&& param.isUsedFor(Use.SIGN) //
							&& !(silent //
									&& Param.KEY.equals(param)),
					param.json());

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
