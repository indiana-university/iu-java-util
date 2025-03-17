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
package edu.iu;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuTextTest {

	@Test
	public void testUtf8() {
		assertEquals("foobar", IuText.utf8(IuText.utf8("foobar")));
		assertNull(IuText.utf8((byte[]) null));
		assertNull(IuText.utf8((String) null));
		assertEquals("", IuText.utf8(new byte[0]));
		assertArrayEquals(new byte[0], IuText.utf8(""));
	}

	@Test
	public void testAscii() {
		assertEquals("foobar", IuText.ascii(IuText.ascii("foobar")));
		assertNull(IuText.ascii((byte[]) null));
		assertNull(IuText.ascii((String) null));
		assertEquals("", IuText.ascii(new byte[0]));
		assertArrayEquals(new byte[0], IuText.ascii(""));
	}

	@Test
	public void testBase64() {
		assertEquals("Zm9vYmFy", IuText.base64(IuText.utf8("foobar")));
		assertEquals("foobar", IuText.utf8(IuText.base64("Zm9vYmFy")));
		assertNull(IuText.base64((byte[]) null));
		assertNull(IuText.base64((String) null));
		assertEquals("", IuText.base64(new byte[0]));
		assertArrayEquals(new byte[0], IuText.base64(""));
	}

	@Test
	public void testBase64Url() {
		assertEquals("Zm9vYmFy", IuText.base64Url(IuText.utf8("foobar")));
		assertEquals("foobar", IuText.utf8(IuText.base64Url("Zm9vYmFy")));
		assertNull(IuText.base64Url((byte[]) null));
		assertNull(IuText.base64Url((String) null));
		assertEquals("", IuText.base64Url(new byte[0]));
		assertArrayEquals(new byte[0], IuText.base64Url(""));
	}


}
