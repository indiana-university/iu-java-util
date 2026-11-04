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
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;

import edu.iu.IuObject;
import edu.iu.IuText;
import edu.iu.client.IuJsonProperties;
import edu.iu.crypt.WebSignature;
import edu.iu.crypt.WebSignedPayload;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.json.bind.serializer.SerializationContext;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;

/**
 * JSON Web Signature (JWS) implementation class.
 */
public class JwsSignedPayload implements WebSignedPayload {
	static {
		IuObject.assertNotOpen(JwsSignedPayload.class);
	}

	private static final Set<String> GENERAL = Set.of("payload", "signatures");

	private static final Set<String> FLATTENED = Set.of("payload", "protected", "header", "signature");

	/**
	 * {@link JsonbDeserializer} handle method.
	 *
	 * <p>
	 * Reads either JSON serialization: general, with a "signatures" array, or
	 * flattened, with a single signature's members at the top level.
	 * </p>
	 *
	 * @param parser  {@link JsonParser}
	 * @param context {@link DeserializationContext}
	 * @param type    {@link Type}
	 * @return parsed JWS
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc7515#section-7.2">RFC-7515
	 *      JWS Section 7.2</a>
	 */
	static JwsSignedPayload deserialize(JsonParser parser, DeserializationContext context, Type type) {
		if (Event.VALUE_NULL.equals(parser.currentEvent()))
			return null;

		final var jws = context.deserialize(IuJsonProperties.class, parser);
		final var payload = jws.get("payload", byte[].class);
		if (payload == null)
			throw new JsonbException("missing payload");

		final Queue<Jws> signatures = new ArrayDeque<>();
		if (jws.containsKey("signatures")) {
			jws.requireOnly(GENERAL);
			for (final var signature : jws.get("signatures", WebSignature[].class))
				signatures.add((Jws) signature);
			if (signatures.isEmpty())
				throw new JsonbException("at least one signature is required");
		} else
			signatures.add(Jws.of(jws.requireOnly(FLATTENED)));

		return new JwsSignedPayload(payload, signatures);
	}

	private final byte[] payload;
	private final Iterable<Jws> signatures;

	/**
	 * Constructor.
	 * 
	 * @param payload    message payload
	 * @param signatures signatures
	 */
	JwsSignedPayload(byte[] payload, Iterable<Jws> signatures) {
		this.payload = payload;
		this.signatures = signatures;
	}

	@Override
	public byte[] getPayload() {
		return payload;
	}

	@Override
	public Iterable<Jws> getSignatures() {
		return signatures;
	}

	@Override
	public String compact() {
		final Iterator<Jws> signatureIterator = signatures.iterator();
		final Jws signature = signatureIterator.next();
		if (signatureIterator.hasNext())
			throw new IllegalStateException("Must have only one signature to use compact serialization");

		return signature.getSignatureInput(payload) + '.' + IuText.base64Url(signature.getSignature());
	}

	/**
	 * {@link JsonbSerializer} handle method.
	 * 
	 * @param generator {@link JsonGenerator}
	 * @param context   {@link SerializationContext}
	 */
	void serialize(JsonGenerator generator, SerializationContext context) {
		generator.writeStartObject();
		context.serialize("payload", payload, generator);

		// flattened for a single signature, general otherwise
		final var signatureIterator = signatures.iterator();
		final var signature = signatureIterator.next();
		if (signatureIterator.hasNext())
			context.serialize("signatures", signatures, generator);
		else
			signature.serializeMembers(generator, context);
		generator.writeEnd();
	}

	@Override
	public String toString() {
		return CryptJsonAdapters.JSONB.toJson(this);
	}

}
