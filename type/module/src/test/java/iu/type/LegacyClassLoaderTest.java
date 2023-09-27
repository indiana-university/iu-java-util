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
package iu.type;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URL;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;

import edu.iu.test.IuTest;
import edu.iu.type.IuComponent;

@SuppressWarnings("javadoc")
public class LegacyClassLoaderTest {

	@Test
	public void testDoesNotDelegateToSystem() throws Throwable {
		var url = Path.of(IuTest.getProperty("testlegacy.archive")).toRealPath().toUri().toURL();
		try (var loader = new LegacyClassLoader(Set.of(), new URL[] { url }, null)) {
			assertThrows(ClassNotFoundException.class, () -> loader.loadClass(IuComponent.class.getName()));
			assertSame(Object.class, loader.loadClass(Object.class.getName()));
			var timer = loader.loadClass("java.util.Timer");
			assertSame(java.util.Timer.class, timer);
			assertNull(loader.getResource("META-INF/iu-test.properties"));
		}
	}

	@Test
	public void testEndorsed() throws Throwable {
		var url = Path.of(IuTest.getProperty("testlegacy.archive")).toRealPath().toUri().toURL();
		try (var loader = new LegacyClassLoader(Set.of(), new URL[] { url }, null)) {
			var legacyInterface = loader.loadClass("edu.iu.legacy.LegacyInterface");
			var nonEndorsedChild = new LegacyClassLoader(Set.of(), new URL[] { url }, loader);
			assertSame(legacyInterface, nonEndorsedChild.loadClass("edu.iu.legacy.LegacyInterface"));
			try (var endorsedChild = new LegacyClassLoader(Set.of("edu.iu.legacy.LegacyInterface"), new URL[] { url },
					loader)) {
				assertNotSame(legacyInterface, endorsedChild.loadClass("edu.iu.legacy.LegacyInterface"));
			}
			try (var endorsedChild = new LegacyClassLoader(Set.of("edu.iu.legacy.LegacyInterface"), new URL[] { url },
					loader)) {
				assertNotSame(legacyInterface, endorsedChild.loadClass("edu.iu.legacy.LegacyInterface", true));
			}
		}
	}

}
