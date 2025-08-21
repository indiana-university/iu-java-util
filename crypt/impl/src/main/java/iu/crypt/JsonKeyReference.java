/*
 * Copyright © 2025 Indiana University
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

import edu.iu.IuObject;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKeyReference;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

/**
 * Encapsulates JSON properties that refer to or verify an X.509 certificate
 * chain.
 * 
 * @param <R> reference type
 */
class JsonKeyReference<R extends JsonKeyReference<R>> extends JsonCertificateReference<R> implements WebKeyReference {

	private String keyId;
	private Algorithm algorithm;

	/**
	 * Constructor.
	 * 
	 * @param jwk {@link JsonObject}
	 */
	JsonKeyReference(JsonValue jwk) {
		super(jwk);
		keyId = IuJson.get(jwk.asJsonObject(), "kid", IuJsonAdapter.of(String.class));
		algorithm = IuJson.get(jwk.asJsonObject(), "alg", CryptJsonAdapters.ALG);
	}

	@Override
	public String getKeyId() {
		return keyId;
	}

	@Override
	public Algorithm getAlgorithm() {
		return algorithm;
	}

	@Override
	public int hashCode() {
		return IuObject.hashCodeSuper(super.hashCode(), keyId, algorithm);
	}

	@Override
	public boolean equals(Object obj) {
		if (!super.equals(obj))
			return false;
		JsonKeyReference<?> other = (JsonKeyReference<?>) obj;
		return IuObject.equals(keyId, other.keyId) //
				&& IuObject.equals(algorithm, other.algorithm);
	}

	/**
	 * Adds serialized JWK attributes to a JSON object builder.
	 * 
	 * @param jwkBuilder {@link JsonObjectBuilder}
	 * @return jwkBuilder
	 */
	@Override
	JsonObjectBuilder serializeTo(JsonObjectBuilder jwkBuilder) {
		IuJson.add(jwkBuilder, "kid", keyId);
		IuJson.add(jwkBuilder, "alg", () -> algorithm, CryptJsonAdapters.ALG);
		super.serializeTo(jwkBuilder);
		return jwkBuilder;
	}

	/**
	 * Determines whether or not the known components of this key match the known
	 * components of another key.
	 * 
	 * @param key {@link WebKey}
	 * @return true if all non-null components of both keys match
	 */
	@Override
	boolean represents(R key) {
		final var ref = (JsonKeyReference<R>) key;
		return IuObject.represents(keyId, ref.keyId) //
				&& IuObject.represents(algorithm, ref.algorithm);
	}

}
