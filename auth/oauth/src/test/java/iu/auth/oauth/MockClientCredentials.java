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
package iu.auth.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.net.http.HttpRequest.Builder;
import java.util.Set;

import javax.security.auth.Subject;

import edu.iu.IdGenerator;
import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.IuAuthenticationException;
import iu.auth.principal.PrincipalVerifier;
import iu.auth.principal.PrincipalVerifierRegistry;

@SuppressWarnings("javadoc")
final class MockClientCredentials implements IuApiCredentials {

	private static final long serialVersionUID = 1L;

	private final class Verifier implements PrincipalVerifier<MockClientCredentials> {
		@Override
		public Class<MockClientCredentials> getType() {
			return MockClientCredentials.class;
		}

		@Override
		public String getRealm() {
			return clientId;
		}

		@Override
		public boolean isAuthoritative() {
			return true;
		}

		@Override
		public void verify(MockClientCredentials id, String realm) throws IuAuthenticationException {
			assertEquals(realm, clientId);
			assertSame(MockClientCredentials.this, id);
		}
	}

	private final String clientId = IdGenerator.generateId();

	MockClientCredentials() {
		PrincipalVerifierRegistry.registerVerifier(new Verifier());
	}

	@Override
	public String getName() {
		return clientId;
	}

	@Override
	public Subject getSubject() {
		return new Subject(true, Set.of(this), Set.of(), Set.of());
	}

	@Override
	public void applyTo(Builder httpRequestBuilder) throws IuAuthenticationException {
		httpRequestBuilder.header("Authorization", "Mock " + clientId);
	}

}
