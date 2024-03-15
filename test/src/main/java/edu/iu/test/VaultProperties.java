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
package edu.iu.test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import edu.iu.IuException;
import edu.iu.IuRuntimeEnvironment;
import edu.iu.IuStream;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

/**
 * Provides access to secrets stored in HashiCorp Vault.
 * <p>
 * Expects {@link System#getProperty(String) system properties} (or
 * {@link System#getenv(String)}) variables if missing:
 * </p>
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
public class VaultProperties {

	private static final Map<String, JsonObject> SECRETS = new HashMap<>();

	/**
	 * Determines whether or not Vault is configured.
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
		return IuRuntimeEnvironment.envOptional("vault.secrets") != null;
	}

	/**
	 * Reads a property value from a Vault secret. All value types are returned as
	 * String.
	 * 
	 * @param name property name
	 * @return property value
	 */
	public static String getProperty(String name) {
		final var secrets = IuRuntimeEnvironment.env("vault.secrets").split(",");
		for (String secret : secrets) {
			final var data = IuException.unchecked(() -> getSecret(secret));
			if (data.containsKey(name)) {
				JsonValue value = data.get(name);
				if (value.getValueType() == JsonValue.ValueType.STRING)
					return ((JsonString) value).getString();

				return value.toString();
			}
		}
		throw new IllegalArgumentException(name + " not found in Vault using "
				+ IuRuntimeEnvironment.env("vault.endpoint") + "/data/" + Arrays.toString(secrets));
	}

	private static JsonObject getSecret(String secret) throws Exception {
		var cachedSecret = SECRETS.get(secret);
		if (cachedSecret != null)
			return cachedSecret;

		final var request = HttpRequest.newBuilder().GET() //
				.uri(new URI(IuRuntimeEnvironment.env("vault.endpoint") + "/data/" + secret)) //
				.header("Authorization", "Bearer " + getAccessToken()) //
				.build();

		final var response = HttpClient.newHttpClient().send(request, BodyHandlers.ofInputStream());

		final var status = response.statusCode();
		if (status != 200)
			throw new IllegalStateException("Unexpected response from Vault; status=" + status + "; request=" + request
					+ "; body " + new String(IuStream.read(response.body()), "UTF-8"));

		JsonObject content;
		try (final var body = response.body()) {
			content = Json.createReader(body).readObject();
		}

		final var data = content.getJsonObject("data").getJsonObject("data");
		synchronized (SECRETS) {
			SECRETS.put(secret, data);
		}

		return data;
	}

	private static String getAccessToken() throws Exception {
		var accessToken = IuRuntimeEnvironment.envOptional("vault.token");
		if (accessToken == null) {
			final var loginEndpoint = new URI(IuRuntimeEnvironment.env("vault.loginEndpoint"));

			JsonObjectBuilder payload = Json.createObjectBuilder();
			payload.add("role_id", IuRuntimeEnvironment.env("vault.roleId"));
			payload.add("secret_id", IuRuntimeEnvironment.env("vault.secretId"));

			final var requestBuilder = HttpRequest.newBuilder() //
					.uri(loginEndpoint) //
					.POST(BodyPublishers.ofString(payload.build().toString())) //
					.header("Content-Type", "application/json; charset=utf-8");

			JsonObject content;
			try (final var response = HttpClient.newHttpClient()
					.send(requestBuilder.build(), BodyHandlers.ofInputStream()).body()) {
				content = Json.createReader(response).readObject();
			}

			accessToken = content.getJsonObject("auth").getString("client_token");
		}
		return accessToken;
	}

	private VaultProperties() {
	}

}
