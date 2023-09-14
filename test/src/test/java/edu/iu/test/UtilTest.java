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
package edu.iu.test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class UtilTest {

	@Test
	public void testMockWithDefaults() {
		var hasDefaults = IuTest.mockWithDefaults(InterfaceWithDefaults.class);
		assertNull(hasDefaults.getAbstractString());
		assertEquals("foobar", hasDefaults.getDefaultString());
	}

	@Test
	public void testMockWithDefaultsHandlesUnsupportedOperationException() {
		var hasDefaults = IuTest.mockWithDefaults(InterfaceWithDefaults.class);
		assertDoesNotThrow(() -> hasDefaults.throwsUnsupportedOperationException());
		
		var exception = new RuntimeException();
		doThrow(exception).when(hasDefaults).throwsUnsupportedOperationException();
		try {
			hasDefaults.throwsUnsupportedOperationException();
		} catch (RuntimeException e) {
			assertSame(exception, e);
		}
	}

	@Test
	public void testMockWithDefaultsHandlesOtherExceptions() {
		var hasDefaults = IuTest.mockWithDefaults(InterfaceWithDefaults.class);
		when(hasDefaults.getAbstractString()).thenThrow(IllegalStateException.class);
		assertThrows(IllegalStateException.class, () -> hasDefaults.getAbstractString());
	}

}
