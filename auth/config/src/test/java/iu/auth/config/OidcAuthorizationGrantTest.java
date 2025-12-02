/*
 * Copyright © 2025 Indiana University
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
package iu.auth.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static iu.auth.config.IuHttpAware.HOST;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import edu.iu.IdGenerator;
import edu.iu.IuWebUtils;
import edu.iu.auth.oauth.OAuthAuthorizationClient;
import edu.iu.crypt.WebToken;

@SuppressWarnings("javadoc")
@ExtendWith(IuHttpAware.class)
public class OidcAuthorizationGrantTest {

	private OAuthAuthorizationClient mockClient;
	private Supplier<OAuthAuthorizationClient> clientSupplier;
	private WebToken mockWebToken;
	private static final URI REDIRECT_URI = URI.create("https://" + HOST + "/redirect");

	@BeforeEach
	void setup() {
		mockClient = mock(OAuthAuthorizationClient.class);
		when(mockClient.getRedirectUri()).thenReturn(REDIRECT_URI);
		clientSupplier = () -> mockClient;
		mockWebToken = mock(WebToken.class);
	}

	@Test
	void testConstructorAndGetClient() {
		OidcAuthorizationGrant grant = new OidcAuthorizationGrant(clientSupplier);
		assertNotNull(grant);
		assertEquals(mockClient, grant.getClient());
	}

	@Test
	void testTokenAuthSetsHeadersAndBody() {
		OidcAuthorizationGrant grant = new OidcAuthorizationGrant(clientSupplier);
		HttpRequest.Builder builder = mock(HttpRequest.Builder.class);
		when(builder.header(anyString(), anyString())).thenReturn(builder);
		when(builder.POST(any(BodyPublisher.class))).thenReturn(builder);
		try (MockedStatic<IuWebUtils> webUtilsMock = Mockito.mockStatic(IuWebUtils.class)) {
			webUtilsMock.when(() -> IuWebUtils.createQueryString(any()))
					.thenReturn("grant_type=authorization_code&redirect_uri=" + REDIRECT_URI);
			grant.tokenAuth(builder);
			verify(builder).header("Content-Type", "application/x-www-form-urlencoded");
			verify(builder).POST(any(BodyPublisher.class));
		}
	}

	@Test
	void testVerifyTokenThrowsOnNullNbfOrExp() {
		OidcAuthorizationGrant grant = new OidcAuthorizationGrant(clientSupplier);
		when(mockWebToken.getNotBefore()).thenReturn(null);
		when(mockWebToken.getExpires()).thenReturn(null);
		assertThrows(NullPointerException.class, () -> grant.verifyToken(mockWebToken));
		when(mockWebToken.getNotBefore()).thenReturn(java.time.Instant.now());
		assertThrows(NullPointerException.class, () -> grant.verifyToken(mockWebToken));
		when(mockWebToken.getExpires()).thenReturn(java.time.Instant.now().plusSeconds(3600));
		assertDoesNotThrow(() -> grant.verifyToken(mockWebToken));
	}

	@Test
	void testValidateJwtDelegatesToParent() {
		OidcAuthorizationGrant grant = spy(new OidcAuthorizationGrant(clientSupplier));
		String jwtString = IdGenerator.generateId();
		WebToken expectedToken = mock(WebToken.class);
		doReturn(expectedToken).when((OAuthAccessTokenGrant) grant).validateJwt(jwtString);
		WebToken result = grant.validateJwt(jwtString);
		assertEquals(expectedToken, result);
	}
}