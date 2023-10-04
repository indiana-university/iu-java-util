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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.test.IuTest;
import edu.iu.type.IuComponent;
import jakarta.annotation.Resource;

@SuppressWarnings("javadoc")
public class LegacyClassLoaderTest {

	private URL url;
	private LegacyClassLoader loader;

	@BeforeEach
	public void setup() throws IOException {
		var path = Path.of(IuTest.getProperty("testlegacy.archive")).toRealPath();
		url = path.toUri().toURL();
		loader = new LegacyClassLoader(false, new URL[] { url }, null);
	}

	@AfterEach
	public void teardown() throws IOException {
		if (loader != null)
			loader.close();
	}

	@Test
	public void testDoesNotDelegateToSystem() throws Throwable {
		assertThrows(ClassNotFoundException.class, () -> loader.loadClass(IuComponent.class.getName()));
		assertSame(Object.class, loader.loadClass(Object.class.getName()));
		var timer = loader.loadClass("java.util.Timer");
		assertSame(java.util.Timer.class, timer);
		assertNull(loader.getResource("META-INF/iu-test.properties"));
	}

	@Test
	public void testCachesSameClass() throws Throwable {
		assertSame(loader.loadClass("edu.iu.legacy.LegacyInterface"),
				loader.loadClass("edu.iu.legacy.LegacyInterface"));
	}

	@Test
	public void testIsolationFromSystemLoader() throws Throwable {
		assertThrows(ClassNotFoundException.class, () -> loader.loadClass(getClass().getName()));
		assertThrows(ClassNotFoundException.class, () -> loader.loadClass(Resource.class.getName()));
	}

	@Test
	public void testChildDelegates() throws Throwable {
		var legacyInterface = loader.loadClass("edu.iu.legacy.LegacyInterface");
		try (var child = new LegacyClassLoader(false, new URL[] { url }, loader)) {
			assertSame(legacyInterface, child.loadClass("edu.iu.legacy.LegacyInterface"));
		}
	}

	@Test
	public void testWebChildDoesntDelegate() throws Throwable {
		var legacyInterface = loader.loadClass("edu.iu.legacy.LegacyInterface");
		try (var webChild = new LegacyClassLoader(true, new URL[] { url }, loader)) {
			assertNotSame(legacyInterface, webChild.loadClass("edu.iu.legacy.LegacyInterface"));
		}
	}

	@Test
	public void testWebChildResolves() throws Throwable {
		try (var webChild = new LegacyClassLoader(true, new URL[] { url }, loader)) {
			assertDoesNotThrow(() -> webChild.loadClass("edu.iu.legacy.LegacyInterface", true));
		}
	}

	@Test
	public void testWebChildCaches() throws Throwable {
		try (var webChild = new LegacyClassLoader(true, new URL[] { url }, loader)) {
			assertSame(webChild.loadClass("edu.iu.legacy.LegacyInterface"),
					webChild.loadClass("edu.iu.legacy.LegacyInterface"));
		}
	}

	@Test
	public void testWebSiblingDoesntShare() throws Throwable {
		try (var childWeb = new LegacyClassLoader(true, new URL[] { url }, loader)) {
			var legacyInterface = loader.loadClass("edu.iu.legacy.LegacyInterface");
			try (var webSibling = new LegacyClassLoader(true, new URL[] { url }, loader)) {
				assertNotSame(legacyInterface, childWeb.loadClass("edu.iu.legacy.LegacyInterface"));
			}
		}
	}

	@Test
	public void testWebChildDelegatesOnNotFound() throws Throwable {
		try (var child = new LegacyClassLoader(false,
				new URL[] { Path.of(IuTest.getProperty("testcomponent.archive")).toRealPath().toUri().toURL() },
				null)) {
			try (var webChild = new LegacyClassLoader(true, new URL[] { url }, child)) {
				assertSame(child, webChild.loadClass("edu.iu.type.testcomponent.InternalClass").getClassLoader());
			}
		}
	}

}
