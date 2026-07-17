/*
 * Copyright © 2026 Indiana University
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;

import edu.iu.UnsafeSupplier;
import jakarta.annotation.Priority;
import jakarta.annotation.Resource;

@SuppressWarnings("javadoc")
public class AnnotationBridgeTest {

	@Test
	public void testPotentiallyRemote() {
		final var annotatedElement = mock(AnnotatedElement.class);
		final var localClass = getClass();
		try (final var mockBackwardsCompatibility = mockStatic(BackwardsCompatibility.class);
				final var mockTypeUtils = mockStatic(TypeUtils.class)) {
			AnnotationBridge.getPotentiallyRemoteClass(annotatedElement, localClass);
			final var bc = new ArgumentMatcher<UnsafeSupplier<Class<?>>>() {
				UnsafeSupplier<Class<?>> supplier;

				@Override
				public boolean matches(UnsafeSupplier<Class<?>> supplier) {
					this.supplier = supplier;
					return true;
				}
			};
			mockTypeUtils.verify(() -> TypeUtils.callWithContext(eq(annotatedElement), argThat(bc)));
			assertDoesNotThrow(bc.supplier::get);
			mockBackwardsCompatibility.verify(() -> BackwardsCompatibility.getCompatibleClass(localClass));
		}
	}

	@Test
	public void testAnnotationPresentDirect() {
		final var annotatedElement = mock(AnnotatedElement.class);
		when(annotatedElement.isAnnotationPresent(Annotation.class)).thenReturn(true);
		assertTrue(AnnotationBridge.isAnnotationPresent(Annotation.class, annotatedElement));
	}

	@Test
	public void testAnnotationPresentNoClassDef() {
		try (final var mockBackwardsCompatibility = mockStatic(BackwardsCompatibility.class)) {
			mockBackwardsCompatibility.when(() -> BackwardsCompatibility.getCompatibleClass(Annotation.class))
					.thenThrow(NoClassDefFoundError.class);
			assertFalse(AnnotationBridge.isAnnotationPresent(Annotation.class, getClass()));
		}
	}

	@Test
	public void testAnnotationPresentNonAnnotation() {
		try (final var mockBackwardsCompatibility = mockStatic(BackwardsCompatibility.class)) {
			mockBackwardsCompatibility.when(() -> BackwardsCompatibility.getCompatibleClass(Annotation.class))
					.thenReturn(Object.class);
			assertFalse(AnnotationBridge.isAnnotationPresent(Annotation.class, getClass()));
		}
	}

	@Test
	public void testAnnotationPresentDifferentAnnotation() {
		@Resource
		class A {
		}
		try (final var mockBackwardsCompatibility = mockStatic(BackwardsCompatibility.class)) {
			mockBackwardsCompatibility.when(() -> BackwardsCompatibility.getCompatibleClass(Annotation.class))
					.thenReturn(Resource.class);
			assertTrue(AnnotationBridge.isAnnotationPresent(Annotation.class, A.class));
		}
	}

	@Test
	public void testGetAnnotationDirect() {
		final var annotatedElement = mock(AnnotatedElement.class);
		final var annotation = mock(Annotation.class);
		when(annotatedElement.getAnnotation(Annotation.class)).thenReturn(annotation);
		assertSame(annotation, AnnotationBridge.getAnnotation(Annotation.class, annotatedElement));
	}

	@Test
	public void testGetAnnotationNoClassDef() {
		try (final var mockBackwardsCompatibility = mockStatic(BackwardsCompatibility.class)) {
			mockBackwardsCompatibility.when(() -> BackwardsCompatibility.getCompatibleClass(Annotation.class))
					.thenThrow(NoClassDefFoundError.class);
			assertNull(AnnotationBridge.getAnnotation(Annotation.class, getClass()));
		}
	}

	@Test
	public void testGetAnnotationNonAnnotation() {
		try (final var mockBackwardsCompatibility = mockStatic(BackwardsCompatibility.class)) {
			mockBackwardsCompatibility.when(() -> BackwardsCompatibility.getCompatibleClass(Annotation.class))
					.thenReturn(Object.class);
			assertNull(AnnotationBridge.getAnnotation(Annotation.class, getClass()));
		}
	}

	@Test
	public void testGetAnnotationDifferentAnnotation() {
		@Resource
		class A {
		}
		try (final var mockBackwardsCompatibility = mockStatic(BackwardsCompatibility.class)) {
			mockBackwardsCompatibility.when(() -> BackwardsCompatibility.getCompatibleClass(Annotation.class))
					.thenReturn(Resource.class);
			assertNull(AnnotationBridge.getAnnotation(Annotation.class, getClass()));
			final var a = assertInstanceOf(Annotation.class, AnnotationBridge.getAnnotation(Annotation.class, A.class));
			assertInstanceOf(PotentiallyRemoteAnnotationHandler.class, Proxy.getInvocationHandler(a));
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testGetAnnotationsDirect() {
		final var annotatedElement = mock(AnnotatedElement.class);
		final var annotation = mock(Annotation.class);
		when(annotation.annotationType()).thenReturn((Class) Annotation.class);
		when(annotatedElement.getAnnotations()).thenReturn(new Annotation[] { annotation });
		assertSame(annotation, AnnotationBridge.getAnnotations(annotatedElement).iterator().next());
	}

	@Test
	public void testGetAnnotationsNoClassDef() {
		@Resource
		class A {
		}
		try (final var mockBackwardsCompatibility = mockStatic(BackwardsCompatibility.class)) {
			mockBackwardsCompatibility.when(() -> BackwardsCompatibility.getCompatibleClass(Resource.class))
					.thenThrow(NoClassDefFoundError.class);
			assertFalse(AnnotationBridge.getAnnotations(A.class).iterator().hasNext());
		}
	}

	@Test
	public void testGetAnnotationsNonAnnotation() {
		@Resource
		class A {
		}
		try (final var mockBackwardsCompatibility = mockStatic(BackwardsCompatibility.class)) {
			mockBackwardsCompatibility.when(() -> BackwardsCompatibility.getCompatibleClass(Resource.class))
					.thenReturn(String.class);
			assertFalse(AnnotationBridge.getAnnotations(A.class).iterator().hasNext());
		}

	}

	@Test
	public void testGetAnnotationsDifferentAnnotation() {
		@Resource
		class A {
		}
		class B {
		}
		try (final var mockBackwardsCompatibility = mockStatic(BackwardsCompatibility.class)) {
			mockBackwardsCompatibility.when(() -> BackwardsCompatibility.getCompatibleClass(Resource.class))
					.thenReturn(Priority.class);
			assertFalse(AnnotationBridge.getAnnotations(B.class).iterator().hasNext());
			final var a = assertInstanceOf(Annotation.class,
					AnnotationBridge.getAnnotations(A.class).iterator().next());
			assertInstanceOf(PotentiallyRemoteAnnotationHandler.class, Proxy.getInvocationHandler(a));
		}
	}

}
