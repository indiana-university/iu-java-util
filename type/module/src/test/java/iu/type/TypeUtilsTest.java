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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import edu.iu.type.IuType;

@SuppressWarnings("javadoc")
@ExtendWith(LegacyContextSupport.class)
public class TypeUtilsTest extends IuTypeTestCase {

	@Test
	public void testPlatformClassNames() throws Throwable {
		assertTrue(TypeUtils.isPlatformType("jakarta."));
		assertTrue(TypeUtils.isPlatformType("java."));
		assertTrue(TypeUtils.isPlatformType("javax."));
		assertTrue(TypeUtils.isPlatformType("jdk."));
		assertFalse(TypeUtils.isPlatformType("iu."));
	}

	@Test
	public void testCallWithContextOfClass() throws Throwable {
		TypeUtils.callWithContext(LegacyContextSupport.get().loadClass("edu.iu.legacy.LegacyBean"), () -> {
			assertSame(LegacyContextSupport.get(), Thread.currentThread().getContextClassLoader());
			return null;
		});
	}

	@Test
	public void testCallWithClassContext() throws Throwable {
		TypeUtils.callWithContext(LegacyContextSupport.get(), () -> {
			assertSame(LegacyContextSupport.get(), Thread.currentThread().getContextClassLoader());
			return null;
		});
	}

	@Test
	public void testContextOfClass() throws ClassNotFoundException {
		var c = LegacyContextSupport.get().loadClass("edu.iu.legacy.LegacyBean");
		assertSame(LegacyContextSupport.get(), TypeUtils.getContext(c));
	}

	@Test
	public void testContextOfField() throws ClassNotFoundException, NoSuchFieldException {
		var c = LegacyContextSupport.get().loadClass("edu.iu.legacy.LegacyBean");
		assertSame(LegacyContextSupport.get(), TypeUtils.getContext(c.getDeclaredField("foo")));
	}

	@Test
	public void testContextOfMethod() throws ClassNotFoundException, NoSuchMethodException {
		var c = LegacyContextSupport.get().loadClass("edu.iu.legacy.LegacyBean");
		assertSame(LegacyContextSupport.get(), TypeUtils.getContext(c.getDeclaredMethod("getFoo")));
	}

	@Test
	public void testContextOfParameter() throws ClassNotFoundException, NoSuchMethodException {
		var c = LegacyContextSupport.get().loadClass("edu.iu.legacy.LegacyBean");
		assertSame(LegacyContextSupport.get(),
				TypeUtils.getContext(c.getDeclaredMethod("setFoo", String.class).getParameters()[0]));
	}

	@Test
	public void testContextOfPackageNotSupported() throws ClassNotFoundException {
		var c = LegacyContextSupport.get().loadClass("edu.iu.legacy.LegacyBean");
		assertEquals("Cannot determine context for package edu.iu.legacy",
				assertThrows(UnsupportedOperationException.class, () -> TypeUtils.getContext(c.getPackage()))
						.getMessage());
	}

	@Test
	public void testPrintArrayType() {
		assertEquals("byte[]", TypeUtils.printType(byte[].class));
	}

	@Test
	public void testPrintGenericArrayType() throws Exception {
		@SuppressWarnings("unused")
		class HasGenericArray<B> {
			B[] genericArray;
		}
		assertEquals("B[]",
				TypeUtils.printType(HasGenericArray.class.getDeclaredField("genericArray").getGenericType()));
	}

	@Test
	public void testMultipleTypeParams() throws Exception {
		assertEquals("ExecutableBase<D,R,Method>", TypeUtils.printType(MethodFacade.class.getGenericSuperclass()));
	}

	@Test
	public void testReferToMustBePresentInHierarchy() throws Exception {
		assertEquals("String not present in type hierarchy for IuType[Integer]; "
				+ "[IuType[ConstantDesc SUPER Integer], IuType[Constable SUPER Integer], IuType[Comparable<Integer> SUPER Integer], "
				+ "IuType[Number SUPER Integer], IuType[Serializable SUPER Number SUPER Integer], IuType[Object SUPER Number SUPER Integer]]",
				assertThrows(IllegalArgumentException.class, () -> IuType.of(Integer.class).referTo(String.class))
						.getMessage());
	}

	// TODO: REMOVE
//	@Test
//	public void testUpperBounds() {
//		class HasBounds<N extends Number> {
//		}
//		Map<String, TypeFacade<?>> params = new HashMap<>();
//		params.put("N", (TypeFacade<?>) IuType.of(HasBounds.class).typeParameter("N"));
//
//		Map<String, TypeFacade<?>> args = new HashMap<>();
//		args.put("N", (TypeFacade<?>) IuType.of(String.class).referTo(CharSequence.class));
//
//		assertEquals("Type argument IuType[CharSequence SUPER String] doesn't match upper bound Number",
//				assertThrows(IllegalArgumentException.class, () -> TypeUtils.sealTypeParameters(params, args))
//						.getMessage());
//	}
//
//	@Test
//	public void testLowerBoundsMismatch() {
//		@SuppressWarnings("unused")
//		class HasBounds<N extends Number> {
//			Class<? super N> hasLowerBound;
//		}
//		Map<String, TypeFacade<?>> params = new HashMap<>();
//		params.put("N", (TypeFacade<?>) IuType.of(HasBounds.class).field("hasLowerBound").type().typeParameter("T"));
//
//		Map<String, TypeFacade<?>> args = new HashMap<>();
//		args.put("N", (TypeFacade<?>) IuType.of(String.class).referTo(CharSequence.class));
//
//		assertEquals("Type argument IuType[CharSequence SUPER String] doesn't match lower bound N",
//				assertThrows(IllegalArgumentException.class, () -> TypeUtils.sealTypeParameters(params, args))
//						.getMessage());
//	}
//
//	@Test
//	public void testRetainsLowerBoundedWildcard() {
//		@SuppressWarnings("unused")
//		class HasBounds {
//			Class<? super Number> hasLowerBound;
//		}
//		Map<String, TypeFacade<?>> params = new HashMap<>();
//		params.put("", (TypeFacade<?>) IuType.of(HasBounds.class).field("hasLowerBound").type().typeParameter("T"));
//
//		assertSame(params.get(""), TypeUtils.sealTypeParameters(params, new HashMap<>()).get(""));
//	}
//
//	@Test
//	public void testLowerBoundsMatch() {
//		@SuppressWarnings("unused")
//		class HasBounds<N extends Number> {
//			Class<? super N> hasLowerBound;
//		}
//		Map<String, TypeFacade<?>> params = new HashMap<>();
//		params.put("N", (TypeFacade<?>) IuType.of(HasBounds.class).field("hasLowerBound").type().typeParameter("T"));
//
//		Map<String, TypeFacade<?>> args = new HashMap<>();
//		args.put("N", (TypeFacade<?>) IuType.of(Integer.class).referTo(Serializable.class));
//
//		assertSame(args.get("N"), TypeUtils.sealTypeParameters(params, args).get("N"));
//	}
//
}
