/*
 * Copyright © 2024 Indiana University
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
package edu.iu;

import static edu.iu.IuIterable.cat;
import static edu.iu.IuIterable.empty;
import static edu.iu.IuIterable.filter;
import static edu.iu.IuIterable.iter;
import static edu.iu.IuIterable.map;
import static edu.iu.IuIterable.of;
import static edu.iu.IuIterable.print;
import static edu.iu.IuIterable.remaindersAreEqual;
import static edu.iu.IuIterable.select;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuIterableTest {

	@Test
	public void testNullToString() {
		assertEquals("null", print((Iterable<?>) null));
		assertEquals("null", print((Iterator<?>) null));
	}

	@Test
	public void testEmptyToString() {
		assertEquals("[]", print(() -> Collections.emptyIterator()));
	}

	@Test
	public void testToStringOfOne() {
		assertEquals("[one]", print(List.of("one")));
	}

	@Test
	public void testToStringOfTwo() {
		assertEquals("[one, two]", print(List.of("one", "two")));
		assertEquals("[one, two]", print(IuIterable.iter("one", "two")));
	}

	@Test
	public void testIteratorToStrings() {
		var iter = iter("one", "two", "three").iterator();
		assertEquals("[one, two, three]", iter.toString());
		assertEquals("one", iter.next());
		assertEquals("[..., two, three]", iter.toString());
		assertEquals("two", iter.next());
		assertEquals("[..., three]", iter.toString());
		assertEquals("three", iter.next());
		assertEquals("[...]", iter.toString());
		assertFalse(iter.hasNext());
	}

	@Test
	public void testHashCodeReturnsItemsRead() {
		var iter = iter("one", "two", "three").iterator();
		assertEquals(0, iter.hashCode());
		assertEquals("one", iter.next());
		assertEquals(1, iter.hashCode());
		assertEquals("two", iter.next());
		assertEquals(2, iter.hashCode());
		assertEquals("three", iter.next());
		assertEquals(3, iter.hashCode());
		assertFalse(iter.hasNext());
	}

	@Test
	public void testEqualsFollowsPosition() {
		var iter1 = IuIterable.iter("one", "two", "three").iterator();
		var iter2 = IuIterable.iter("two", "one", "three").iterator();
		assertNotEquals(iter1, iter2);
		assertNotEquals(iter1.next(), iter2.next());
		assertNotEquals(iter1, iter2);
		assertNotEquals(iter1.next(), iter2.next());
		assertEquals(iter1, iter2);
		assertEquals(iter1.next(), iter2.next());
		assertEquals(iter1, iter2);
		assertFalse(iter1.hasNext());
		assertFalse(iter2.hasNext());
	}

	@Test
	public void testNotEqualsNonFactory() {
		var iter1 = iter("one", "two", "three").iterator();
		var iter2 = List.of("two", "one", "three").iterator();
		assertNotEquals(iter1, iter2);
	}

	@Test
	public void testBasicFactoryToString() {
		var source = List.of("two", "one", "three");
		var iter = of(source::iterator);
		assertEquals(source.toString(), iter.toString());
		assertEquals(source.toString(), iter.iterator().toString());
		assertEquals(source.toString(), print(iter));
		assertEquals(source.toString(), print(iter.iterator()));
	}

	@Test
	public void testEmpty() {
		assertFalse(empty().iterator().hasNext());
	}

	@Test
	public void testPrintSkipBounds() {
		assertThrows(IllegalArgumentException.class, () -> print(null, -1));
	}

	@Test
	public void testRemainderOfSameIsEquals() {
		var iter1 = iter("one", "three").iterator();
		assertTrue(remaindersAreEqual(iter1, iter1));
	}

	@Test
	public void testMismatchByFirstItem() {
		var i1 = iter("one");
		var i2 = iter("two");
		assertFalse(remaindersAreEqual(i1.iterator(), i2.iterator()));
		assertFalse(remaindersAreEqual(i2.iterator(), i1.iterator()));
	}

	@Test
	public void testMismatchByLaterItem() {
		var i1 = iter("one");
		var i2 = iter("one", "two");
		assertFalse(remaindersAreEqual(i1.iterator(), i2.iterator()));
		assertFalse(remaindersAreEqual(i2.iterator(), i1.iterator()));
	}

	@Test
	public void testTailsMatch() {
		var i1 = iter("one", "three");
		var i2 = iter("two", "one", "three");
		var iter2 = i2.iterator();
		iter2.next();
		assertTrue(remaindersAreEqual(i1.iterator(), iter2));
	}

	@Test
	public void testNullAndEmptyArrays() {
		assertSame(iter((Object[]) null), empty());
		assertSame(iter(new Object[0]), empty());
	}

	@Test
	public void testIterOutOfBounds() {
		assertThrows(IndexOutOfBoundsException.class, () -> iter((Object[]) null, -1));
		assertThrows(IndexOutOfBoundsException.class, () -> iter((Object[]) null, 1));
		assertThrows(IndexOutOfBoundsException.class, () -> iter(new Object[] { "one" }, 2));
	}

	@Test
	public void testNoSuchElementAfterArray() {
		assertThrows(NoSuchElementException.class, () -> iter().iterator().next());
		assertThrows(NoSuchElementException.class, () -> {
			var iter = iter("one").iterator();
			iter.next();
			iter.next();
		});
	}

	@Test
	public void testCat() {
		assertSame(empty(), cat());
		var iter = iter();
		assertSame(iter, cat(iter));
		assertEquals("[one, two, three]", cat(iter("one"), iter("two", "three")).toString());
	}

	@Test
	public void testNoSuchElementAfterCat() {
		assertThrows(NoSuchElementException.class, () -> {
			var iter = cat(iter("one"), iter("two", "three")).iterator();
			iter.next();
			iter.next();
			iter.next();
			iter.next();
		});
	}

	@Test
	public void testMap() {
		var map = new LinkedHashMap<>();
		map.put("one", 1);
		map.put("two", 2);
		assertEquals("[1, 2]", map(map.keySet(), map::get).toString());
	}

	@Test
	public void testFilter() {
		var set = Set.of("three", "one");
		assertEquals("[one, three]", filter(iter("one", "two", "three"), set::contains).toString());
	}

	@Test
	public void testSelect() {
		var set = Set.of("two");
		assertEquals("two", select(iter("one", "two", "three"), set::contains));
		assertThrows(NoSuchElementException.class, () -> select(iter("four", "five"), set::contains));
	}

	@Test
	public void testNoSuchElementFromFilter() {
		assertThrows(NoSuchElementException.class,
				() -> filter(iter("one", "two", "three"), a -> false).iterator().next());
	}

	@Test
	public void testStream() {
		assertEquals("[one, two, three]", of(Stream.of("one", "two", "three")::iterator).toString());
		assertArrayEquals(new String[] { "one", "two", "three" },
				IuIterable.stream(IuIterable.iter("one", "two", "three")).toArray(String[]::new));
	}

}
