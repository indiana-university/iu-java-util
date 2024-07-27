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
package edu.iu.auth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.config.IuAuthenticationRealm.Type;
import edu.iu.client.IuJson;
import edu.iu.client.IuVault;
import edu.iu.client.IuVaultKeyedValue;
import iu.auth.config.AuthConfig;

@SuppressWarnings("javadoc")
public class IuAuthenticationRealmTest {

	@SuppressWarnings("unchecked")
	@Test
	public void testVerify() {
		final var authId = IdGenerator.generateId();
		final var vault = mock(IuVault.class);
		final var value = mock(IuVaultKeyedValue.class);
		when(value.getValue()).thenReturn("{\"type\":\"pki\"}");
		when(vault.get("realm/" + authId)).thenReturn(value);
		AuthConfig.registerInterface("realm", IuPrivateKeyPrincipal.class, vault);
		AuthConfig.registerInterface("realm", IuCertificateAuthority.class, vault);

		final var realm = AuthConfig.load(IuPrivateKeyPrincipal.class, authId);
		assertInstanceOf(IuPrivateKeyPrincipal.class, realm);

		final var error = assertThrows(IllegalStateException.class,
				() -> AuthConfig.load(IuCertificateAuthority.class, authId));
		assertEquals("Invalid realm type for pki", error.getMessage());
	}

	@Test
	public void testJson() {
		for (final var t : Type.values()) {
			assertEquals(IuJson.string(t.code), Type.JSON.toJson(t));
		}
	}
	
}
