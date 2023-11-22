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
package edu.iu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;

import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IdTest {

	private void isolate(Class<?> classToIsolate, UnsafeConsumer<Class<?>> isolatedClassConsumer) {
		final var resourceName = classToIsolate.getName().replace('.', '/') + ".class";
		final var absoluteResourceName = classToIsolate.getClassLoader().getResource(resourceName).toString();
		final var classPathEntry = absoluteResourceName.substring(0,
				absoluteResourceName.length() - resourceName.length());
		try (final var isolatedLoader = new URLClassLoader(new URL[] { URI.create(classPathEntry).toURL() }, null)) {
			final var isolatedClass = isolatedLoader.loadClass(classToIsolate.getName());
			assertNotSame(classToIsolate, isolatedClass);
			isolatedClassConsumer.accept(isolatedClass);
		} catch (Throwable e) {
			throw IuException.unchecked(e);
		}
	}

	@Test
	public void testInitExceptionOnSeed() throws Exception {
		assertInstanceOf(NoSuchAlgorithmException.class,
				assertThrows(ExceptionInInitializerError.class, () -> isolate(IdGenerator.class, c -> {
					try (final var mockSecureRandom = mockStatic(SecureRandom.class)) {
						mockSecureRandom.when(() -> SecureRandom.getInstanceStrong())
								.thenThrow(NoSuchAlgorithmException.class);
						c.getMethod("generateId").invoke(null);
					}
				})).getCause());
	}

	@Test
	public void testInitExceptionOnPrng() throws Exception {
		assertInstanceOf(NoSuchAlgorithmException.class,
				assertThrows(IllegalStateException.class, () -> isolate(IdGenerator.class, c -> {
					try (final var mockSecureRandom = mockStatic(SecureRandom.class)) {
						mockSecureRandom.when(() -> SecureRandom.getInstance("SHA1PRNG"))
								.thenThrow(NoSuchAlgorithmException.class);
						IuException.checkedInvocation(() -> c.getMethod("generateId").invoke(null));
					}
				})).getCause());
	}

	@Test
	public void testEncodeDecode() {
		System.out.println(IdGenerator.generateId());
		for (int i = 0; i < 1000000; i++) {
			String randomId = IdGenerator.generateId();
			IdGenerator.verifyId(randomId, 10000L);
			assertEquals(32, randomId.length(), randomId);
		}
	}

	@Test
	public void testVerifyNeedsValidChars() {
		assertEquals("Invalid encoding",
				assertThrows(IllegalArgumentException.class, () -> IdGenerator.verifyId("@!#&", 0)).getMessage());
		assertEquals("Invalid encoding",
				assertThrows(IllegalArgumentException.class, () -> IdGenerator.verifyId("\n\t\f\0", 0)).getMessage());
		assertEquals("Invalid encoding",
				assertThrows(IllegalArgumentException.class, () -> IdGenerator.verifyId("{||}", 0)).getMessage());
	}
	
	@Test
	public void testVerifyNeedsPowerOf4() {
		assertEquals("Invalid length",
				assertThrows(IllegalArgumentException.class, () -> IdGenerator.verifyId("a", 0)).getMessage());
	}

	@Test
	public void testVerifyNeeds24() {
		assertEquals("Invalid length",
				assertThrows(IllegalArgumentException.class, () -> IdGenerator.verifyId("abcd", 0)).getMessage());
	}

	@Test
	public void testVerifyNeedsValidTime() {
		assertEquals("Invalid time signature",
				assertThrows(IllegalArgumentException.class, () -> IdGenerator.verifyId("abcdefghijklmnopqrstuvwxyzABCDEF", 0)).getMessage());
	}

	@Test
	public void testVerifyNeedsUnexpiredTime() throws Exception {
		var id = IdGenerator.generateId();
		Thread.sleep(2);
		assertEquals("Expired time signature",
				assertThrows(IllegalArgumentException.class, () -> IdGenerator.verifyId(id, 1)).getMessage());
	}

	@Test
	public void testVerifyNeedsValidChecksum() throws Exception {
		Thread.sleep(2);
		assertEquals("Invalid checksum",
				assertThrows(IllegalArgumentException.class, () -> IdGenerator.verifyId("hPTbzx2RQ4sCTn-aWy4zwPyU_TJoM6A3", 0)).getMessage());
	}

}