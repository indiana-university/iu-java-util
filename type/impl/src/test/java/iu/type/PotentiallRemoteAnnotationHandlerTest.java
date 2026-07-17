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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import edu.iu.legacy.Incompatible;
import edu.iu.legacy.NotResource;
import jakarta.annotation.Resource;
import jakarta.annotation.Resources;

@ExtendWith(LegacyContextSupport.class)
@SuppressWarnings("javadoc")
public class PotentiallRemoteAnnotationHandlerTest {

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testConvert() throws Throwable {
		final var legacyResource = BackwardsCompatibility.getCompatibleClass(Resource.class, LegacyContextSupport.loader);
		assertEquals("javax.annotation.Resource", legacyResource.getName());
		final var legacyAnnotation = LegacyContextSupport.loader.loadClass("edu.iu.legacy.LegacyResource")
				.getAnnotation((Class) legacyResource);
		final var h = new PotentiallyRemoteAnnotationHandler(Resource.class, legacyAnnotation);
		final var o = new Object();
		assertSame(o, h.convert(o, Object.class));
		assertThrows(IllegalStateException.class, () -> h.convert(o, Object[].class));
		assertThrows(IllegalStateException.class, () -> h.convert(o, Resource.class));

		TypeUtils.callWithContext(legacyResource, () -> {
			final var r = assertInstanceOf(Resource.class, h.convert(legacyAnnotation, Resource.class));
			assertTrue(r.shareable());
			assertEquals(r, legacyAnnotation);
			assertEquals(Resource.AuthenticationType.CONTAINER, r.authenticationType());
			assertSame(Resource.class, r.annotationType());
			assertThrows(IllegalStateException.class, () -> h.convert(r, NotResource.class));
			return null;
		});
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testMulti() throws Throwable {
		final var legacyResources = BackwardsCompatibility.getCompatibleClass(Resources.class, LegacyContextSupport.loader);
		assertEquals("javax.annotation.Resources", legacyResources.getName());
		final var legacyAnnotation = LegacyContextSupport.loader.loadClass("edu.iu.legacy.LegacyResource")
				.getAnnotation((Class) legacyResources);
		final var h = new PotentiallyRemoteAnnotationHandler(Resources.class, legacyAnnotation);
		final var r = assertInstanceOf(Resources.class,
				TypeUtils.callWithContext(legacyResources, () -> h.convert(legacyAnnotation, Resources.class)));
		assertNotEquals(r, new Object());
		final var rs = r.value();
		assertEquals(2, rs.length);
		assertEquals(rs[0], rs[0]);
		assertNotEquals(rs[0], rs[1]);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testIncompatible() throws Throwable {
		final var incompatible = BackwardsCompatibility.getCompatibleClass(Incompatible.class, LegacyContextSupport.loader);
		final var legacyAnnotation = LegacyContextSupport.loader.loadClass("edu.iu.legacy.LegacyResource")
				.getAnnotation((Class) incompatible);
		final var h = new PotentiallyRemoteAnnotationHandler(Resources.class, legacyAnnotation);

		TypeUtils.callWithContext(incompatible, () -> {
			final var r = (Incompatible) h.convert(legacyAnnotation, Incompatible.class);
			assertThrows(IllegalStateException.class, () -> r.isAnEnum());
			assertThrows(IllegalStateException.class, () -> r.notAnEnum());
			return null;
		});
	}

}
