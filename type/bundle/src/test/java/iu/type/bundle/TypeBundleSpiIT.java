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
package iu.type.bundle;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.util.ServiceLoader;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.type.bundle.IuTypeBundle;

@SuppressWarnings("javadoc")
public class TypeBundleSpiIT {

	private Field instanceField;
	private TypeBundleSpi instanceToRestore;

	@BeforeEach
	public void setup() throws Exception {
		instanceField = TypeBundleSpi.class.getDeclaredField("instance");
		instanceField.setAccessible(true);
		instanceToRestore = (TypeBundleSpi) instanceField.get(null);
		instanceField.set(null, null);
	}

	@AfterEach
	public void teardown() throws Exception {
		if (instanceToRestore != null)
			instanceField.set(null, instanceToRestore);
	}

	@Test
	public void testShutdown() throws Exception {
		IuTypeBundle.shutdown(); // no-op
		final var spi = mock(TypeBundleSpi.class);
		instanceField.set(null, spi);
		IuTypeBundle.shutdown();
		verify(spi).close();
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testCloses() throws Exception {
		final var spi = new TypeBundleSpi();
		spi.close();
		assertThrows(IllegalStateException.class, () -> spi.resolveType(null));
		assertThrows(IllegalStateException.class,
				() -> spi.createComponent(ModuleLayer.boot(), null, (BiConsumer) null, null));
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
