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

import java.lang.reflect.Type;
import java.util.Properties;
import java.util.function.Function;

import iu.client.Vault;
import jakarta.json.JsonObject;

/**
 * Provides access to a HashiCorp Vault K/V secrets engine.
 * 
 * <p>
 * Properties for use with {@link #RUNTIME} or {@link #of(Properties, Function)}
 * are listed below.
 * </p>
 * 
 * <dl>
 * <dt>iu.vault.secrets (IU_VAULT_SECRETS)</dt>
 * <dd>Comma-separated list of secrets to read from the Vault K/V store</dd>
 * <dt>iu.vault.endpoint (IU_VAULT_ENDPOINT)</dt>
 * <dd>Base URL for a Vault K/V V2 engine API</dd>
 * <dt>iu.vault.token (IU_VAULT_TOKEN)</dt>
 * <dd>Access token for use with Vault, i.e., in development. If not set,
 * iu.vault.loginEndpoint, iu.vault.roleId, and iu.vault.secretId <em>must</em>
 * be provided, i.e., for use by a CI/CD environment. If iu.vault.token is set,
 * the approle properties will be ignored.</dd>
 * <dt>iu.vault.loginEndpoint (IU_VAULT_LOGIN_ENDPOINT)</dt>
 * <dd>URL for the Vault approle login endpoint, for use when iu.vault.token is
 * not set.</dd>
 * <dt>iu.vault.roleId (IU_VAULT_ROLE_ID)</dt>
 * <dd>Vault approle Role ID, for use when iu.vault.token is not set.</dd>
 * <dt>iu.vault.secretId (IU_VAULT_SECRET_ID)</dt>
 * <dd>Vault approle Secret ID, for use when iu.vault.token is not set.</dd>
 * <dt>iu.vault.cacheTtl (IU_VAULT_CACHE_TTL)</dt>
 * <dd>Secrets cache time to live; by default, secrets are not cached.</dd>
 * </dl>
 */
public interface IuVault {

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
	 * <dt>iu.vault.secrets (IU_VAULT_SECRETS)</dt>
	 * <dd>Comma-separated list of secrets to read from the Vault K/V store</dd>
	 * <dt>iu.vault.endpoint (IU_VAULT_ENDPOINT)</dt>
	 * <dd>Base URL for a Vault K/V store</dd>
	 * <dt>iu.vault.token (IU_VAULT_TOKEN)</dt>
	 * <dd>Access token for use with Vault, i.e., in development. If not set,
	 * vault.loginEndpoint, vault.roleId, and vault.secretId <em>must</em> be
	 * provided, i.e., for use by a CI/CD environment. If vault.token is set, the
	 * approle properties will be ignored.</dd>
	 * <dt>iu.vault.loginEndpoint (IU_VAULT_LOGIN_ENDPOINT)</dt>
	 * <dd>URL for the Vault approle login endpoint, for use when vault.token is not
	 * set.</dd>
	 * <dt>iu.vault.roleId (IU_VAULT_ROLE_ID)</dt>
	 * <dd>Vault approle Role ID, for use when vault.token is not set.</dd>
	 * <dt>iu.vault.secretId (IU_VAULT_SECRET_ID)</dt>
	 * <dd>Vault approle Secret ID, for use when vault.token is not set.</dd>
	 * </dl>
	 * 
	 * <p>
	 * If the system property {@code vault.token} or environment variable
	 * {@code VAULT_TOKEN} are populated, then approle properties will be skipped
	 * </p>
	 */
	public static final IuVault RUNTIME = Vault.of(null, IuJsonAdapter::of);

	/**
	 * Determines whether or not the RUNTIME Vault is configured.
	 * <p>
	 * May be used to selectively disable unit tests. For example:
	 * </p>
	 * 
	 * <pre>
	 * &#64;EnabledIf("edu.iu.client.IuVault#isConfigured")
	 * </pre>
	 * 
	 * @return true if Vault is configured; else false
	 */
	public static boolean isConfigured() {
		return Vault.isConfigured();
	}

	/**
	 * Gets an {@link IuVault} instance for a specific application scenario.
	 * 
	 * @param properties   {@link Properties}
	 * @param valueAdapter {@link IuJsonAdapter} type mapping function
	 * @return {@link IuVault}
	 */
	public static IuVault of(Properties properties, Function<Type, IuJsonAdapter<?>> valueAdapter) {
		return Vault.of(properties, valueAdapter);
	}

	/**
	 * Reads a property value.
	 * 
	 * @param name property name
	 * @return property value
	 */
	String get(String name);

	/**
	 * Reads a property value.
	 * 
	 * @param <T>  value type
	 * @param name property name
	 * @param type value class
	 * @return property value
	 */
	<T> T get(String name, Class<T> type);

	/**
	 * Gets a full K/V secret.
	 * 
	 * @param secret secret name
	 * @return {@link JsonObject}
	 */
	IuVaultSecret getSecret(String secret);

}
