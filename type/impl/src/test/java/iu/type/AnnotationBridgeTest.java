/*
 * Copyright © 2025 Indiana University
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.reflect.AnnotatedElement;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import edu.iu.IuException;
import edu.iu.legacy.Incompatible;
import edu.iu.legacy.NotResource;
import jakarta.annotation.Resource;
import jakarta.annotation.Resources;
import jakarta.annotation.security.PermitAll;

@SuppressWarnings("javadoc")
@ExtendWith(LegacyContextSupport.class)
public class AnnotationBridgeTest extends IuTypeTestCase {

	@interface MissingAnnotation {
	}

	private Class<?> getLegacyResource() {
		return IuException.unchecked(() -> LegacyContextSupport.get().loadClass("edu.iu.legacy.LegacyResource"));
	}

	@Test
	public void testIsDirectlyPresent() {
		@Resource
		class HasResource {
		}
		assertTrue(AnnotationBridge.isAnnotationPresent(Resource.class, HasResource.class));
	}

	@Test
	public void testLegacyIsPresent() throws ClassNotFoundException {
		assertTrue(AnnotationBridge.isAnnotationPresent(Resource.class, getLegacyResource()));
	}

	@Test
	public void testLegacyIsNotPresentForMissingAnnotation() throws ClassNotFoundException {
		assertFalse(AnnotationBridge.isAnnotationPresent(MissingAnnotation.class, getLegacyResource()));
	}

	@Test
	public void testLegacyIsNotPresentForNonAnnotation() throws ClassNotFoundException {
		assertFalse(AnnotationBridge.isAnnotationPresent(NotResource.class, getLegacyResource()));
	}

	@Test
	public void testLegacyThrowsRuntimeException() throws ClassNotFoundException {
		try (var mockTypeUtils = mockStatic(TypeUtils.class)) {
			mockTypeUtils.when(() -> TypeUtils.callWithContext(any(AnnotatedElement.class), any()))
					.thenThrow(RuntimeException.class);
			assertThrows(RuntimeException.class,
					() -> AnnotationBridge.isAnnotationPresent(Resource.class, getLegacyResource()));
		}
	}

	@Test
	public void testIsDirectlyHasResource() {
		@Resource
		class HasResource {
		}
		assertInstanceOf(Resource.class, AnnotationBridge.getAnnotation(Resource.class, HasResource.class));
	}

	@Test
	public void testLegacyHasResource() throws ClassNotFoundException {
		assertInstanceOf(Resource.class, AnnotationBridge.getAnnotation(Resource.class, getLegacyResource()));
	}

	@Test
	public void testLegacyHasNullForMissingAnnotation() throws ClassNotFoundException {
		assertNull(AnnotationBridge.getAnnotation(MissingAnnotation.class, getLegacyResource()));
	}

	@Test
	public void testLegacyHasNullForNonAnnotation() throws ClassNotFoundException {
		assertNull(AnnotationBridge.getAnnotation(NotResource.class, getLegacyResource()));
	}

	@Test
	public void testLegacyHasNullForNonPresentAnnotation() throws ClassNotFoundException {
		assertNull(AnnotationBridge.getAnnotation(PermitAll.class, getLegacyResource()));
	}

	@Test
	public void testGetsNoAnnotations() throws ClassNotFoundException {
		class HasNoAnnotations {
		}
		assertFalse(AnnotationBridge.getAnnotations(HasNoAnnotations.class).iterator().hasNext());
	}

	@Test
	public void testGetsLegacyAnnotations() throws ClassNotFoundException {
		var annotations = AnnotationBridge.getAnnotations(getLegacyResource());
		Set<Class<?>> annotationTypes = new HashSet<>();
		for (var annotation : annotations)
			annotationTypes.add(annotation.annotationType());
		assertTrue(annotationTypes.contains(Resource.class));
		assertTrue(annotationTypes.contains(Resources.class));
		assertTrue(annotationTypes.contains(Incompatible.class));
	}

	@Test
	public void testGetsPlatformBaseAnnotations() throws ClassNotFoundException {
		var annotations = AnnotationBridge.getAnnotations(
				IuException.unchecked(() -> LegacyContextSupport.get().loadClass("edu.iu.legacy.Incompatible")));
		Set<Class<?>> annotationTypes = new HashSet<>();
		for (var annotation : annotations)
			annotationTypes.add(annotation.annotationType());
		assertTrue(annotationTypes.contains(Documented.class));
		assertTrue(annotationTypes.contains(Retention.class));
	}

}
