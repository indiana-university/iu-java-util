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
package iu.redis.lettuce;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.logging.Level;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import edu.iu.test.IuTestLogger;
import edu.iu.client.IuJson;
import edu.iu.client.IuVault;
import edu.iu.redis.IuRedisConfiguration;


@EnabledIf("edu.iu.client.IuVault#isConfigured")
@SuppressWarnings("javadoc")
public class LettuceConnectionIT {

	@BeforeAll
	public static void setupClass() {
		// Connection info in Vault: ua-vpit/enterprise-systems/eshrs-jeecontrol/kv/managed/jeecontrol/unt/cache
		
		//final var config = IuJson.parse(keyedValue).asJsonObject();
		/*AuthConfig.registerInterface("realm", IuSamlServiceProviderMetadata.class, IuVault.RUNTIME);
		AuthConfig.registerInterface(IuPrivateKeyPrincipal.class);
		final var realm = AuthConfig.load(IuSamlServiceProviderMetadata.class, REALM);
		postUri = realm.getAcsUris().iterator().next();

		AuthConfig.register(new PkiVerifier(realm.getIdentity()));

		final var provider = new SamlServiceProvider(postUri, REALM, realm);
		AuthConfig.register(provider);
		AuthConfig.seal();

		final var identity = SamlServiceProvider.serviceProviderIdentity(realm);
		System.out.println("Verified SAML Service Provider " + identity);*/

	}
	
	@Test
	public void testConnection() {
		IuTestLogger.allow("", Level.FINE);
		IuVault vault = IuVault.RUNTIME;
		final var host = vault.get("cache/spring.redis.host").getValue();
		String port = vault.get("cache/spring.redis.port").getValue();
		String password = vault.get("cache/spring.redis.password").getValue();
		
		IuRedisConfiguration config = new IuRedisConfiguration() {
			@Override
			public String getHost() {
				return host;
			}

			@Override
			public String getPort() {
				return port;
			}

			@Override
			public String getPassword() {
				return password;
			}

			@Override
			public String getUsername() {
				return "test_user";
			}
		};
		final var connection = new LettuceConnection(config);
		assertNotNull(connection);
		
	}
}
