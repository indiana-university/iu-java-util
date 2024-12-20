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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IdTest {

	@Test
	public void testEncodeDecode() {
		for (int i = 0; i < 1000000; i++) {
			String randomId = IdGenerator.generateId();
			IdGenerator.verifyId(randomId, 10000L);
			assertEquals(32, randomId.length(), randomId);
		}
	}

	@Test
	public void testVerifyNeedsValidChars() {
		assertEquals("Illegal base64 character 40",
				assertThrows(IllegalArgumentException.class, () -> IdGenerator.verifyId("@!#&", 0)).getMessage());
		assertEquals("Illegal base64 character a",
				assertThrows(IllegalArgumentException.class, () -> IdGenerator.verifyId("\n\t\f\0", 0)).getMessage());
		assertEquals("Illegal base64 character 7b",
				assertThrows(IllegalArgumentException.class, () -> IdGenerator.verifyId("{||}", 0)).getMessage());
	}
	
	@Test
	public void testVerifyNeedsPowerOf4() {
		assertEquals("Input byte[] should at least have 2 bytes for base64 bytes",
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
		Thread.sleep(2000L);
		assertEquals("Expired time signature",
				assertThrows(IllegalArgumentException.class, () -> IdGenerator.verifyId(id, 1)).getMessage());
	}

	@Test
	public void testVerifyNeedsValidChecksum() throws Exception {
		assertEquals("Invalid checksum",
				assertThrows(IllegalArgumentException.class, () -> IdGenerator.verifyId("F4TS-R7YNZkaLtcVh544L4xSE2zCQpIZ", 0)).getMessage());
	}

}
