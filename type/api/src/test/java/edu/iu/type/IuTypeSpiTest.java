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
package edu.iu.type;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import edu.iu.test.IuTest;
import edu.iu.type.spi.IuTypeSpi;
import edu.iu.type.spi.TypeImplementation;

@SuppressWarnings("javadoc")
public class IuTypeSpiTest {

	private static IuTypeSpi iuTypeSpi;

	@BeforeAll
	public static void setupClass() throws ClassNotFoundException {
		iuTypeSpi = mock(IuTypeSpi.class);
		var serviceLoader = mock(ServiceLoader.class);
		when(serviceLoader.iterator()).thenReturn(List.of(iuTypeSpi).iterator());
		try (var mockServiceLoader = mockStatic(ServiceLoader.class)) {
			mockServiceLoader.when(() -> ServiceLoader.load(IuTypeSpi.class, IuTypeSpi.class.getClassLoader()))
					.thenReturn(serviceLoader);
			assertSame(iuTypeSpi, TypeImplementation.PROVIDER);
		}
	}

	@Test
	public void testResolveType() {
		IuType.of(Object.class);
		verify(iuTypeSpi, times(1)).resolveType(Object.class);
	}

	@Test
	public void testNewIsolatedComponent() throws IOException {
		var in = mock(InputStream.class);
		IuComponent.of(in);
		verify(iuTypeSpi).createComponent(ModuleLayer.boot(), null, null, in);
	}

	@Test
	public void testNewDelegatingComponent() throws IOException {
		var in = mock(InputStream.class);
		IuComponent.of(ModuleLayer.boot(), ClassLoader.getSystemClassLoader(), in);
		verify(iuTypeSpi).createComponent(ModuleLayer.boot(), ClassLoader.getSystemClassLoader(), null, in);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testNewControlledIsolatedComponent() throws IOException {
		var in = mock(InputStream.class);
		var cb = mock(BiConsumer.class);
		IuComponent.of(cb, in);
		verify(iuTypeSpi).createComponent(ModuleLayer.boot(), null, cb, in);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testExtendComponent() throws IOException {
		var in = mock(InputStream.class);
		var comp = IuTest.mockWithDefaults(IuComponent.class);
		comp.extend(in);
		verify(comp).extend((BiConsumer) null, in);
	}

	@Test
	public void testScanComponent() throws IOException, ClassNotFoundException {
		var path = mock(Path.class);
		IuComponent.scan(ClassLoader.getSystemClassLoader(), path);
		verify(iuTypeSpi).scanComponentEntry(ClassLoader.getSystemClassLoader(), path);
	}

	@Test
	public void testScanClassFolder() throws IOException, ClassNotFoundException {
		final var target = getClass();
		final var loader = target.getClassLoader();
		final var resourceName = target.getName().replace('.', '/') + ".class";
		final var resource = loader.getResource(resourceName).toExternalForm();
		assertTrue(resource.startsWith("file:"), () -> resource);
		assertTrue(resource.endsWith(resourceName), () -> resource + " " + resourceName);
		final var pathEntry = Path.of(URI.create(resource.substring(0, resource.length() - resourceName.length())))
				.toRealPath();

		IuComponent.scan(getClass());

		verify(iuTypeSpi, atLeastOnce()).scanComponentEntry(loader, pathEntry);
	}

	@Test
	public void testScanClassJar() throws IOException, ClassNotFoundException {
		final var target = Test.class;
		final var loader = target.getClassLoader();
		final var resource = loader.getResource(target.getName().replace('.', '/') + ".class").toExternalForm();
		assertTrue(resource.startsWith("jar:"), () -> resource);
		final var pathEntry = Path.of(URI.create(resource.substring(4, resource.indexOf("!/")))).toRealPath();
		IuComponent.scan(Test.class);
		verify(iuTypeSpi).scanComponentEntry(loader, pathEntry);
	}

}
