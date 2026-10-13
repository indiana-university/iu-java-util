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
package iu.jwt.spi;

import edu.iu.crypt.WebKey;
import edu.iu.jwt.WebToken;
import edu.iu.jwt.WebTokenBuilder;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.bind.serializer.JsonbSerializer;

/**
 * JWT SPI interface.
 */
public interface IuJwtSpi {

	/**
	 * Gets the {@link Jsonb} instance that converts token claims.
	 *
	 * @return {@link Jsonb}
	 * @see WebToken#jsonb()
	 */
	Jsonb getJsonb();

	/**
	 * Registers a JSON-B adapter for claim values.
	 *
	 * @param adapter {@link JsonbAdapter}
	 * @see WebToken#registerAdapter(JsonbAdapter)
	 */
	void registerAdapter(JsonbAdapter<?, ?> adapter);

	/**
	 * Registers a JSON-B serializer for claim values.
	 *
	 * @param serializer {@link JsonbSerializer}
	 * @see WebToken#registerSerializer(JsonbSerializer)
	 */
	void registerSerializer(JsonbSerializer<?> serializer);

	/**
	 * Registers a JSON-B deserializer for claim values.
	 *
	 * @param deserializer {@link JsonbDeserializer}
	 * @see WebToken#registerDeserializer(JsonbDeserializer)
	 */
	void registerDeserializer(JsonbDeserializer<?> deserializer);

	/**
	 * Creates a new {@link WebTokenBuilder} instance.
	 * 
	 * @return {@link WebTokenBuilder}
	 */
	WebTokenBuilder getJwtBuilder();

	/**
	 * Verifies the signature on a JWT and returns parsed claim values.
	 * 
	 * @param jwt       compact JWT serialization
	 * @param issuerKey key to use for verifying the token signature
	 * @return parsed claim values
	 */
	WebToken verifyJwt(String jwt, WebKey issuerKey);

	/**
	 * Decrypts and verifies the signature on am encrypted JWT, and returns parsed
	 * claim values.
	 * 
	 * @param jwt        compact JWT serialization
	 * @param issuerKey  key to use for verifying the token signature
	 * @param decryptKey key to use for decrypting the token
	 * @return parsed claim values
	 */
	WebToken decryptAndVerifyJwt(String jwt, WebKey issuerKey, WebKey decryptKey);

}
