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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import edu.iu.IuIterable;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;

@SuppressWarnings("javadoc")
public class TypeFacadeTest extends IuTypeTestCase {

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void testDelegates() {
		final var facade = (TypeFacade) TypeFactory.resolveRawClass(getClass()).referTo(Object.class);
		assertEquals(facade.name(), facade.template.name());
		assertEquals(facade.erase(), facade.template.erase());
		assertEquals(facade.autoboxClass(), facade.template.autoboxClass());
		assertEquals(facade.autoboxDefault(), facade.template.autoboxDefault());
		assertEquals(facade.declaringType(), facade.template.declaringType());
		assertEquals(facade.sub(Object.class), facade.template.sub(Object.class));
		assertEquals(facade.enclosedTypes(), facade.template.enclosedTypes());
		assertEquals(facade.constructors(), facade.template.constructors());
		assertEquals(facade.fields(), facade.template.fields());
		assertEquals(facade.properties(), facade.template.properties());
	}

	@Test
	@SuppressWarnings({ "rawtypes" })
	public void testHierarchyRefersBackToFacade() {
		class A {}
		class B extends A {}
		final var facade = (TypeFacade) TypeFactory.resolveRawClass(B.class).referTo(A.class);
		assertSame(facade, facade.referTo(Object.class).reference().referrer());
	}

	@Test
	public void testAnnotations() {
		@Resource
		class AnnotatedClass {
		}
		class CanReferToAnnotatedClass extends AnnotatedClass {
		}
		final var facade = TypeFactory.resolveRawClass(CanReferToAnnotatedClass.class).referTo(AnnotatedClass.class);
		assertInstanceOf(TypeFacade.class, facade);
		var resource = facade.annotation(Resource.class);
		assertTrue(IuIterable.remaindersAreEqual(List.of(resource).iterator(), facade.annotations().iterator()));
	}

	@Test
	public void testPermitAll() {
		@PermitAll
		class AnnotatedClass {
		}
		class CanReferToAnnotatedClass extends AnnotatedClass {
		}
		final var facade = TypeFactory.resolveRawClass(CanReferToAnnotatedClass.class).referTo(AnnotatedClass.class);
		assertInstanceOf(TypeFacade.class, facade);
		assertTrue(facade.permitted());
	}

	@Test
	public void testPermitSome() {
		@RolesAllowed("Some")
		class AnnotatedClass {
		}
		class CanReferToAnnotatedClass extends AnnotatedClass {
		}
		final var facade = TypeFactory.resolveRawClass(CanReferToAnnotatedClass.class).referTo(AnnotatedClass.class);
		assertInstanceOf(TypeFacade.class, facade);
		assertTrue(facade.permitted(role -> role.equals("Some")));
	}

}
