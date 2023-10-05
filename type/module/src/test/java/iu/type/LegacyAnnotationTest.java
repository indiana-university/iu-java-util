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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Annotation;
import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import edu.iu.legacy.Incompatible;
import jakarta.annotation.Resource;
import jakarta.annotation.Resource.AuthenticationType;
import jakarta.annotation.Resources;

@SuppressWarnings("javadoc")
@ExtendWith(LegacyContextSupport.class)
public class LegacyAnnotationTest {

	private Class<?> getLegacyResourceClass() throws Throwable {
		return LegacyContextSupport.get().loadClass("edu.iu.legacy.LegacyResource");
	}

	private Resource getLegacyResource() throws Throwable {
		var legacyAnnotationType = LegacyContextSupport.get().loadClass("javax.annotation.Resource")
				.asSubclass(Annotation.class);
		var legacyAnnotation = getLegacyResourceClass().getAnnotation(legacyAnnotationType);
		return (Resource) Proxy.newProxyInstance(Resource.class.getClassLoader(), new Class<?>[] { Resource.class },
				new LegacyAnnotationHandler(Resource.class, legacyAnnotation));
	}

	private Resources getLegacyResources() throws Throwable {
		var legacyAnnotationType = LegacyContextSupport.get().loadClass("javax.annotation.Resources")
				.asSubclass(Annotation.class);
		var legacyAnnotation = getLegacyResourceClass().getAnnotation(legacyAnnotationType);
		return (Resources) Proxy.newProxyInstance(Resources.class.getClassLoader(), new Class<?>[] { Resources.class },
				new LegacyAnnotationHandler(Resources.class, legacyAnnotation));
	}

	@Test
	public void testAnnotationType() throws Throwable {
		assertSame(Resource.class, getLegacyResource().annotationType());
	}

	@Test
	public void testSameIsEqual() throws Throwable {
		var resource = getLegacyResource();
		assertEquals(resource, resource);
	}

	@Test
	public void testLegacyIsEqual() throws Throwable {
		var resource = getLegacyResource();
		var loader = LegacyContextSupport.get();
		var legacyAnnotationType = loader.loadClass("javax.annotation.Resource").asSubclass(Annotation.class);
		var legacyAnnotation = loader.loadClass("edu.iu.legacy.LegacyResource").getAnnotation(legacyAnnotationType);
		assertEquals(resource, legacyAnnotation);
	}

	@Test
	public void testNonLegacyIsEqual() throws Throwable {
		@Resource
		class NonLegacyResource {
		}
		assertEquals(getLegacyResource(), NonLegacyResource.class.getAnnotation(Resource.class));
	}

	@Test
	public void testDifferentValuesIsNotEqual() throws Throwable {
		@Resource(authenticationType = AuthenticationType.APPLICATION)
		class NonLegacyResource {
		}
		assertNotEquals(getLegacyResource(), NonLegacyResource.class.getAnnotation(Resource.class));
	}

	@Test
	public void testWrongTypeIsNotEqual() throws Throwable {
		var resource = getLegacyResource();
		assertNotEquals(resource, this);
	}

	@Test
	public void testResourcesHasCorrectValues() throws Throwable {
		var resources = getLegacyResources();
		assertEquals(2, resources.value().length);
		assertEquals("one", resources.value()[0].name());
		assertSame(LegacyContextSupport.get().loadClass("edu.iu.legacy.LegacyBean"), resources.value()[0].type());
		assertEquals("two", resources.value()[1].name());
	}

	@Test
	public void testIncompatibleConversion() throws Throwable {
		var legacyAnnotationType = LegacyContextSupport.get().loadClass(Incompatible.class.getName())
				.asSubclass(Annotation.class);
		var legacyAnnotation = getLegacyResourceClass().getAnnotation(legacyAnnotationType);
		var incompatible = (Incompatible) Proxy.newProxyInstance(Incompatible.class.getClassLoader(),
				new Class<?>[] { Incompatible.class },
				new LegacyAnnotationHandler(Incompatible.class, legacyAnnotation));
		assertTrue(assertThrows(IllegalStateException.class, incompatible::notAnEnum).getMessage()
				.startsWith("cannot convert "));
		assertTrue(assertThrows(IllegalStateException.class, incompatible::notAnArray).getMessage()
				.startsWith("cannot convert "));
		assertTrue(assertThrows(IllegalStateException.class, incompatible::notResource).getMessage()
				.startsWith("cannot convert "));
		assertTrue(assertThrows(IllegalStateException.class, incompatible::isAnArray).getMessage()
				.startsWith("cannot convert "));
		assertTrue(assertThrows(IllegalStateException.class, incompatible::isAnEnum).getMessage()
				.startsWith("cannot convert "));
		assertTrue(assertThrows(IllegalStateException.class, incompatible::isResource).getMessage()
				.startsWith("cannot convert "));
	}

}
