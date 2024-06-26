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

import java.lang.reflect.Type;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

import edu.iu.IuCacheMap;
import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.IuRuntimeEnvironment;
import edu.iu.client.HttpException;
import edu.iu.client.HttpResponseHandler;
import edu.iu.client.IuHttp;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuVault;
import edu.iu.client.IuVaultSecret;
import jakarta.json.JsonObject;

/**
 * Provides access to secrets stored in HashiCorp Vault.
 * 
 */
public final class Vault implements IuVault {
	static {
		IuObject.assertNotOpen(Vault.class);
	}

	private static final Logger LOG = Logger.getLogger(Vault.class.getName());

	/**
	 * Implements {@link IuVault#of(Properties, Function)}.
	 * 
	 * @param properties   optional property overrides
	 * @param valueAdapter
	 * @return {@link Vault} instance
	 */
	public static Vault of(Properties properties, Function<Type, IuJsonAdapter<?>> valueAdapter) {
		final var endpoint = prop(properties, "iu.vault.endpoint", URI::create);
		if (endpoint == null) {
			if (properties == null)
				return null;
			else
				throw new NullPointerException("Missing iu.vault.endpoint");
		}

		final var cacheTtl = prop(properties, "iu.vault.cacheTtl", Duration::parse);
		final Map<String, JsonObject> secretCache;
		if (cacheTtl == null)
			secretCache = null;
		else
			secretCache = new IuCacheMap<>(cacheTtl);

		final var secretNames = prop(properties, "iu.vault.secrets", a -> a.split(","));
		final var token = prop(properties, "iu.vault.token", a -> a);
		final var cubbyhole = "true".equals(prop(properties, "iu.vault.cubbyhole", a -> a));
		if (token != null)
			return new Vault(endpoint, secretNames, token, cubbyhole, valueAdapter, secretCache);
		else {
			final var loginEndpoint = Objects.requireNonNull( //
					prop(properties, "iu.vault.loginEndpoint", URI::create),
					"Missing iu.vault.loginEndpoint or iu.vault.token");

			final var roleId = Objects.requireNonNull( //
					prop(properties, "iu.vault.roleId", a -> a), "Missing iu.vault.roleId");
			final var secretId = Objects.requireNonNull( //
					prop(properties, "iu.vault.secretId", a -> a), "Missing iu.vault.secretId");

			return new Vault(endpoint, secretNames, loginEndpoint, roleId, secretId, cubbyhole, valueAdapter,
					secretCache);
		}
	}

	/**
	 * Determines if the {@link IuVault#RUNTIME} is configured and will be non-null.
	 * 
	 * @return true if {@link IuVault#RUNTIME} is configured; else false
	 */
	public static boolean isConfigured() {
		return prop(null, "iu.vault.endpoint", URI::create) != null;
	}

	/**
	 * Reads a configuration property.
	 * 
	 * @param <T>                 property type
	 * @param properties          runtime environment overrides
	 * @param name                property name
	 * @param textToValueFunction conversion function
	 * @return property value
	 */
	static <T> T prop(Properties properties, String name, Function<String, T> textToValueFunction) {
		if (properties != null) {
			final var propertyValue = properties.getProperty(name);
			if (propertyValue != null)
				return textToValueFunction.apply(propertyValue);
		}

		return IuRuntimeEnvironment.envOptional(name, textToValueFunction);
	}

	private final URI endpoint;
	private final String[] secretNames;
	private final URI loginEndpoint;
	private final String roleId;
	private final String secretId;
	private final boolean cubbyhole;
	private final Function<Type, IuJsonAdapter<?>> valueAdapter;
	private final Map<String, JsonObject> secretCache;

	private String token;
	private Instant tokenExpires;

	private Vault(URI endpoint, String[] secretNames, String token, boolean cubbyhole,
			Function<Type, IuJsonAdapter<?>> valueAdapter, Map<String, JsonObject> secretCache) {
		this.endpoint = endpoint;
		this.secretNames = secretNames;
		this.token = token;
		this.loginEndpoint = null;
		this.roleId = null;
		this.secretId = null;
		this.cubbyhole = cubbyhole;
		this.valueAdapter = valueAdapter;
		this.secretCache = secretCache;
	}

	private Vault(URI endpoint, String[] secretNames, URI loginEndpoint, String roleId, String secretId,
			boolean cubbyhole, Function<Type, IuJsonAdapter<?>> valueAdapter, Map<String, JsonObject> secretCache) {
		this.endpoint = endpoint;
		this.secretNames = secretNames;
		this.token = null;
		this.loginEndpoint = loginEndpoint;
		this.roleId = roleId;
		this.secretId = secretId;
		this.cubbyhole = cubbyhole;
		this.valueAdapter = valueAdapter;
		this.secretCache = secretCache;
	}

	@Override
	public String[] list() {
		if (secretNames == null)
			throw new UnsupportedOperationException();

		final Queue<String> list = new ArrayDeque<>();
		for (String secretName : secretNames)
			list.addAll(getSecret(secretName).getData().keySet());

		return list.toArray(String[]::new);
	}

	@Override
	public String get(String name) {
		return get(name, String.class);
	}

	@Override
	public <T> T get(String name, Class<T> type) {
		if (secretNames == null)
			throw new UnsupportedOperationException();

		for (String secretName : secretNames) {
			final var secret = getSecret(secretName);
			if (secret.getData().containsKey(name))
				return secret.get(name, type);
		}

		throw new IllegalArgumentException(
				name + " not found in Vault using " + endpoint + '/' + Arrays.toString(secretNames));
	}

	@Override
	public IuVaultSecret getSecret(String secret) {
		class Ref {
			JsonObject data;
		}

		final Ref ref;
		final Supplier<JsonObject> dataSupplier;
		final Supplier<JsonObject> metadataSupplier;

		final Function<JsonObject, JsonObject> convertData;
		final Function<JsonObject, JsonObject> convertMetadata;
		if (cubbyhole) {
			convertData = a -> a;
			convertMetadata = a -> null;
		} else {
			// TODO: support launchpad through configuration
			if (secret.startsWith("managed/")) {
				final var lastSlash = secret.lastIndexOf('/');
				final var prefix = secret.substring(lastSlash + 1) + '/';
				convertData = a -> {
					final var b = IuJson.object();
					for (final var e : a.getJsonObject("data").entrySet())
						b.add(prefix + e.getKey(), e.getValue());
					return b.build();
				};
			} else
				convertData = a -> a.getJsonObject("data");
			convertMetadata = a -> a.getJsonObject("metadata");
		}

		if (secretCache == null) {
			ref = new Ref();
			ref.data = readSecret(secret);
			dataSupplier = () -> convertData.apply(ref.data);
			metadataSupplier = () -> convertMetadata.apply(ref.data);
		} else {
			ref = null;
			dataSupplier = () -> convertData.apply(readSecretUsingCache(secret));
			metadataSupplier = () -> convertMetadata.apply(readSecretUsingCache(secret));
		}

		final Consumer<JsonObject> mergePatchConsumer;
		if (secret.startsWith("managed/"))
			mergePatchConsumer = a -> {
				// TODO: support launchpad through configuration
				throw new UnsupportedOperationException();
			};
		else
			mergePatchConsumer = mergePatch -> IuException.unchecked(() -> {
				final var data = dataSupplier.get();
				final var metadata = metadataSupplier.get();

				final var updatedData = IuJson.PROVIDER.createMergePatch(mergePatch).apply(data).asJsonObject();

				final String dataRequestPayload;
				if (cubbyhole)
					dataRequestPayload = updatedData.toString();
				else {
					final var dataRequestPayloadBuilder = IuJson.object();
					dataRequestPayloadBuilder.add("options", IuJson.object().add("cas", metadata.getInt("version")));
					dataRequestPayloadBuilder.add("data", updatedData);
					dataRequestPayload = dataRequestPayloadBuilder.build().toString();
				}

				final var dataUri = dataUri(secret);
				final HttpResponseHandler<?> responseHandler;
				if (cubbyhole)
					responseHandler = IuHttp.NO_CONTENT;
				else
					responseHandler = IuHttp.READ_JSON_OBJECT;

				IuHttp.send(dataUri, rb -> {
					rb.POST(BodyPublishers.ofString(dataRequestPayload));
					this.authorize(rb);
				}, responseHandler);

				LOG.config(() -> "vault:set:" + dataUri + ":" + mergePatch.keySet());

				final var updated = readSecret(secret);
				if (secretCache == null)
					ref.data = updated;
				else
					secretCache.put(secret, updated);
			});

		return new VaultSecret(dataSupplier, metadataSupplier, mergePatchConsumer, valueAdapter);
	}

	private JsonObject readSecretUsingCache(String secret) {
		var data = secretCache.get(secret);
		if (data == null)
			secretCache.put(secret, data = readSecret(secret));
		return data;
	}

	/**
	 * Reads a secret using the Vault API.
	 * 
	 * @param secret secret name
	 * @return parsed secret
	 */
	JsonObject readSecret(String secret) {
		return IuException.unchecked(() -> {
			try {
				return IuHttp.send(dataUri(secret), this::authorize, IuHttp.READ_JSON_OBJECT).getJsonObject("data");
			} catch (HttpException e) {
				if (e.getResponse().statusCode() == 404)
					if (cubbyhole)
						return IuJson.object().build();
					else
						return IuJson.object().add("data", IuJson.object()).build();
				else
					throw e;
			}
		});
	}

	/**
	 * Creates a data URI for calling the Vault API.
	 * 
	 * @param secret secret name
	 * @return Vault API data {@link URI}
	 */
	URI dataUri(String secret) {
		if (!secret.matches("[\\w\\/\\-]+"))
			throw new IllegalArgumentException("invalid secret name");

		final var sb = new StringBuilder();
		sb.append(endpoint);
		sb.append('/');
		if (cubbyhole)
			sb.append(secret);
		else
			sb.append(URLEncoder.encode(secret, StandardCharsets.UTF_8));
		return URI.create(sb.toString());
	}

	private void approle(HttpRequest.Builder requestBuilder) {
		final var payload = IuJson.object();
		payload.add("role_id", roleId);
		payload.add("secret_id", secretId);
		requestBuilder.header("Content-Type", "application/json;charset=utf-8");
		requestBuilder.POST(BodyPublishers.ofString(payload.build().toString()));
	}

	private void authorize(HttpRequest.Builder requestBuilder) {
		if (tokenExpires != null //
				&& Instant.now().isAfter(tokenExpires))
			token = null;

		if (token == null) {
			final var authResponse = IuException.unchecked( //
					() -> IuHttp.send(loginEndpoint, this::approle, IuHttp.READ_JSON_OBJECT) //
							.getJsonObject("auth"));

			tokenExpires = Instant.now().truncatedTo(ChronoUnit.SECONDS)
					.plusSeconds(authResponse.getInt("lease_duration", 0));
			token = authResponse.getString("client_token");
		}

		requestBuilder.header("X-Vault-Token", token);
	}

}
