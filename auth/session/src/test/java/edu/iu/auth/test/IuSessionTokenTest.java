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
package edu.iu.auth.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.KeyPairGenerator;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.List;

import javax.security.auth.Subject;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.oauth.IuAuthorizationScope;
import edu.iu.auth.session.IuSessionHeader;
import edu.iu.auth.session.IuSessionProviderKey;
import edu.iu.auth.session.IuSessionProviderKey.Usage;
import edu.iu.auth.session.IuSessionToken;

@SuppressWarnings("javadoc")
public class IuSessionTokenTest {

	@Test
	public void testSerializesAsAccessToken() throws Exception {
		final var issuer = IdGenerator.generateId();
		final var ecKeygen = KeyPairGenerator.getInstance("EC");
		ecKeygen.initialize(new ECGenParameterSpec("secp256r1"));
		final var keyPair = ecKeygen.generateKeyPair();
		final var subject = new Subject();
		subject.getPrincipals().add(new Principal() {
			@Override
			public String getName() {
				return issuer;
			}
		});
		subject.getPrincipals().add(IuAuthorizationScope.of("session", issuer));
		subject.getPrivateCredentials().add(new IuSessionProviderKey() {
			@Override
			public String getId() {
				return "default";
			}

			@Override
			public Usage getUsage() {
				return Usage.SIGN;
			}

			@Override
			public Type getType() {
				return Type.EC_P256;
			}

			@Override
			public PublicKey getPublic() {
				return keyPair.getPublic();
			}

			@Override
			public PrivateKey getPrivate() {
				return keyPair.getPrivate();
			}
		});
		IuSessionToken.register(subject);

		final var audience = IdGenerator.generateId();
		final var principal = IdGenerator.generateId();
		final var header = mock(IuSessionHeader.class);
		when(header.getKeyId()).thenReturn("default");
		when(header.getSignatureAlgorithm()).thenReturn("ES256");
		when(header.getIssuer()).thenReturn(issuer);
		when(header.getAudience()).thenReturn(audience);
		when(header.getAuthorizedPrincipals()).thenReturn(List.of(new Principal() {
			@Override
			public String getName() {
				return principal;
			}
		}, IuAuthorizationScope.of("session", issuer)));

		final var token = IuSessionToken.create(header);
		assertEquals(token, IuSessionToken.authorize(token.getAccessToken()));
	}

}
