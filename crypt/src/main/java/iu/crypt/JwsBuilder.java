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

import java.io.InputStream;
import java.net.URI;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.IuStream;
import edu.iu.IuText;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.crypt.WebCryptoHeader.Param;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Use;
import edu.iu.crypt.WebSignature.Builder;
import edu.iu.crypt.WebSignedPayload;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

/**
 * Collects inputs for {@link Jws} encrypted messages.
 */
public class JwsBuilder implements Builder<JwsBuilder> {
	static {
		IuObject.assertNotOpen(JwsBuilder.class);
	}

	/**
	 * Parses JWS signed payload from serialized form
	 * 
	 * @param jws compact or JSON serialized from
	 * @return JWS signed payload
	 */
	public static JwsSignedPayload parse(String jws) {
		if (jws.startsWith("{")) {
			final var json = IuJson.parse(jws).asJsonObject();
			final var payload = IuJson.get(json, "payload", UnpaddedBinary.JSON);

			var signatures = IuJson.get(json, "signatures",
					IuJsonAdapter.<Iterable<Jws>>of(Iterable.class, IuJsonAdapter.from(Jws::parse)));
			if (signatures == null)
				signatures = Collections.singleton(Jws.parse(json));

			return new JwsSignedPayload(payload, signatures);
		} else {
			final var compact = UnpaddedBinary.compact(jws);
			final var protectedHeader = UnpaddedBinary.compactJson(compact.next()).asJsonObject();
			final var payload = UnpaddedBinary.base64Url(compact.next());
			final var signature = UnpaddedBinary.base64Url(compact.next());
			return new JwsSignedPayload(payload,
					IuIterable.iter(new Jws(protectedHeader, new Jose(protectedHeader), signature)));
		}
	}

	private class PendingSignature extends JoseBuilder<PendingSignature> {
		private PendingSignature(Algorithm algorithm) {
			super(algorithm);
			if (!algorithm.use.equals(Use.SIGN))
				throw new IllegalArgumentException("Not a signature algorithm " + algorithm);
		}

		@Override
		protected JsonValue param(String name) {
			return super.param(name);
		}

		private Jose header() {
			return new Jose(toJson());
		}

		private JsonObject protectedHeader() {
			final var protectedHeaderBuilder = IuJson.object();
			if (compact)
				for (final var paramName : paramNames()) {
					if (!paramName.equals(Param.KEY.name))
						protectedHeaderBuilder.add(paramName, param(paramName));
				}
			else if (protectedParameters.isEmpty())
				return null;
			else
				for (final var paramName : protectedParameters)
					protectedHeaderBuilder.add(paramName, Objects.requireNonNull(param(paramName), paramName));

			return protectedHeaderBuilder.build();
		}

	}

	private boolean compact;
	private Set<String> protectedParameters = new LinkedHashSet<>();
	private Deque<PendingSignature> pendingSignatures = new ArrayDeque<>();

	/**
	 * Constructor.
	 * 
	 * @param algorithm {@link Algorithm}
	 */
	public JwsBuilder(Algorithm algorithm) {
		protectedParameters.add(Param.ALGORITHM.name);
		next(algorithm);
	}

	@Override
	public JwsBuilder next(Algorithm algorithm) {
		pendingSignatures.offer(new PendingSignature(algorithm));
		return this;
	}

	@Override
	public JwsBuilder compact() {
		compact = true;
		return this;
	}

	@Override
	public JwsBuilder protect(Param... params) {
		for (final var param : params)
			protectedParameters.add(param.name);
		return this;
	}

	@Override
	public JwsBuilder protect(String... params) {
		for (final var param : params)
			protectedParameters.add(param);
		return this;
	}

	@Override
	public JwsBuilder keyId(String keyId) {
		pendingSignatures.peekLast().keyId(keyId);
		return this;
	}

	@Override
	public JwsBuilder wellKnown(URI uri) {
		pendingSignatures.peekLast().wellKnown(uri);
		return this;
	}

	@Override
	public JwsBuilder wellKnown(WebKey key) {
		pendingSignatures.peekLast().wellKnown(key);
		return this;
	}

	@Override
	public JwsBuilder key(WebKey key) {
		pendingSignatures.peekLast().key(key);
		return this;
	}

	@Override
	public JwsBuilder type(String type) {
		pendingSignatures.peekLast().type(type);
		return this;
	}

	@Override
	public JwsBuilder contentType(String contentType) {
		pendingSignatures.peekLast().contentType(contentType);
		return this;
	}

	@Override
	public JwsBuilder crit(String... parameterNames) {
		pendingSignatures.peekLast().crit(parameterNames);
		return this;
	}

	@Override
	public <T> JwsBuilder param(Param param, T value) {
		pendingSignatures.peekLast().param(param, value);
		return this;
	}

	@Override
	public <T> JwsBuilder param(String name, T value) {
		pendingSignatures.peekLast().param(name, value);
		return this;
	}

	@Override
	public WebSignedPayload sign(InputStream in) {
		final var payload = IuException.<InputStream, byte[]>unchecked(in, IuStream::read);

		final Queue<Jws> signatures = new ArrayDeque<>();
		for (final var pendingSignature : pendingSignatures) {
			final var key = pendingSignature.key();
			final var header = pendingSignature.header();
			final var algorithm = header.getAlgorithm();

			final var protectedHeader = pendingSignature.protectedHeader();
			final var encodedHeader = UnpaddedBinary
					.base64Url(IuText.utf8(Objects.requireNonNullElse(protectedHeader, "").toString()));
			final var encodedPayload = UnpaddedBinary.base64Url(payload);
			final var signingInput = encodedHeader + '.' + encodedPayload;
			final var dataToSign = IuText.utf8(signingInput);

			final byte[] signature;
			if (algorithm.algorithm.startsWith("Hmac")) {
				signature = IuException.unchecked(() -> {
					final var mac = Mac.getInstance(algorithm.algorithm);
					mac.init(new SecretKeySpec(key.getKey(), "Hmac"));
					return mac.doFinal(dataToSign);
				});
			} else
				signature = IuException.unchecked(() -> {
					final var sig = Signature.getInstance(algorithm.algorithm);
					switch (algorithm) {
					case PS256:
						sig.setParameter(new PSSParameterSpec(MGF1ParameterSpec.SHA256.getDigestAlgorithm(), "MGF1",
								MGF1ParameterSpec.SHA256, algorithm.size / 8, 1));
						break;
					case PS384:
						sig.setParameter(new PSSParameterSpec(MGF1ParameterSpec.SHA384.getDigestAlgorithm(), "MGF1",
								MGF1ParameterSpec.SHA384, algorithm.size / 8, 1));
						break;
					case PS512:
						sig.setParameter(new PSSParameterSpec(MGF1ParameterSpec.SHA512.getDigestAlgorithm(), "MGF1",
								MGF1ParameterSpec.SHA512, algorithm.size / 8, 1));
						break;
					default:
						break;
					}
					sig.initSign(key.getPrivateKey());
					sig.update(dataToSign);
					return sig.sign();
				});

			signatures.add(new Jws(protectedHeader, header, signature));
		}

		return new JwsSignedPayload(payload, Collections.unmodifiableCollection(signatures));
	}

}
