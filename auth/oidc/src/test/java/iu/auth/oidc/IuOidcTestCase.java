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
package iu.auth.oidc;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.net.URI;
import java.net.http.HttpRequest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockedStatic;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.UnsafeConsumer;
import edu.iu.client.IuHttp;
import jakarta.json.JsonObject;

@SuppressWarnings("javadoc")
public class IuOidcTestCase {

	protected static final URI ROOT_URI = URI.create("test://localhost/" + IdGenerator.generateId());

	private MockedStatic<IuHttp> mockHttp;

	protected URI uri(JsonObject content) {
		final var uri = URI.create(ROOT_URI.toString() + '/' + IdGenerator.generateId());
		mockHttp.when(() -> IuHttp.get(uri, IuHttp.READ_JSON_OBJECT)).thenReturn(content);
		return uri;
	}

	protected URI uri(JsonObject content, UnsafeConsumer<HttpRequest.Builder> requestVerifier) {
		final var uri = URI.create(ROOT_URI.toString() + '/' + IdGenerator.generateId());
		mockHttp.when(() -> IuHttp.send(eq(uri), argThat(a -> {
			IuException.unchecked(() -> {
				final var req = mock(HttpRequest.Builder.class);
				a.accept(req);
				requestVerifier.accept(req);
			});
			return true;
		}), eq(IuHttp.READ_JSON_OBJECT))).thenReturn(content);
		return uri;
	}

	@BeforeAll
	public static void setupClass() {
		System.setProperty("iu.http.allowedUri", ROOT_URI.toString());
	}

	@BeforeEach
	public void setup() {
		mockHttp = mockStatic(IuHttp.class);
	}

	@AfterEach
	public void teardown() {
		mockHttp.close();
	}

}
