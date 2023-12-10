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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.util.ServiceLoader;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class TypeBundleSpiIT {

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testCloses() throws Exception {
		final var spi = new TypeBundleSpi();
		assertEquals("iu.util.type.impl", spi.getImplementationModule().getName());
		spi.close();
		assertThrows(IllegalStateException.class, () -> spi.getImplementationModule());
		assertThrows(IllegalStateException.class, () -> spi.resolveType(null));
		assertThrows(IllegalStateException.class, () -> spi.createComponent((BiConsumer) null, null));
		assertThrows(IllegalStateException.class, () -> spi.scanComponentEntry(null, null));
		spi.close(); // second call is no-op
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testCloseOnError() {
		var e = new RuntimeException();
		try (final var mockServiceLoader = mockStatic(ServiceLoader.class)) {
			mockServiceLoader.when(() -> ServiceLoader.load(any(Class.class), any())).thenThrow(e);
			assertSame(e, assertThrows(RuntimeException.class, () -> new TypeBundleSpi()));
		}
	}

}
