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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;

import org.junit.jupiter.api.Test;

import jakarta.annotation.Resource;
import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;

@SuppressWarnings("javadoc")
public class AnnotatedElementTest {

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testProperties() {
		var e = mock(AnnotatedElement.class);
		when(e.isAnnotationPresent(Resource.class)).thenReturn(true);

		var r = mock(Resource.class);
		when(r.annotationType()).thenReturn((Class) Resource.class);
		when(e.getAnnotation(Resource.class)).thenReturn(r);
		when(e.getAnnotations()).thenReturn(new Annotation[] { r });

		var annotatedElement = new AnnotatedElementBase<>(e, null);
		assertTrue(annotatedElement.hasAnnotation(Resource.class));
		assertSame(r, annotatedElement.annotation(Resource.class));
		var i = annotatedElement.annotations().iterator();
		assertTrue(i.hasNext());
		assertSame(r, i.next());
		assertFalse(i.hasNext());
	}

	@Test
	public void testJustDenyAllDenys() {
		@DenyAll
		interface JustDenysAll {
		}
		assertFalse(new AnnotatedElementBase<>(JustDenysAll.class, null).permitted());
	}

	@Test
	public void testPermitAndDenyAllStillDenys() {
		@PermitAll
		@DenyAll
		interface PermitsAndDenysAll {
		}
		assertFalse(new AnnotatedElementBase<>(PermitsAndDenysAll.class, null).permitted());
	}

	@Test
	public void testRoleAllowAndDenyAllStillDenys() {
		@RolesAllowed("should not work")
		@DenyAll
		interface AllowsRoleAndDenysAll {
		}
		assertFalse(new AnnotatedElementBase<>(AllowsRoleAndDenysAll.class, null)
				.permitted(r -> "should not work".equals(r)));
	}

	@Test
	public void testJustPermitAllPermits() {
		@PermitAll
		interface JustPermitAll {
		}
		assertTrue(new AnnotatedElementBase<>(JustPermitAll.class, null).permitted());
	}

	@Test
	public void testRoleAllowAndPermitAllStillPermits() {
		@RolesAllowed("would work")
		@PermitAll
		interface AllowsRoleAndPermitsAll {
		}
		assertTrue(new AnnotatedElementBase<>(AllowsRoleAndPermitsAll.class, null)
				.permitted(r -> "would not work".equals(r)));
	}

	@Test
	public void testRoleAllows() {
		@RolesAllowed("works")
		interface AllowsRole {
		}
		assertFalse(new AnnotatedElementBase<>(AllowsRole.class, null).permitted(r -> "will not work".equals(r)));
		assertTrue(new AnnotatedElementBase<>(AllowsRole.class, null).permitted(r -> "works".equals(r)));
	}

	@Test
	public void testDenyByDefault() {
		interface CantJustDoIt {
		}
		assertFalse(new AnnotatedElementBase<>(CantJustDoIt.class, null).permitted());
	}

}
