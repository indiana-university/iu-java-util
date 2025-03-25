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
package iu.client;

import java.lang.reflect.Type;
import java.net.URI;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import edu.iu.IuObject;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuVaultKeyedValue;
import edu.iu.client.IuVaultMetadata;
import edu.iu.client.IuVaultSecret;
import jakarta.json.JsonObject;

/**
 * Implements {@link IuVaultSecret}.
 */
final class VaultSecret implements IuVaultSecret {

	private final String name;
	private final URI uri;
	private final Supplier<JsonObject> dataSupplier;
	private final Supplier<JsonObject> metadataSupplier;
	private final Consumer<JsonObject> mergePatchConsumer;
	private final Function<Type, IuJsonAdapter<?>> valueAdapter;

	/**
	 * Constructor.
	 *
	 * @param name               secret name
	 * @param uri                data URI
	 * @param dataSupplier       supplies the current version of the secret
	 * @param metadataSupplier   supplies metadata for the current version of the
	 *                           secret, supplies null for cubbyhole secrets
	 * @param mergePatchConsumer consumes a merge patch for updating the secret
	 * @param valueAdapter       value adapter function
	 */
	VaultSecret(String name, URI uri, Supplier<JsonObject> dataSupplier, Supplier<JsonObject> metadataSupplier,
			Consumer<JsonObject> mergePatchConsumer, Function<Type, IuJsonAdapter<?>> valueAdapter) {
		this.name = name;
		this.uri = uri;
		this.dataSupplier = dataSupplier;
		this.metadataSupplier = metadataSupplier;
		this.mergePatchConsumer = mergePatchConsumer;
		this.valueAdapter = valueAdapter;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public JsonObject getData() {
		return dataSupplier.get();
	}

	@Override
	public IuVaultMetadata getMetadata() {
		return IuObject.convert(metadataSupplier.get(), a -> IuJson.wrap(a, IuVaultMetadata.class, valueAdapter));
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> IuVaultKeyedValue<T> get(String key, Class<T> type) {
		final var value = getData().get(key);
		if (value == null)
			return null;
		else
			return new VaultKeyedValue<>(this, key, (T) valueAdapter.apply(type).fromJson(value), type);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> void set(String key, T value, Class<T> type) {
		final IuJsonAdapter<T> adapter = (IuJsonAdapter<T>) valueAdapter.apply(type);
		final var jsonValue = adapter.toJson(value);

		final var builder = IuJson.object();
		builder.add(key, jsonValue);

		mergePatchConsumer.accept(builder.build());
	}

	@Override
	public String toString() {
		return "VaultSecret [" + uri + "]";
	}

	@Override
	public int hashCode() {
		return IuObject.hashCode(uri);
	}

	@Override
	public boolean equals(Object obj) {
		if (!IuObject.typeCheck(this, obj))
			return false;
		VaultSecret other = (VaultSecret) obj;
		return Objects.equals(uri, other.uri);
	}

}
