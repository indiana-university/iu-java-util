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
package edu.iu;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.security.MessageDigest;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuDigestTest {

	@SuppressWarnings("deprecation")
	@Test
	public void testMD5() throws Throwable {
		final var data = IdGenerator.generateId().getBytes();
		assertArrayEquals(MessageDigest.getInstance("MD5").digest(data), IuDigest.md5(data));
	}

	@SuppressWarnings("deprecation")
	@Test
	public void testSha1() throws Throwable {
		final var data = IdGenerator.generateId().getBytes();
		assertArrayEquals(MessageDigest.getInstance("SHA-1").digest(data), IuDigest.sha1(data));
	}

	@Test
	public void testSha256() throws Throwable {
		final var data = IdGenerator.generateId().getBytes();
		assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(data), IuDigest.sha256(data));
	}

	@Test
	public void testSha384() throws Throwable {
		final var data = IdGenerator.generateId().getBytes();
		assertArrayEquals(MessageDigest.getInstance("SHA-384").digest(data), IuDigest.sha384(data));
	}

	@Test
	public void testSha512() throws Throwable {
		final var data = IdGenerator.generateId().getBytes();
		assertArrayEquals(MessageDigest.getInstance("SHA-512").digest(data), IuDigest.sha512(data));
	}

}
