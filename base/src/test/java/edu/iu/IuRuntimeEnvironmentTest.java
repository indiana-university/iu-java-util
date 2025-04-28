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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuRuntimeEnvironmentTest {

	@Test
	public void testInvalidPropertyName() {
		assertThrows(IllegalArgumentException.class, () -> IuRuntimeEnvironment.env("@!#$%"));
	}

	@Test
	public void testMissing() {
		final var id = id();
		assertNull(IuRuntimeEnvironment.envOptional(id));
		final var e = assertThrows(NullPointerException.class, () -> IuRuntimeEnvironment.env(id));
		assertEquals("Missing system property " + id + " or environment variable "
				+ id.toUpperCase().replace('.', '_').replace('-', '_'), e.getMessage());
	}

	@Test
	public void testBlank() {
		final var id = id();
		System.setProperty(id, "");
		assertNull(IuRuntimeEnvironment.envOptional(id));
		final var e = assertThrows(NullPointerException.class, () -> IuRuntimeEnvironment.env(id));
		assertEquals("Missing system property " + id + " or environment variable "
				+ id.toUpperCase().replace('.', '_').replace('-', '_'), e.getMessage());
	}

	@Test
	public void testSet() {
		final var id = id();
		final var val = id();
		System.setProperty(id, val);
		assertEquals(val, IuRuntimeEnvironment.envOptional(id));
		assertEquals(val, IuRuntimeEnvironment.env(id));
	}

	private String id() {
		String id;
		do
			id = IdGenerator.generateId();
		while (!Character.isLetter(id.charAt(0)));
		return id;
	}
}
