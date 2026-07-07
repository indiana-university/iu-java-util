/*
 * Copyright © 2026 Indiana University
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
package edu.iu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuClassLoaderContextTest {

	private ClassLoader savedContextLoader;

	@BeforeEach
	public void saveContextLoader() {
		savedContextLoader = Thread.currentThread().getContextClassLoader();
	}

	@AfterEach
	public void restoreContextLoader() {
		Thread.currentThread().setContextClassLoader(savedContextLoader);
	}

	@Test
	public void testGetContextForSystemClassLoader() {
		Thread.currentThread().setContextClassLoader(ClassLoader.getSystemClassLoader());
		assertEquals("system", IuClassLoaderContext.getContext().getName());
	}

	@Test
	public void testGetContextForPlatformClassLoader() {
		Thread.currentThread().setContextClassLoader(ClassLoader.getPlatformClassLoader());
		assertEquals("platform", IuClassLoaderContext.getContext().getName());
	}

	@Test
	public void testGetContextWithNullContextLoader() {
		Thread.currentThread().setContextClassLoader(null);
		assertEquals("boot", IuClassLoaderContext.getContext().getName());
	}

	@Test
	public void testRegisterAndGetContext() {
		final var loader = new ClassLoader(ClassLoader.getPlatformClassLoader()) {
		};
		final IuClassLoaderContext context = () -> "my-app";
		IuClassLoaderContext.register(context, loader);
		Thread.currentThread().setContextClassLoader(loader);
		assertSame(context, IuClassLoaderContext.getContext());
		assertEquals("my-app", IuClassLoaderContext.getContext().getName());
	}

	@Test
	public void testRegisterNullLoaderThrows() {
		final IuClassLoaderContext context = () -> "test";
		assertThrows(NullPointerException.class, () -> IuClassLoaderContext.register(context, null));
	}

	@Test
	public void testRegisterNullContextThrows() {
		final var loader = new ClassLoader(ClassLoader.getPlatformClassLoader()) {
		};
		assertThrows(NullPointerException.class, () -> IuClassLoaderContext.register(null, loader));
	}

	@Test
	public void testRegisterDuplicateLoaderThrows() {
		final var loader = new ClassLoader(ClassLoader.getPlatformClassLoader()) {
		};
		IuClassLoaderContext.register(() -> "first", loader);
		assertThrows(IllegalStateException.class, () -> IuClassLoaderContext.register(() -> "second", loader));
	}

	@Test
	public void testGetNameFromLambda() {
		final IuClassLoaderContext ctx = () -> "lambda-name";
		assertEquals("lambda-name", ctx.getName());
	}

}
