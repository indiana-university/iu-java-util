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
package iu.type.container;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockConstruction;

import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class TypeContainerBootstrapIT {

	@Test
	public void testInitCrypt() throws Exception {
		System.setProperty("iu.boot.components", "target/dependency/iu-java-crypt-impl-bundle.jar");
		try (final var typeContainerBootstrap = assertDoesNotThrow(TypeContainerBootstrap::new)) {
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "before init container");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "after init base iu.util.crypt");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "after create Component .*");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE,
					"after create ComponentResource .*");
			IuTestLogger.expect(TypeContainerResource.class.getName(), Level.FINE,
					"init resource ComponentResource .*");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "before join .*");
			IuTestLogger.expect("edu.iu.crypt.Init", Level.CONFIG, "init iu-java-crypt SPI .*");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "before destroy Component .*");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE,
					"before destroy base iu.util.crypt");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "after destroy container");
			assertDoesNotThrow(typeContainerBootstrap::run);
			assertDoesNotThrow(typeContainerBootstrap::close);
		}
	}

	@Test
	public void testInitResourceError() throws Exception {
		System.setProperty("iu.boot.components", "target/dependency/iu-java-crypt-impl-bundle.jar");
		try (final var typeContainerBootstrap = assertDoesNotThrow(TypeContainerBootstrap::new)) {
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "before init container");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "after init base iu.util.crypt");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "after create Component .*");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE,
					"after create ComponentResource .*");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "before join .*");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "before destroy Component .*");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE,
					"before destroy base iu.util.crypt");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "after destroy container");

			final var error = new IllegalStateException();
			try (final var mockTypeContainerResource = mockConstruction(TypeContainerResource.class, (a, ctx) -> {
				doThrow(error).when(a).join();
			})) {
				assertSame(error, assertThrows(IllegalStateException.class, typeContainerBootstrap::run));
			}
		}
	}

}
