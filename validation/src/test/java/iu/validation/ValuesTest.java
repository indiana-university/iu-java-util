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
package iu.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.chrono.JapaneseDate;
import java.util.ArrayDeque;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class ValuesTest {

	/** Declares {@code now()}, but as an instance method. */
	public static class InstanceNow implements Comparable<InstanceNow> {
		public InstanceNow now() {
			return this;
		}

		@Override
		public int compareTo(InstanceNow o) {
			return 0;
		}
	}

	/** Declares a static {@code now()} that does not return its own type. */
	public static class ForeignNow implements Comparable<ForeignNow> {
		public static String now() {
			return "";
		}

		@Override
		public int compareTo(ForeignNow o) {
			return 0;
		}
	}

	/** Declares a usable static {@code now()}, but is not {@link Comparable}. */
	public static class UncomparableNow {
		public static UncomparableNow now() {
			return new UncomparableNow();
		}
	}

	@Test
	public void testSizeOfEachSupportedShape() {
		assertEquals(3, Values.size("abc"));
		assertEquals(2, Values.size(List.of("a", "b")));
		assertEquals(1, Values.size(Map.of("k", "v")));
		assertEquals(4, Values.size(new int[4]));
		assertEquals(2, Values.size(new String[] { "a", "b" }));
	}

	@Test
	public void testSizeCountsAnIterableThatIsNotACollection() {
		// the DTO interfaces this module was written for expose Iterable, not Collection
		final Iterable<String> iterable = () -> new ArrayDeque<>(List.of("a", "b", "c")).iterator();
		assertEquals(3, Values.size(iterable));
		assertEquals(0, Values.size((Iterable<String>) () -> Set.<String>of().iterator()));
	}

	@Test
	public void testSizeRejectsAnUnsizedValue() {
		final var e = assertThrows(UnsupportedOperationException.class, () -> Values.size(42));
		assertEquals("java.lang.Integer is not a sized value", e.getMessage());
	}

	@Test
	public void testText() {
		assertEquals("abc", Values.text("abc"));
		assertEquals("abc", Values.text(new StringBuilder("abc")).toString());

		final var e = assertThrows(UnsupportedOperationException.class, () -> Values.text(42));
		assertEquals("java.lang.Integer is not text", e.getMessage());
	}

	@Test
	public void testBool() {
		assertTrue(Values.bool(Boolean.TRUE));
		assertFalse(Values.bool(Boolean.FALSE));

		final var e = assertThrows(UnsupportedOperationException.class, () -> Values.bool("true"));
		assertEquals("java.lang.String is not a boolean", e.getMessage());
	}

	@Test
	public void testNumberOfEachSupportedShape() {
		final var one = new BigDecimal("1");
		assertEquals(one, Values.number(one));
		assertEquals(0, Values.number(BigInteger.ONE).compareTo(one));
		assertEquals(0, Values.number(1).compareTo(one));
		assertEquals(0, Values.number(1L).compareTo(one));
		assertEquals(0, Values.number((short) 1).compareTo(one));
		assertEquals(0, Values.number((byte) 1).compareTo(one));
		assertEquals(0, Values.number("1.50").compareTo(new BigDecimal("1.5")));
	}

	@Test
	public void testNumberConvertsBinaryFloatingPointByShortestRoundTrip() {
		// BigDecimal.valueOf(double), not new BigDecimal(double): 0.1d compares equal
		// to the decimal 0.1 rather than to its exact binary expansion
		assertEquals(0, Values.number(0.1d).compareTo(new BigDecimal("0.1")));
		assertEquals(0, Values.number(0.5f).compareTo(new BigDecimal("0.5")));
	}

	@Test
	public void testNumberRejectsANonNumber() {
		final var e = assertThrows(UnsupportedOperationException.class, () -> Values.number(List.of()));
		assertTrue(e.getMessage().endsWith(" is not a number"), e::getMessage);
	}

	@Test
	public void testNumberRejectsTextThatIsNotANumeral() {
		assertThrows(NumberFormatException.class, () -> Values.number("not a number"));
	}

	@Test
	public void testCompareToNowForDateAndCalendar() {
		assertTrue(Values.compareToNow(new Date(0L)) < 0);
		assertTrue(Values.compareToNow(new Date(System.currentTimeMillis() + 3600_000L)) > 0);

		final Calendar past = new GregorianCalendar(1999, Calendar.JANUARY, 1);
		assertTrue(Values.compareToNow(past) < 0);
	}

	@Test
	public void testCompareToNowResolvesThePresentThroughStaticNow() {
		assertTrue(Values.compareToNow(Instant.EPOCH) < 0);
		assertTrue(Values.compareToNow(Instant.now().plusSeconds(3600L)) > 0);
		assertTrue(Values.compareToNow(LocalDate.of(1999, 1, 1)) < 0);
		assertTrue(Values.compareToNow(Year.of(1999)) < 0);
		assertTrue(Values.compareToNow(JapaneseDate.of(1999, 1, 1)) < 0);
	}

	@Test
	public void testCompareToNowRejectsATypeWithNoNowMethod() {
		// String is Comparable, so this isolates the missing now() alone
		final var e = assertThrows(UnsupportedOperationException.class, () -> Values.compareToNow("1999-01-01"));
		assertEquals("java.lang.String is not a temporal value", e.getMessage());
	}

	@Test
	public void testCompareToNowRejectsANonStaticNowMethod() {
		assertThrows(UnsupportedOperationException.class, () -> Values.compareToNow(new InstanceNow()));
	}

	@Test
	public void testCompareToNowRejectsANowMethodReturningAnotherType() {
		assertThrows(UnsupportedOperationException.class, () -> Values.compareToNow(new ForeignNow()));
	}

	@Test
	public void testCompareToNowRejectsAnUncomparableValue() {
		assertThrows(UnsupportedOperationException.class, () -> Values.compareToNow(UncomparableNow.now()));
	}

}
