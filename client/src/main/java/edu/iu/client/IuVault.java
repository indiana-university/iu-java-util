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
package edu.iu.client;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.IuRuntimeEnvironment;
import jakarta.json.JsonObject;

/**
 * Provides access to secrets stored in HashiCorp Vault.
 * 
 */
public class IuVault {
	static {
		IuObject.assertNotOpen(IuVault.class);
	}

	/**
	 * Singleton instance configured at class initialization time.
	 * 
	 * <p>
	 * Expects {@link System#getProperty(String) system properties} (or
	 * {@link System#getenv(String)}) variables if missing.
	 * </p>
	 * 
	 * <p>
	 * Will be null if vault.secrets is not populated.
	 * </p>
	 * 
	 * <dl>
	 * <dt>vault.secrets (VAULT_SECRETS)</dt>
	 * <dd>Comma-separated list of secrets to read from the Vault K/V store</dd>
	 * <dt>vault.endpoint (VAULT_ENDPOINT)</dt>
	 * <dd>Base URL for a Vault K/V store</dd>
	 * <dt>vault.token (VAULT_TOKEN)</dt>
	 * <dd>Access token for use with Vault, i.e., in development. If not set,
	 * vault.loginEndpoint, vault.roleId, and vault.secretId <em>must</em> be
	 * provided, i.e., for use by a CI/CD environment. If vault.token is set, the
	 * approle properties will be ignored.</dd>
	 * <dt>vault.loginEndpoint (VAULT_LOGIN_ENDPOINT)</dt>
	 * <dd>URL for the Vault approle login endpoint, for use when vault.token is not
	 * set.</dd>
	 * <dt>vault.roleId (VAULT_ROLE_ID)</dt>
	 * <dd>Vault approle Role ID, for use when vault.token is not set.</dd>
	 * <dt>vault.secretId (VAULT_SECRET_ID)</dt>
	 * <dd>Vault approle Secret ID, for use when vault.token is not set.</dd>
	 * </dl>
	 * 
	 * <p>
	 * If the system property {@code vault.token} or environment variable
	 * {@code VAULT_TOKEN} are populated, then approle properties will be skipped
	 * </p>
	 */
	public static IuVault RUNTIME;

	static {
		final var secrets = IuRuntimeEnvironment.envOptional("iu.vault.secrets", a -> a.split(","));
		if (secrets == null)
			RUNTIME = null;
		else {
			final var endpoint = IuRuntimeEnvironment.env("iu.vault.endpoint", URI::create);
			final var token = IuRuntimeEnvironment.envOptional("iu.vault.token");
			if (token != null)
				RUNTIME = new IuVault(endpoint, secrets, token);
			else {
				final var loginEndpoint = IuRuntimeEnvironment.env("iu.vault.loginEndpoint", URI::create);
				final var roleId = IuRuntimeEnvironment.env("iu.vault.roleId");
				final var secretId = IuRuntimeEnvironment.env("iu.vault.secretId");
				RUNTIME = new IuVault(endpoint, secrets, loginEndpoint, roleId, secretId);
			}
		}
	}

	/**
	 * Determines whether or not the RUNTIME Vault is configured.
	 * <p>
	 * May be used to selectively disable unit tests. For example:
	 * </p>
	 * 
	 * <pre>
	 * &#64;EnabledIf("edu.iu.test.VaultProperties#isConfigured")
	 * </pre>
	 * 
	 * @return true if Vault is configured; else false
	 */
	public static boolean isConfigured() {
		return RUNTIME != null;
	}

	private final Map<String, JsonObject> secrets = new HashMap<>();
	private final URI endpoint;
	private final String[] secretNames;
	private final String token;
	private final URI loginEndpoint;
	private final String roleId;
	private final String secretId;

	/**
	 * Constructor.
	 * 
	 * @param endpoint    Vault K/V endpoint
	 * @param secretNames Secret names to load, collisions prefer first found
	 * @param token       Bearer token
	 */
	public IuVault(URI endpoint, String[] secretNames, String token) {
		this.endpoint = endpoint;
		this.secretNames = secretNames;
		this.token = token;
		this.loginEndpoint = null;
		this.roleId = null;
		this.secretId = null;
	}

	/**
	 * Constructor.
	 * 
	 * @param endpoint      Vault K/V endpoint
	 * @param secretNames   Secret names to load, collisions prefer first found
	 * @param loginEndpoint Vault approle login endpoint
	 * @param roleId        Role ID
	 * @param secretId      Secret ID
	 */
	public IuVault(URI endpoint, String[] secretNames, URI loginEndpoint, String roleId, String secretId) {
		this.endpoint = endpoint;
		this.secretNames = secretNames;
		this.token = null;
		this.loginEndpoint = loginEndpoint;
		this.roleId = roleId;
		this.secretId = secretId;
	}

	/**
	 * Reads a property value from a Vault secret.
	 * 
	 * @param <T>     value type
	 * @param name    property name
	 * @param adapter converts the property value to the value type
	 * @return property value
	 */
	public <T> T get(String name, IuJsonAdapter<T> adapter) {
		for (String secret : secretNames) {
			final var data = getSecret(secret);
			if (data.containsKey(name))
				return adapter.fromJson(data.get(name));
		}

		throw new IllegalArgumentException(
				name + " not found in Vault using " + endpoint + "/data/" + Arrays.toString(secretNames));
	}

	/**
	 * Reads a property value from a Vault secret.
	 * 
	 * @param name property name
	 * @return property value
	 */
	public String get(String name) {
		return get(name, IuJsonAdapter.of(String.class));
	}

	private JsonObject getSecret(String secret) {
		var cachedSecret = secrets.get(secret);
		if (cachedSecret != null)
			return cachedSecret;

		final var data = IuException.unchecked(
				() -> IuHttp.send(URI.create(endpoint + "/data/" + secret), this::authorize, IuHttp.READ_JSON_OBJECT))
				.getJsonObject("data").getJsonObject("data");

		synchronized (secrets) {
			secrets.put(secret, data);
		}

		return data;
	}

	private void approle(HttpRequest.Builder dataRequestBuilder) {
		final var payload = IuJson.object();
		payload.add("role_id", Objects.requireNonNull(roleId, "Missing vault.roleId"));
		payload.add("secret_id", Objects.requireNonNull(secretId, "Missing vault.loginEndpoint"));
		dataRequestBuilder.POST(BodyPublishers.ofString(payload.build().toString()));
		dataRequestBuilder.header("Content-Type", "application/json; charset=utf-8");
	}

	private void authorize(HttpRequest.Builder dataRequestBuilder) {
		var accessToken = token;

		if (accessToken == null)
			accessToken = IuException
					.unchecked(
							() -> IuHttp
									.send(Objects.requireNonNull(loginEndpoint, "Missing vault.loginEndpoint"),
											this::approle, IuHttp.READ_JSON_OBJECT)
									.getJsonObject("auth").getString("client_token"));

		dataRequestBuilder.header("Authorization", "Bearer " + accessToken);
	}

}
