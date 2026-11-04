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
package edu.iu.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import edu.iu.UnsafeSupplier;

@SuppressWarnings({ "javadoc", "unused" })
public class BindResourcesTest {

	@Retention(RetentionPolicy.RUNTIME)
	@interface Bind {
	}

	@interface Unbound {
	}

	static class Extension {
		String text = "foo";
		Integer count = 42;
	}

	static class Dep {
		@Bind
		String text;
		@Bind
		UnsafeSupplier<?> supplier;
		String unbound;
	}

	static class Holder {
		@Bind
		Dep dep;
		@Bind
		UnsafeSupplier<?> supplier;
	}

	static class Other {
	}

	static class Basic {
		@Bind
		String text;
		@Bind
		Dep dep;
		@Bind
		Holder holder;
		@Bind
		UnsafeSupplier<?> supplier;
		String unbound;
	}

	static class Parent {
		@Bind
		String text;
	}

	static class Child extends Parent {
		@Bind
		Dep dep;
	}

	static class Missing {
		@Bind
		String missing;
	}

	static class Mismatch {
		@Bind
		String count;
	}

	static class CollidingHolder {
		@Bind
		Other thing;
	}

	static class Colliding {
		@Bind
		Dep thing;
		@Bind
		CollidingHolder holder;
	}

	@Test
	public void testBindsAllResourceKinds() {
		final var basic = new Basic();
		IuTest.bindResources(basic, new Extension(), Bind.class);

		// extension field, platform type
		assertEquals("foo", basic.text);
		assertEquals("foo", basic.dep.text);

		// same-package singleton, shared by name and annotation
		assertNotNull(basic.dep);
		assertSame(basic.dep, basic.holder.dep);

		// non-platform, non-package type is a shared singleton mock
		assertTrue(Mockito.mockingDetails(basic.supplier).isMock());
		assertSame(basic.supplier, basic.dep.supplier);
		assertSame(basic.supplier, basic.holder.supplier);
		assertInstanceOf(UnsafeSupplier.class, basic.supplier);

		// unannotated fields are untouched
		assertEquals(null, basic.unbound);
		assertEquals(null, basic.dep.unbound);
	}

	@Test
	public void testBindsSuperclassFields() {
		final var child = new Child();
		IuTest.bindResources(child, new Extension(), Bind.class);
		assertEquals("foo", child.text);
		assertNotNull(child.dep);
		assertNotSame(child, child.dep);
	}

	@Test
	public void testIgnoresOtherAnnotations() {
		final var basic = new Basic();
		IuTest.bindResources(basic, new Extension(), Unbound.class);
		assertEquals(null, basic.text);
		assertEquals(null, basic.dep);
	}

	@Test
	public void testMissingPlatformField() {
		final var e = assertThrows(IllegalStateException.class,
				() -> IuTest.bindResources(new Missing(), new Extension(), Bind.class));
		assertInstanceOf(NoSuchFieldException.class, e.getCause());
	}

	@Test
	public void testMismatchedExtensionFieldIsNotCopied() {
		final var e = assertThrows(IllegalStateException.class,
				() -> IuTest.bindResources(new Mismatch(), new Extension(), Bind.class));
		assertInstanceOf(NoSuchFieldException.class, e.getCause());
	}

	@Test
	public void testNameCollisionBetweenTypes() {
		final var e = assertThrows(IllegalArgumentException.class,
				() -> IuTest.bindResources(new Colliding(), new Extension(), Bind.class));
		assertEquals(1, e.getSuppressed().length);
		assertInstanceOf(NoSuchFieldException.class, e.getSuppressed()[0]);
	}
}
