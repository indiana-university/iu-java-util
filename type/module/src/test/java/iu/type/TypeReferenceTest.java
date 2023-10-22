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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import edu.iu.type.IuReferenceKind;

@SuppressWarnings("javadoc")
public class TypeReferenceTest {

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testNonArgumentKindIsStable() {
		var type = mock(TypeFacade.class);
		var ref = new TypeReference(IuReferenceKind.ERASURE, type, type);
		assertEquals(IuReferenceKind.ERASURE, ref.kind());
		assertSame(type, ref.referent());
		assertSame(type, ref.referrer());
		assertNull(ref.name());
		assertTrue(ref.index() < 0);
		assertEquals(ref, ref);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testNonArgumentKindConstructorRequiresNonArgumentKind() {
		var type = mock(TypeFacade.class);
		var field = mock(FieldFacade.class);
		assertThrows(AssertionError.class, () -> new TypeReference(IuReferenceKind.FIELD, field, type));
		var param = mock(ParameterFacade.class);
		assertThrows(AssertionError.class, () -> new TypeReference(IuReferenceKind.PARAMETER, param, type));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testNamedArgumentKindIsStable() {
		var type = mock(TypeFacade.class);
		var field = mock(FieldFacade.class);
		var ref = new TypeReference(IuReferenceKind.FIELD, field, type, "");
		assertEquals(IuReferenceKind.FIELD, ref.kind());
		assertSame(type, ref.referent());
		assertSame(field, ref.referrer());
		assertEquals("", ref.name());
		assertTrue(ref.index() < 0);
		assertEquals(ref, ref);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testNamedArgumentKindConstructorRequiresNamedArgumentKind() {
		var type = mock(TypeFacade.class);
		var param = mock(ParameterFacade.class);
		assertThrows(AssertionError.class, () -> new TypeReference(IuReferenceKind.PARAMETER, param, type, ""));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testIndexedArgumentKindIsStable() {
		var type = mock(TypeFacade.class);
		var param = mock(ParameterFacade.class);
		var ref = new TypeReference(IuReferenceKind.PARAMETER, param, type, 0);
		assertEquals(IuReferenceKind.PARAMETER, ref.kind());
		assertSame(type, ref.referent());
		assertSame(param, ref.referrer());
		assertNull(ref.name());
		assertEquals(0, ref.index());
		assertEquals(ref, ref);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testIndexedArgumentKindConstructorRequiresIndexedArgumentKind() {
		var type = mock(TypeFacade.class);
		var field = mock(FieldFacade.class);
		assertThrows(AssertionError.class, () -> new TypeReference(IuReferenceKind.FIELD, field, type, 0));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testIndexedArgumentKindConstructorRequiresValidIndex() {
		var type = mock(TypeFacade.class);
		var param = mock(ParameterFacade.class);
		assertThrows(AssertionError.class, () -> new TypeReference(IuReferenceKind.PARAMETER, param, type, -1));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testEquivalence() {
		var type1 = mock(TypeFacade.class);
		var type2 = mock(TypeFacade.class);
		var ref1 = new TypeReference(IuReferenceKind.ERASURE, type1, type2);
		assertEquals(ref1, new TypeReference(IuReferenceKind.ERASURE, type1, type2));
		var ref2 = new TypeReference(IuReferenceKind.ERASURE, type2, type1);
		var set = new HashSet<>(Set.of(ref1, ref2));
		assertTrue(set.contains(ref1));
		assertTrue(set.contains(ref2));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testEqualsNullCheck() {
		var type = mock(TypeFacade.class);
		var ref = new TypeReference(IuReferenceKind.ERASURE, type, type);
		assertNotEquals(ref, null);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testReferrerUsesIdentityForEquals() {
		var type1 = new TypeTemplate<>(Object.class, a -> {
		});
		type1.sealHierarchy(List.of());
		var type2 = new TypeTemplate<>(Object.class, a -> {
		});
		type2.sealHierarchy(List.of());
		var ref1 = new TypeReference(IuReferenceKind.ERASURE, type1, type1);
		var ref2 = new TypeReference(IuReferenceKind.ERASURE, type2, type1);
		assertNotEquals(ref1, ref2);
		assertNotEquals(ref2, ref1);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testDifferentNamesNotEquals() {
		var type = mock(TypeFacade.class);
		var field = mock(FieldFacade.class);
		var ref1 = new TypeReference(IuReferenceKind.FIELD, field, type, "a");
		var ref2 = new TypeReference(IuReferenceKind.FIELD, field, type, "b");
		assertNotEquals(ref1, ref2);
		assertNotEquals(ref2, ref1);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testDifferentReferentNotEquals() {
		var type1 = mock(TypeFacade.class);
		var type2 = mock(TypeFacade.class);
		var field = mock(FieldFacade.class);
		var ref1 = new TypeReference(IuReferenceKind.FIELD, field, type1, "a");
		var ref2 = new TypeReference(IuReferenceKind.FIELD, field, type2, "a");
		assertNotEquals(ref1, ref2);
		assertNotEquals(ref2, ref1);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testDifferentIndexNotEquals() {
		var type = mock(TypeFacade.class);
		var param = mock(ParameterFacade.class);
		var ref1 = new TypeReference(IuReferenceKind.PARAMETER, param, type, 1);
		var ref2 = new TypeReference(IuReferenceKind.PARAMETER, param, type, 2);
		assertNotEquals(ref1, ref2);
		assertNotEquals(ref2, ref1);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testDifferentKindsNotEquals() {
		var type = mock(TypeFacade.class);
		var ref1 = new TypeReference(IuReferenceKind.ERASURE, type, type);
		var ref2 = new TypeReference(IuReferenceKind.SUPER, type, type);
		assertNotEquals(ref1, ref2);
		assertNotEquals(ref2, ref1);
	}

}