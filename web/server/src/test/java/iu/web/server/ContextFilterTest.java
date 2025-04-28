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
package iu.web.server;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Collections;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.Filter.Chain;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import edu.iu.IdGenerator;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class ContextFilterTest {

	@Test
	public void testRequestUriNotInAllowList() {
		final var filter = new ContextFilter(Collections.emptyList(), Collections.emptyList());
		final var requestHeaders = mock(Headers.class);
		when(requestHeaders.get("X-Forwarded-Host")).thenReturn(null);
		final var requestUri = URI.create(IdGenerator.generateId());
		final var exchange = mock(HttpExchange.class);
		when(exchange.getRequestURI()).thenReturn(requestUri);
		when(exchange.getRequestHeaders()).thenReturn(requestHeaders);
		final var chain = mock(Chain.class);
		IuTestLogger.expect(ContextFilter.class.getName(), Level.INFO,
				"rejecting " + requestUri + ", not in allow list []");
		assertDoesNotThrow(() -> filter.doFilter(exchange, chain));
	}

	@Test
	public void testRequest() {
		final var filter = new ContextFilter(Collections.emptyList(), Collections.emptyList());
		final var requestHeaders = mock(Headers.class);
		when(requestHeaders.get("X-Forwarded-Host")).thenReturn(null);
		final var requestUri = URI.create(IdGenerator.generateId());
		final var exchange = mock(HttpExchange.class);
		when(exchange.getRequestURI()).thenReturn(requestUri);
		when(exchange.getRequestHeaders()).thenReturn(requestHeaders);
		final var chain = mock(Chain.class);
		assertDoesNotThrow(() -> filter.doFilter(exchange, chain));
	}

}
