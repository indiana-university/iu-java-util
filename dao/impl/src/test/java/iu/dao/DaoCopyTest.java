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
package iu.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Covers how each shape of entity is protected from the reader that gets it. */
@SuppressWarnings("javadoc")
public class DaoCopyTest {

	/** A plain mutable entity: no clone(), so it is copied field by field. */
	public static class Mutable {
		private String value;
		String other;

		public Mutable() {
		}

		Mutable(String value) {
			this.value = value;
		}

		String value() {
			return value;
		}
	}

	/** Copies itself, and says so. */
	public static class Cloner implements Cloneable {
		String value;

		Cloner(String value) {
			this.value = value;
		}

		@Override
		public Cloner clone() {
			final var copy = new Cloner(value);
			copy.value = value + "-cloned";
			return copy;
		}
	}

	/** {@link Cloneable} without a public clone(), so the field copy is used. */
	public static class PrivatelyCloneable implements Cloneable {
		String value;

		public PrivatelyCloneable() {
		}

		PrivatelyCloneable(String value) {
			this.value = value;
		}
	}

	/** No no-argument constructor and no clone(): nothing to copy it with. */
	public static class Uncopyable {
		Uncopyable(String value) {
		}
	}

	/**
	 * A private no-argument constructor, which the DAO also accepts for
	 * materializing a row.
	 */
	public static class PrivateConstructor {
		String value;

		private PrivateConstructor() {
		}

		PrivateConstructor(String value) {
			this.value = value;
		}
	}

	/** An immutable carrier. */
	public record Row(String id, String value) {
	}

	/** An interface-mapped entity. */
	public interface View {
		String getId();
	}

	@Test
	public void testAFieldCopyReachesEveryField() {
		final var original = new Mutable("one");
		original.other = "two";

		final var copy = (Mutable) DaoCopy.copyOf(original);
		assertNotSame(original, copy);

		// the private field has no setter, and a mapped column need not have one
		assertEquals("one", copy.value());
		assertEquals("two", copy.other);
	}

	@Test
	public void testAPrivateNoArgumentConstructorIsStillUsable() {
		final var original = new PrivateConstructor("one");
		final var copy = (PrivateConstructor) DaoCopy.copyOf(original);

		assertNotSame(original, copy);
		assertEquals("one", copy.value);
	}

	@Test
	public void testAnEntityThatClonesItselfIsAskedTo() {
		final var copy = (Cloner) DaoCopy.copyOf(new Cloner("one"));
		assertEquals("one-cloned", copy.value);
	}

	@Test
	public void testCloneableWithoutAPublicCloneFallsBackToTheFieldCopy() {
		final var original = new PrivatelyCloneable("one");
		final var copy = (PrivatelyCloneable) DaoCopy.copyOf(original);

		assertNotSame(original, copy);
		assertEquals("one", copy.value);
	}

	@Test
	public void testImmutableRowsAreSharedRatherThanCopied() {
		final var record = new Row("a", "one");
		assertSame(record, DaoCopy.copyOf(record));

		final var view = Proxy.newProxyInstance(View.class.getClassLoader(), new Class<?>[] { View.class },
				(proxy, method, args) -> "a");
		assertSame(view, DaoCopy.copyOf(view));

		assertTrue(DaoCopy.immutable(View.class));
		assertTrue(DaoCopy.immutable(Row.class));
		assertFalse(DaoCopy.immutable(Mutable.class));
	}

	@Test
	public void testAListOfImmutableRowsIsSharedWhole() {
		final List<?> rows = List.of(new Row("a", "one"));
		assertSame(rows, DaoCopy.copyOf(Row.class, rows));
	}

	@Test
	public void testAListOfMutableRowsIsRebuiltFromCopies() {
		final var original = new Mutable("one");
		final var copied = DaoCopy.copyOf(Mutable.class, List.of(original));

		assertEquals(1, copied.size());
		assertNotSame(original, copied.get(0));
		assertThrows(UnsupportedOperationException.class, () -> ((List<Object>) copied).add(null));
	}

	@Test
	public void testANullRowCopiesToNothing() {
		assertNull(DaoCopy.copyOf((Object) null));
		assertEquals(Collections.singletonList(null), DaoCopy.copyOf(Mutable.class, Collections.singletonList(null)));
	}

	@Test
	public void testATypeWithNoWayToCopyItIsRejected() {
		final var e = assertThrows(IllegalArgumentException.class, () -> DaoCopy.copyOf(new Uncopyable("one")));
		assertEquals("Cannot copy " + Uncopyable.class.getName()
				+ "; an entity read through a cached DAO must be Cloneable, a record, or have a no-argument constructor",
				e.getMessage());
	}
}
