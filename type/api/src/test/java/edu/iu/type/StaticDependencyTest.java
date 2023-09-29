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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Set;

import org.junit.jupiter.api.Test;

import iu.type.api.StaticDependencyHelper;
import jakarta.annotation.Resource;
import jakarta.annotation.Resource.AuthenticationType;
import jakarta.annotation.Resources;
import jakarta.annotation.security.PermitAll;

@SuppressWarnings("javadoc")
public class StaticDependencyTest {

	@Test
	public void testAnnotationNotSupported() throws Exception {
		var resourceName = StaticDependencyHelper.class.getName().replace('.', '/') + ".class";
		var typeResource = StaticDependencyHelper.class.getClassLoader().getResource(resourceName).toExternalForm();
		var url = new URL(typeResource.substring(0, typeResource.length() - resourceName.length()));
		try (var loader = new URLClassLoader(new URL[] { url }) {
			@Override
			protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
				synchronized (getClassLoadingLock(name)) {
					if (name.startsWith("iu.") || name.startsWith("jakarta.")) {
						Class<?> rv = this.findLoadedClass(name);
						if (rv != null)
							return rv;
						rv = findClass(name);
						if (resolve)
							resolveClass(rv);
						return rv;
					} else
						return super.loadClass(name, resolve);
				}
			}
		}) {
			var staticDependencyHelper = loader.loadClass(StaticDependencyHelper.class.getName());
			assertNotSame(staticDependencyHelper, StaticDependencyHelper.class);
			assertFalse((Boolean) staticDependencyHelper.getMethod("isAnnotationSupported").invoke(null));

			var annotatedElement = mock(IuAnnotatedElement.class);
			assertFalse((Boolean) staticDependencyHelper.getMethod("hasPermitAll", IuAnnotatedElement.class)
					.invoke(null, annotatedElement));

			assertTrue(((Set<?>) staticDependencyHelper.getMethod("getResources", Class.class).invoke(null,
					HasResources.class)).isEmpty());
		}
	}

	@Test
	public void testAnnotationSupported() {
		assertTrue(StaticDependencyHelper.isAnnotationSupported());

		var annotatedElement = mock(IuAnnotatedElement.class);
		assertFalse(StaticDependencyHelper.hasPermitAll(annotatedElement));

		annotatedElement = mock(IuAnnotatedElement.class);
		when(annotatedElement.hasAnnotation(PermitAll.class)).thenReturn(true);
		assertTrue(StaticDependencyHelper.hasPermitAll(annotatedElement));
	}

	@Resources({ @Resource(name = "one", shareable = false), @Resource(type = String.class) })
	private interface HasResources {
	}

	@Resource(name = "no-auth", authenticationType = AuthenticationType.APPLICATION)
	private class HasResource {
	}

	@Test
	public void testResources() {
		try (var mockType = mockStatic(IuType.class)) {
			mockType.when(() -> IuType.of(any())).then(a -> {
				var type = mock(IuType.class);
				when(type.baseClass()).thenReturn(a.getArgument(0));
				return type;
			});

			var hasOne = false;
			var hasString = false;
			for (var resource : StaticDependencyHelper.getResources(HasResources.class)) {
				assertNull(resource.get());
				if (resource.needsAuthentication() && !resource.shared() && resource.name().equals("one")
						&& resource.type().baseClass() == Object.class)
					hasOne = true;
				if (resource.needsAuthentication() && resource.shared() && resource.name().isEmpty()
						&& resource.type().baseClass() == String.class)
					hasString = true;
			}
			assertTrue(hasOne);
			assertTrue(hasString);

			var resources = StaticDependencyHelper.getResources(HasResource.class);
			assertEquals(1, resources.size());
			var resource = resources.iterator().next();
			assertEquals("no-auth", resource.name());
			assertFalse(resource.needsAuthentication());
		}
	}

}
