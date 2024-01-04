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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuObjectTest {

	@Test
	public void testPlatformType() {
		assertFalse(IuObject.isPlatformName(""));
		assertTrue(IuObject.isPlatformName("com.sun."));
		assertTrue(IuObject.isPlatformName("java."));
		assertTrue(IuObject.isPlatformName("javax."));
		assertTrue(IuObject.isPlatformName("jakarta."));
		assertTrue(IuObject.isPlatformName("jdk."));
		assertTrue(IuObject.isPlatformName("netscape.javascript."));
		assertTrue(IuObject.isPlatformName("org.ietf.jgss."));
		assertTrue(IuObject.isPlatformName("org.w3c.dom."));
		assertTrue(IuObject.isPlatformName("org.xml.sax."));
	}

	@Test
	public void testCompareNullCheckForSameIsZero() {
		assertEquals(0, IuObject.compareNullCheck("", ""));
	}

	@Test
	public void testNullLessThanNonNull() {
		assertEquals(-1, IuObject.compareNullCheck(null, ""));
		assertEquals(1, IuObject.compareNullCheck("", null));
	}

	@Test
	public void testMismatchNonNullsCheckToNull() {
		assertNull(IuObject.compareNullCheck("a", "b"));
	}

	@Test
	public void testCompareToMatchesNonNullNullCheck() {
		assertEquals(IuObject.compareNullCheck("", ""), IuObject.compareTo("", ""));
	}

	@Test
	public void testCompareUsesNaturalOrder() {
		assertEquals(-1, IuObject.compareTo("a", "b"));
		assertEquals(1, IuObject.compareTo("b", "a"));
	}

	@Test
	public void testCompareUsesSystemOrderFoNonComparable() {
		var a = new Object();
		var b = new Object();
		assertEquals(-IuObject.compareTo(a, b), IuObject.compareTo(b, a));
	}

	@Test
	public void testCompareUsesSystemOrderForClassMismatch() {
		var a = "";
		var b = new Object();
		assertEquals(-IuObject.compareTo(a, b), IuObject.compareTo(b, a));
	}

	@Test
	public void testHashCode() {
		assertEquals(31, IuObject.hashCode((Object) null));
		assertEquals(40390, IuObject.hashCode(new boolean[] { true, false }));
		assertEquals(1055, IuObject.hashCode(new byte[] { 2, 1 }));
		assertEquals(4097, IuObject.hashCode(new char[] { 'a', 'b' }));
		assertEquals(-2075230271, IuObject.hashCode(new double[] { 0.1, 2.3, 4.5 }));
		assertEquals(-1142946842, IuObject.hashCode(new float[] { 0.1f, 2.3f }));
		assertEquals(1089, IuObject.hashCode(new int[] { 3, 4 }));
		assertEquals(1155, IuObject.hashCode(new long[] { 5, 8 }));
		assertEquals(1232, IuObject.hashCode(new short[] { 8, -8 }));

		int result = 31 + (31 * (31 * String.class.hashCode() + 31 + "foo".hashCode()) + 31 + "bar".hashCode());
		assertEquals(result, IuObject.hashCode((Object) new String[] { "foo", "bar" }));

		assertEquals(31, IuObject.hashCode(""));
	}

	@Test
	public void testTypeCheckSameSame() {
		assertTrue(IuObject.typeCheck(new ArrayList<>(), new ArrayList<>()));
	}

	@Test
	public void testTypeCheckImplMismatch() {
		assertFalse(IuObject.typeCheck(new ArrayList<>(), new HashSet<>()));
	}

	@Test
	public void testTypeCheckCommonSuperClass() {
		assertTrue(IuObject.typeCheck(new ArrayList<>(), new HashSet<>(), Collection.class));
	}

	@Test
	public void testTypeCheckUnommonSuperClass() {
		assertFalse(IuObject.typeCheck(new ArrayList<>(), new HashSet<>(), List.class));
		assertFalse(IuObject.typeCheck(new HashSet<>(), new ArrayList<>(), List.class));
	}

	@Test
	public void testTypeCheckNulls() {
		assertTrue(IuObject.typeCheck(null, null));
		assertFalse(IuObject.typeCheck(new Object(), null));
		assertFalse(IuObject.typeCheck(null, new Object()));
	}

	@Test
	public void testEqualsNulls() {
		assertTrue(IuObject.equals(null, null));
		assertFalse(IuObject.equals(new Object(), null));
		assertFalse(IuObject.equals(null, new Object()));
	}

	@Test
	public void testSetsAreEquals() {
		var set1 = Set.of("a", "b", "c");
		var set2 = new HashSet<>(set1);
		assertTrue(IuObject.equals(set1, set2));
		assertTrue(IuObject.equals(set2, set1));
		set2.add("d");
		assertFalse(IuObject.equals(set1, set2));
		assertFalse(IuObject.equals(set2, set1));
		set2.remove("c"); // same size again
		assertFalse(IuObject.equals(set1, set2));
		assertFalse(IuObject.equals(set2, set1));
	}

	@Test
	public void testSortedSetsNotEquivalentToSet() {
		var set1 = Set.of("foo", "bar", "baz");
		var set2 = new TreeSet<>(set1);
		assertEquals(IuObject.equals(set1, set2), IuObject.equals(set2, set1));
	}

	@Test
	public void testIterablesAreEquals() {
		var list = List.of("a", "b", "c");
		var queue = new ArrayDeque<>(list);
		assertTrue(IuObject.equals(list, queue));
		assertTrue(IuObject.equals(queue, list));
		queue.add("d");
		assertFalse(IuObject.equals(list, queue));
		assertFalse(IuObject.equals(queue, list));
		queue.poll(); // same size again
		assertFalse(IuObject.equals(list, queue));
		assertFalse(IuObject.equals(queue, list));
	}

	@Test
	public void testMapsAreEquals() {
		var map1 = Map.of("foo", 1, "bar", 2, "baz", 3);
		var map2 = new HashMap<>(map1);
		assertTrue(IuObject.equals(map1, map2));
		assertTrue(IuObject.equals(map2, map1));
		map2.put("bar", 4);
		assertFalse(IuObject.equals(map1, map2));
		assertFalse(IuObject.equals(map2, map1));
		map2.put("bar", 2);
		map2.put("zzz", 4);
		assertFalse(IuObject.equals(map1, map2));
		assertFalse(IuObject.equals(map2, map1));
	}

	@Test
	public void testDifferentClassesNotEquals() {
		var map1 = Map.of("foo", 1, "bar", 2, "baz", 3);
		var set1 = Set.of("foo", "bar", "baz");
		assertFalse(IuObject.equals(set1, map1));
		assertFalse(IuObject.equals(map1, set1));
	}

	@Test
	public void testPrimitiveArrayEqualses() {
		assertTrue(IuObject.equals(new boolean[] { true, false }, new boolean[] { true, false }));
		assertTrue(IuObject.equals(new byte[] { 2, 1 }, new byte[] { 2, 1 }));
		assertTrue(IuObject.equals(new char[] { 'a', 'b' }, new char[] { 'a', 'b' }));
		assertTrue(IuObject.equals(new double[] { 0.1, 2.3, 4.5 }, new double[] { 0.1, 2.3, 4.5 }));
		assertTrue(IuObject.equals(new float[] { 0.1f, 2.3f }, new float[] { 0.1f, 2.3f }));
		assertTrue(IuObject.equals(new int[] { 3, 4 }, new int[] { 3, 4 }));
		assertTrue(IuObject.equals(new long[] { 5, 8 }, new long[] { 5, 8 }));
		assertTrue(IuObject.equals(new short[] { 8, -8 }, new short[] { 8, -8 }));
	}

	@Test
	public void testObjectArrayEqualses() {
		assertTrue(IuObject.equals(new String[] { "foo", "bar" }, new String[] { "foo", "bar" }));
		assertFalse(IuObject.equals(new String[] { "foo", "bar", "baz" }, new String[] { "foo", "bar" }));
		assertFalse(IuObject.equals(new String[] { "foo", "bar" }, new String[] { "foo", "bar", "baz" }));
		assertFalse(IuObject.equals(new String[] { "foo", "baz" }, new String[] { "foo", "bar" }));
		assertFalse(IuObject.equals(new String[] { "foo", "bar" }, new String[] { "foo", "baz" }));
	}

	@Test
	public void testStringsAreEqual() {
		assertTrue(IuObject.equals("", ""));
	}

	@Test
	public void testObjectsAreNotEqual() {
		assertFalse(IuObject.equals(new Object(), new Object()));
	}

	@Test
	public void testWaitFor() throws InterruptedException, TimeoutException {
		final var timeout = Duration.ofMillis(100L);
		final var thirdOfTimeout = timeout.dividedBy(3L);
		final var expires = Instant.now().plus(timeout);
		class Box {
			volatile boolean done;
			Throwable thrown;
		}
		final var box = new Box();
		new Thread(() -> {
			try {
				Thread.sleep(thirdOfTimeout.toMillis(), thirdOfTimeout.toNanosPart() % 1000000);
				synchronized (box) {
					box.notifyAll();
				}

				Thread.sleep(thirdOfTimeout.toMillis(), thirdOfTimeout.toNanosPart() % 1000000);
				synchronized (box) {
					box.done = true;
					box.notifyAll();
				}

				Thread.sleep(timeout.toMillis(), timeout.toNanosPart() % 1000000);
			} catch (Throwable e) {
				box.thrown = e;
			}
		}).start();
		IuObject.waitFor(box, () -> box.done, timeout);
		assertNull(box.thrown);
		assertThrows(TimeoutException.class, () -> IuObject.waitFor(box, () -> !box.done, expires));
	}

	@Test
	public void testWaitForWithFactory() throws InterruptedException, TimeoutException {
		IuObject.waitFor(this, () -> true, Duration.ZERO, TimeoutException::new);
		final var timeout = new TimeoutException();
		assertSame(timeout, assertThrows(TimeoutException.class,
				() -> IuObject.waitFor(this, () -> false, Duration.ZERO, () -> timeout)));
	}

}
