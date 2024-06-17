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
package iu.client;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Type;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuVaultMetadata;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

@SuppressWarnings("javadoc")
public class VaultSecretTest {

	@SuppressWarnings("unchecked")
	@Test
	public void testMetadata() {
		final var metadata = mock(JsonObject.class);
		final Function<Type, IuJsonAdapter<?>> valueAdapter = mock(Function.class);
		final var secret = new VaultSecret(null, () -> metadata, null, valueAdapter);
		try (final var mockJsonProxy = mockStatic(JsonProxy.class)) {
			secret.getMetadata();
			mockJsonProxy.verify(() -> JsonProxy.wrap(metadata, IuVaultMetadata.class, valueAdapter));
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testSet() {
		class ValueType {
		}

		final var key = IdGenerator.generateId();
		final var value = new ValueType();
		final var jsonValue = mock(JsonValue.class);
		final var mergePatch = mock(JsonObject.class);

		final var builder = mock(JsonObjectBuilder.class);
		when(builder.add(key, mergePatch)).thenReturn(builder);
		when(builder.build()).thenReturn(mergePatch);

		final var adapter = mock(IuJsonAdapter.class);
		when(adapter.toJson(value)).thenReturn(jsonValue);

		final Function<Type, IuJsonAdapter<?>> valueAdapter = mock(Function.class);
		when(valueAdapter.apply(ValueType.class)).thenReturn(adapter);

		final Consumer<JsonObject> mergePatchConsumer = mock(Consumer.class);
		final var secret = new VaultSecret(null, null, mergePatchConsumer, valueAdapter);
		try (final var mockJson = mockStatic(IuJson.class)) {
			when(IuJson.object()).thenReturn(builder);
			secret.set(key, value, ValueType.class);
			verify(mergePatchConsumer).accept(mergePatch);
		}
	}

}
