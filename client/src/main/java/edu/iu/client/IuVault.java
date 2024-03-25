/*
- * Copyright © 2024 Indiana University
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
 * <p>
 * Expects {@link System#getProperty(String) system properties} (or
 * {@link System#getenv(String)}) variables if missing:
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
public class IuVault {
	static {
		IuObject.assertNotOpen(IuVault.class);
	}

	private static final Map<String, JsonObject> SECRETS = new HashMap<>();
	private static final URI ENDPOINT = IuRuntimeEnvironment.envOptional("iu.vault.endpoint", URI::create);
	private static final String[] SECRET_NAMES = IuRuntimeEnvironment.envOptional("iu.vault.secrets",
			a -> a.split(","));
	private static final String TOKEN = IuRuntimeEnvironment.envOptional("iu.vault.token");
	private static final URI LOGINENDPOINT = IuRuntimeEnvironment.envOptional("iu.vault.loginEndpoint", URI::create);
	private static final String ROLEID = IuRuntimeEnvironment.envOptional("iu.vault.roleId");
	private static final String SECRETID = IuRuntimeEnvironment.envOptional("iu.vault.secretId");

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
		return SECRET_NAMES != null;
	}

	/**
	 * Reads a property value from a Vault secret.
	 * 
	 * @param <T>                 value type
	 * @param name                property name
	 * @param adapter converts the property value to the value type
	 * @return property value
	 */
	public static <T> T get(String name, IuJsonAdapter<T> adapter) {
		if (SECRET_NAMES != null)
			for (String secret : SECRET_NAMES) {
				final var data = getSecret(secret);
				if (data.containsKey(name))
					return adapter.fromJson(data.get(name));
			}

		throw new IllegalArgumentException(
				name + " not found in Vault using " + ENDPOINT + "/data/" + Arrays.toString(SECRET_NAMES));
	}

	/**
	 * Reads a property value from a Vault secret.
	 * 
	 * @param name property name
	 * @return property value
	 */
	public static String get(String name) {
		return get(name, IuJsonAdapter.of(String.class));
	}

	private static JsonObject getSecret(String secret) {
		var cachedSecret = SECRETS.get(secret);
		if (cachedSecret != null)
			return cachedSecret;

		final var data = IuException
				.unchecked(() -> IuHttp.send(URI.create(ENDPOINT + "/data/" + secret), IuVault::authorize,
						IuHttp.validate(IuJson::parse, IuHttp.OK)))
				.asJsonObject().getJsonObject("data").getJsonObject("data");

		synchronized (SECRETS) {
			SECRETS.put(secret, data);
		}

		return data;
	}

	private static void approle(HttpRequest.Builder dataRequestBuilder) {
		final var payload = IuJson.object();
		payload.add("role_id", Objects.requireNonNull(ROLEID, "Missing vault.roleId"));
		payload.add("secret_id", Objects.requireNonNull(SECRETID, "Missing vault.loginEndpoint"));
		dataRequestBuilder.POST(BodyPublishers.ofString(payload.build().toString()));
		dataRequestBuilder.header("Content-Type", "application/json; charset=utf-8");
	}

	private static void authorize(HttpRequest.Builder dataRequestBuilder) {
		var accessToken = TOKEN;

		if (accessToken == null)
			accessToken = IuException.unchecked(() -> IuHttp
					.send(Objects.requireNonNull(LOGINENDPOINT, "Missing vault.loginEndpoint"), IuVault::approle,
							IuHttp.validate(IuJson::parse, IuHttp.OK))
					.asJsonObject().getJsonObject("auth").getString("client_token"));

		dataRequestBuilder.header("Authorization", "Bearer " + accessToken);
	}

	private IuVault() {
	}

}
