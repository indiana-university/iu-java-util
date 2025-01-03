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
package iu.type.boot;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.io.IOException;
import java.util.logging.Level;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class InitIT {

	@BeforeEach
	public void init() throws Exception {
		System.setProperty("iu.config", "");
		Class.forName("iu.logging.boot.IuLoggingBootstrap").getConstructor(boolean.class).newInstance(true);
		IuTestLogger.allow("", Level.CONFIG, "IuLogContext initialized .*");
	}

	@AfterEach
	public void cleanup() {
	}

	@Test
	public void testMain() {
		IuTestLogger.allow("iu.type.container", Level.CONFIG);

		IuTestLogger.expect(Init.class.getName(), Level.FINE, "before init loader");
		IuTestLogger.expect(Init.class.getName(), Level.FINE,
				"after init loader (iu\\.util\\.type(\\.container|\\.bundle|\\.base)?(, )?)+");
		IuTestLogger.expect(Init.class.getName(), Level.FINE, "after init container bootstrap .*");
		IuTestLogger.expect(Init.class.getName(), Level.FINE, "before destroy container bootstrap .*");
		IuTestLogger.expect(Init.class.getName(), Level.FINE,
				"before destroy loader (iu\\.util\\.type(\\.container|\\.bundle|\\.base)?(, )?)+");
		assertDoesNotThrow(() -> Init.main());
	}

	@Test
	public void testMainError() {
		IuTestLogger.allow("iu.type.container", Level.CONFIG);

		IuTestLogger.expect(Init.class.getName(), Level.FINE, "before init loader");
		IuTestLogger.expect(Init.class.getName(), Level.FINE,
				"after init loader (iu\\.util\\.type(\\.container|\\.bundle|\\.base)?(, )?)+");

		System.setProperty("iu.boot.components", IdGenerator.generateId());
		try { // File not found
			assertThrows(IllegalStateException.class, () -> Init.main());
		} finally {
			System.getProperties().remove("iu.boot.components");
		}
	}

	@Test
	public void testDoubleClose() {
		IuTestLogger.allow("iu.type.container", Level.CONFIG);

		IuTestLogger.expect(Init.class.getName(), Level.FINE, "before init loader");
		IuTestLogger.expect(Init.class.getName(), Level.FINE,
				"after init loader (iu\\.util\\.type(\\.container|\\.bundle|\\.base)?(, )?)+");
		IuTestLogger.expect(Init.class.getName(), Level.FINE, "after init container bootstrap .*");
		IuTestLogger.expect(Init.class.getName(), Level.FINE, "before destroy container bootstrap .*");
		IuTestLogger.expect(Init.class.getName(), Level.FINE,
				"before destroy loader (iu\\.util\\.type(\\.container|\\.bundle|\\.base)?(, )?)+");
		assertDoesNotThrow(() -> {
			try (final var init = new Init()) {
				init.close();
			}
		});
	}

	@Test
	public void testCloseError() throws IOException {
		IuTestLogger.allow("iu.type.container", Level.CONFIG);

		IuTestLogger.expect(Init.class.getName(), Level.FINE, "before init loader");
		IuTestLogger.expect(Init.class.getName(), Level.FINE,
				"after init loader (iu\\.util\\.type(\\.container|\\.bundle|\\.base)?(, )?)+");
		IuTestLogger.expect(Init.class.getName(), Level.FINE, "after init container bootstrap .*");
		IuTestLogger.expect(Init.class.getName(), Level.FINE, "before destroy container bootstrap .*");
		IuTestLogger.expect(Init.class.getName(), Level.FINE,
				"before destroy loader (iu\\.util\\.type(\\.container|\\.bundle|\\.base)?(, )?)+");

		final var error = new IllegalStateException();
		try (final var init = new Init()) {
			try (final var mockIuException = mockStatic(IuException.class)) {
				mockIuException.when(() -> IuException.suppress(any(), any())).thenReturn(error);
				mockIuException.when(() -> IuException.unchecked(error)).thenReturn(error);
				assertSame(error, assertThrows(IllegalStateException.class, init::close));
			}
		}
	}

}
