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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class AuthorizedScopeTest {

	@Test
	public void testProps() {
		final var scope = new AuthorizationScope("foo", "bar");
		assertEquals("foo", scope.getName());
		assertEquals("bar", scope.getRealm());
		assertEquals("OAuth Scope foo, for realm bar", scope.toString());
	}

	@Test
	public void testEquals() {
		final var scope1 = new AuthorizationScope("foo", "bar");
		final var scope2 = new AuthorizationScope("foo", "baz");
		final var scope3 = new AuthorizationScope("bar", "baz");
		final var scope4 = new AuthorizationScope("foo", "baz");
		assertNotEquals(scope1, new Object());
		assertNotEquals(scope1, scope2);
		assertNotEquals(scope2, scope1);
		assertNotEquals(scope1, scope3);
		assertNotEquals(scope3, scope1);
		assertNotEquals(scope2, scope3);
		assertNotEquals(scope3, scope2);
		assertEquals(scope2, scope4);
		assertEquals(scope4, scope2);
		assertEquals(scope2.hashCode(), scope4.hashCode());
	}

}
