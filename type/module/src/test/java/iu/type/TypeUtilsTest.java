/*
 * Copyright © 2023 Indiana University
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
package iu.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@SuppressWarnings("javadoc")
@ExtendWith(LegacyContextSupport.class)
public class TypeUtilsTest {

	@Test
	public void testPlatformClassNames() throws Throwable {
		assertTrue(TypeUtils.isPlatformType("jakarta."));
		assertTrue(TypeUtils.isPlatformType("java."));
		assertTrue(TypeUtils.isPlatformType("javax."));
		assertTrue(TypeUtils.isPlatformType("jdk."));
		assertFalse(TypeUtils.isPlatformType("iu."));
	}

	@Test
	public void testDoInContext() {
		TypeUtils.doInContext(LegacyContextSupport.get(),
				() -> assertSame(LegacyContextSupport.get(), Thread.currentThread().getContextClassLoader()));
	}

	@Test
	public void testDoInContextOfClass() throws ClassNotFoundException {
		TypeUtils.doInContext(LegacyContextSupport.get().loadClass("edu.iu.legacy.LegacyBean"),
				() -> assertSame(LegacyContextSupport.get(), Thread.currentThread().getContextClassLoader()));
	}

	@Test
	public void testCallWithContextOfClass() throws Exception {
		TypeUtils.callWithContext(LegacyContextSupport.get().loadClass("edu.iu.legacy.LegacyBean"), () -> {
			assertSame(LegacyContextSupport.get(), Thread.currentThread().getContextClassLoader());
			return null;
		});
	}

	@Test
	public void testdoInContextRethrowsRuntimeException() {
		assertThrows(RuntimeException.class, () -> TypeUtils.doInContext(LegacyContextSupport.get(), () -> {
			throw new RuntimeException();
		}));
	}

	@Test
	public void testdoInContextWrapsCheckedException() {
		class CheckedException extends Exception {
			private static final long serialVersionUID = 1L;
		}
		try (var mockTypeUtils = mockStatic(TypeUtils.class)) {
			mockTypeUtils.when(() -> TypeUtils.doInContext(any(ClassLoader.class), any())).thenCallRealMethod();
			mockTypeUtils.when(() -> TypeUtils.callWithContext(any(ClassLoader.class), any()))
					.thenThrow(new CheckedException());
			assertSame(CheckedException.class, assertThrows(IllegalStateException.class,
					() -> TypeUtils.doInContext(LegacyContextSupport.get(), () -> {
					})).getCause().getClass());
		}
	}

	@Test
	public void testCallWithClassContext() throws Exception {
		TypeUtils.callWithContext(LegacyContextSupport.get(), () -> {
			assertSame(LegacyContextSupport.get(), Thread.currentThread().getContextClassLoader());
			return null;
		});
	}

	@Test
	public void testContextOfClass() throws ClassNotFoundException {
		var c = LegacyContextSupport.get().loadClass("edu.iu.legacy.LegacyBean");
		assertSame(LegacyContextSupport.get(), TypeUtils.getContext(c));
	}

	@Test
	public void testContextOfField() throws ClassNotFoundException, NoSuchFieldException {
		var c = LegacyContextSupport.get().loadClass("edu.iu.legacy.LegacyBean");
		assertSame(LegacyContextSupport.get(), TypeUtils.getContext(c.getDeclaredField("foo")));
	}

	@Test
	public void testContextOfMethod() throws ClassNotFoundException, NoSuchMethodException {
		var c = LegacyContextSupport.get().loadClass("edu.iu.legacy.LegacyBean");
		assertSame(LegacyContextSupport.get(), TypeUtils.getContext(c.getDeclaredMethod("getFoo")));
	}

	@Test
	public void testContextOfParameter() throws ClassNotFoundException, NoSuchMethodException {
		var c = LegacyContextSupport.get().loadClass("edu.iu.legacy.LegacyBean");
		assertSame(LegacyContextSupport.get(),
				TypeUtils.getContext(c.getDeclaredMethod("setFoo", String.class).getParameters()[0]));
	}

	@Test
	public void testContextOfPackageNotSupported() throws ClassNotFoundException {
		var c = LegacyContextSupport.get().loadClass("edu.iu.legacy.LegacyBean");
		assertEquals("Cannot determine context for package edu.iu.legacy",
				assertThrows(UnsupportedOperationException.class, () -> TypeUtils.getContext(c.getPackage()))
						.getMessage());
	}

}
