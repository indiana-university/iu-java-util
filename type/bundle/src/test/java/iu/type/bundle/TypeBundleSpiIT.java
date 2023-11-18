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
package iu.type.bundle;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class TypeBundleSpiIT {
	
	@Test
	public void testCloses() throws Exception {
		final var spi = new TypeBundleSpi();
		spi.close();
		assertThrows(IllegalStateException.class, () -> spi.resolveType(null));
		assertThrows(IllegalStateException.class, () -> spi.createComponent(null));
		spi.close(); // second call is no-op
	}

	@Test
	public void testDelegateCleansUpOnCreateError() throws Exception {
		final var e = new RuntimeException();
		try (final var mockFinder = mockConstruction(BundleModuleFinder.class, (finder, context) -> {
			doThrow(e).when(finder).findAll();
		})) {
			assertSame(e, assertThrows(RuntimeException.class, TypeBundleSpi::new));
		}
	}

	@Test
	public void testDelegateSuppressesCleanUpErrorAfterCreateError() throws Exception {
		final var e = new RuntimeException();
		final var e2 = new RuntimeException();
		try (final var mockFinder = mockConstruction(BundleModuleFinder.class, (finder, context) -> {
			doThrow(e).when(finder).findAll();
		}); //
				final var mockTypeBundle = mockStatic(TypeBundleSpi.class, CALLS_REAL_METHODS)) {
			mockTypeBundle.when(() -> TypeBundleSpi.cleanUp(any(), any())).thenThrow(e2);
			assertSame(e, assertThrows(RuntimeException.class, TypeBundleSpi::new));
			assertSame(e2, e.getSuppressed()[0]);
		}
	}

}
