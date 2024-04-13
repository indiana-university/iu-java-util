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
package iu.auth.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.jwt.IuWebToken;
import edu.iu.auth.jwt.IuWebTokenIssuer;

@SuppressWarnings("javadoc")
public class IuWebTokenTest {

//	   The contents of the JOSE Header describe the cryptographic operations
//	   applied to the JWT Claims Set.  If the JOSE Header is for a JWS, the
//	   JWT is represented as a JWS and the claims are digitally signed or
//	   MACed, with the JWT Claims Set being the JWS Payload.  If the JOSE
//	   Header is for a JWE, the JWT is represented as a JWE and the claims
//	   are encrypted, with the JWT Claims Set being the plaintext encrypted
//	   by the JWE.  A JWT may be enclosed in another JWE or JWS structure to
//	   create a Nested JWT, enabling nested signing and encryption to be
//	   performed.
//
//	   A JWT is represented as a sequence of URL-safe parts separated by
//	   period ('.') characters.  Each part contains a base64url-encoded
//	   value.  The number of parts in the JWT is dependent upon the
//	   representation of the resulting JWS using the JWS Compact
//	   Serialization or JWE using the JWE Compact Serialization.
	@Test
	public void testHeaderVerification() {
		final var iss = IdGenerator.generateId();
		assertThrows(NullPointerException.class, () -> IuWebToken.issue(iss).build());

		final var issuer = mock(IuWebTokenIssuer.class);
		IuWebTokenIssuer.register(issuer);

		final var token = IuWebToken.issue(iss).build();
		assertEquals("", token.toString());
	}

}
