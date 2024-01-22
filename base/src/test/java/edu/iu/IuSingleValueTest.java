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
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuSingleValueTest {

	private <T> void assertSingleValue(T value, Class<T> type) throws Exception {
		assertTrue(IuSingleValue.isSingleValue(type));
		assertSame(value, IuSingleValue.convert(value, type));

		final var serialized = IuSingleValue.serialize(value);
		assertEquals(serialized, IuSingleValue.convert(value, CharSequence.class));
		assertEquals(value, IuSingleValue.deserialize(serialized, type));
		assertEquals(value, IuSingleValue.deserialize(new StringReader(serialized.toString()), type));
		assertEquals(value, IuSingleValue.deserialize(new ByteArrayInputStream(serialized.toString().getBytes("UTF-8")), type));

		assertEquals(value, IuSingleValue.convert(serialized, type));
		assertEquals(value, IuSingleValue.convert(new StringReader(serialized.toString()), type));
		assertEquals(value, IuSingleValue.convert(new ByteArrayInputStream(serialized.toString().getBytes("UTF-8")), type));

		final var serializationWriter = new StringWriter();
		IuSingleValue.serialize(value, serializationWriter);
		assertEquals(serialized, serializationWriter.toString());

		final var serializationStream = new ByteArrayOutputStream();
		IuSingleValue.serialize(value, serializationStream);
		assertEquals(serialized, new String(serializationStream.toByteArray(), "UTF-8"));
	}

	private void assertAutobox(Class<?> nonPrimitive, Object defaultValue) throws Exception {
		Class<?> primitive = (Class<?>) nonPrimitive.getField("TYPE").get(null);
		assertSame(nonPrimitive, IuSingleValue.getNonPrimitiveClass(primitive));
		assertEquals(defaultValue, IuSingleValue.getDefaultValue(primitive));
		assertNull(IuSingleValue.getDefaultValue(nonPrimitive));
	}

	@Test
	public void testNonPrimitive() {
		assertSame(Object.class, IuSingleValue.getNonPrimitiveClass(Object.class));
		assertNull(IuSingleValue.getDefaultValue(Object.class));
	}

	@Test
	public void testBooleanIsPrimitive() throws Exception {
		assertAutobox(Boolean.class, false);
	}

	@Test
	public void testCharacterIsPrimitive() throws Exception {
		assertAutobox(Character.class, '\0');
	}

	@Test
	public void testIntegerIsPrimitive() throws Exception {
		assertAutobox(Integer.class, 0);
	}

	@Test
	public void testLongIsPrimitive() throws Exception {
		assertAutobox(Long.class, 0L);
	}

	@Test
	public void testShortIsPrimitive() throws Exception {
		assertAutobox(Short.class, (short) 0);
	}

	@Test
	public void testByteIsPrimitive() throws Exception {
		assertAutobox(Byte.class, (byte) 0);
	}

	@Test
	public void testDoubleIsPrimitive() throws Exception {
		assertAutobox(Double.class, 0.0);
	}

	@Test
	public void testFloatIsPrimitive() throws Exception {
		assertAutobox(Float.class, 0.0f);
	}

	@Test
	public void testVoidIsPrimitive() throws Exception {
		assertAutobox(Void.class, null);
	}

	@Test
	public void testCharSequence() throws Exception {
		assertSingleValue("", CharSequence.class);
	}

	@Test
	public void testString() throws Exception {
		assertSingleValue("", String.class);
	}

	@Test
	public void testReaderAsSerializeSource() {
		assertEquals("", IuSingleValue.serialize(new StringReader("")));
	}

	@Test
	public void testReaderAsConvertSource() {
		assertEquals("", IuSingleValue.convert(new StringReader(""), CharSequence.class));
		assertEquals("", IuSingleValue.convert(new StringReader(""), String.class));
	}

	@Test
	public void testUnusableReaderAsSourceValue() {
		final var cause = new IOException();
		final var e = assertThrows(IllegalArgumentException.class, () -> IuSingleValue.convert(new Reader() {
			@Override
			public int read(char[] cbuf, int off, int len) throws IOException {
				throw cause;
			}

			@Override
			public void close() throws IOException {
			}
		}, CharSequence.class));
		assertEquals("Unreadable source value", e.getMessage());
		assertSame(cause, e.getCause());
	}

	@Test
	public void testReaderAsDeserializeTarget() throws Exception {
		assertEquals("", IuStream.read(IuSingleValue.deserialize("", Reader.class)));
		assertEquals("", IuStream.read(IuSingleValue.deserialize(new StringReader(""), Reader.class)));
		assertEquals("",
				IuStream.read(IuSingleValue.deserialize(new ByteArrayInputStream("".getBytes()), Reader.class)));
	}

	@Test
	public void testReaderAsConversionTarget() throws Exception {
		assertEquals("", IuStream.read(IuSingleValue.convert("", Reader.class)));
	}

	@Test
	public void testWriterAsDeserializeTarget() throws Exception {
		assertEquals("", ((DetachedWriter) IuSingleValue.deserialize("", Writer.class)).text());
		assertEquals("", ((DetachedWriter) IuSingleValue.deserialize(new StringReader(""), Writer.class)).text());
		assertEquals("",
				((DetachedWriter) IuSingleValue.deserialize(new ByteArrayInputStream("".getBytes()), Writer.class))
						.text());
	}

	@Test
	public void testWriterAsConversionTarget() throws Exception {
		assertEquals("", ((DetachedWriter) IuSingleValue.convert("", Writer.class)).text());
		assertEquals("", ((DetachedWriter) IuSingleValue.convert(new StringReader(""), Writer.class)).text());
	}

	@Test
	public void testStringWriterAsSerializeSource() {
		assertEquals("", IuSingleValue.serialize(new StringWriter()));
	}

	@Test
	public void testDetachedWriterAsSerializeSource() {
		assertEquals("", IuSingleValue.serialize(new DetachedWriter()));
	}

	@Test
	public void testStringWriterAsConvertSource() {
		assertEquals("", IuSingleValue.convert(new StringWriter(), CharSequence.class));
	}

	@Test
	public void testDetachedWriterAsConvertSource() {
		assertEquals("", IuSingleValue.convert(new DetachedWriter(), CharSequence.class));
	}

	@Test
	public void testNullSerializesToNull() {
		assertNull(IuSingleValue.serialize(null));
	}

	@Test
	public void testNullCannotSerializesToStream() {
		assertThrows(NullPointerException.class, () -> IuSingleValue.serialize(null, new DetachedWriter()));
		assertThrows(NullPointerException.class, () -> IuSingleValue.serialize(null, new DetachedOutputStream()));
	}

	@Test
	public void testNullDeserializesToNull() {
		assertNull(IuSingleValue.deserialize((CharSequence) null, Number.class));
	}

	@Test
	public void testNullConvertsToNull() {
		assertNull(IuSingleValue.convert((CharSequence) null, Number.class));
	}

	@Test
	public void testEmptyStringDeserializesToNullVoid() throws Exception {
		assertNull(IuSingleValue.deserialize(new StringReader(""), Void.class));
		assertNull(IuSingleValue.deserialize(new StringReader(""), void.class));
	}

	@Test
	public void testCharacter() throws Exception {
		assertSingleValue('a', Character.class);
		assertSingleValue('a', char.class);
		assertEquals("Invalid character, expected length == 1",
				assertThrows(IllegalArgumentException.class, () -> IuSingleValue.deserialize("", char.class))
						.getMessage());
	}

	@Test
	public void testBoolean() throws Exception {
		assertSingleValue(true, Boolean.class);
		assertSingleValue(true, boolean.class);
		assertTrue(IuSingleValue.deserialize("1", boolean.class));
		assertTrue(IuSingleValue.deserialize("-1", boolean.class));
		assertTrue(IuSingleValue.deserialize("true", boolean.class));
		assertTrue(IuSingleValue.deserialize("Y", boolean.class));
		assertTrue(IuSingleValue.deserialize("TrUe", boolean.class));
		assertTrue(IuSingleValue.deserialize("y", boolean.class));
		assertFalse(IuSingleValue.deserialize("0", boolean.class));
		assertFalse(IuSingleValue.deserialize("false", boolean.class));
		assertFalse(IuSingleValue.deserialize("N", boolean.class));
	}

	@Test
	public void testInteger() throws Exception {
		final var i = ThreadLocalRandom.current().nextInt(Short.MAX_VALUE + 1, Integer.MAX_VALUE);
		assertSingleValue(i, Integer.class);
		assertSingleValue(i, int.class);
		assertThrows(NumberFormatException.class, () -> IuSingleValue.deserialize(Integer.MAX_VALUE + "0", int.class));
		assertEquals((double) i, IuSingleValue.convert(i, Double.class));
		assertEquals((long) i, IuSingleValue.convert(i, Long.class));
		assertThrows(ArithmeticException.class, () -> IuSingleValue.convert(i, Short.class));
		assertThrows(ArithmeticException.class, () -> IuSingleValue.convert(i, Byte.class));
		assertEquals(new BigDecimal(i), IuSingleValue.convert(i, BigDecimal.class));
		assertEquals(new BigInteger(Integer.toString(i)), IuSingleValue.convert(i, BigInteger.class));
	}

	@Test
	public void testLong() throws Exception {
		final var l = ThreadLocalRandom.current().nextLong(Integer.MAX_VALUE + 1, Long.MAX_VALUE);
		assertSingleValue(l, Long.class);
		assertSingleValue(l, long.class);
		assertThrows(NumberFormatException.class, () -> IuSingleValue.deserialize(Long.MAX_VALUE + "0", long.class));
		assertThrows(ArithmeticException.class, () -> IuSingleValue.convert(l, Integer.class));
		assertThrows(ArithmeticException.class, () -> IuSingleValue.convert(l, Short.class));
		assertThrows(ArithmeticException.class, () -> IuSingleValue.convert(l, Byte.class));
		assertEquals(new BigDecimal(l), IuSingleValue.convert(l, BigDecimal.class));
		assertEquals(new BigInteger(Long.toString(l)), IuSingleValue.convert(l, BigInteger.class));
	}

	@Test
	public void testShort() throws Exception {
		final var s = (short) ThreadLocalRandom.current().nextInt(Byte.MAX_VALUE + 1, Short.MAX_VALUE);
		assertSingleValue(s, Short.class);
		assertSingleValue(s, short.class);
		assertThrows(NumberFormatException.class, () -> IuSingleValue.deserialize(Short.MAX_VALUE + "0", short.class));
		assertEquals((double) s, IuSingleValue.convert(s, Double.class));
		assertEquals((float) s, IuSingleValue.convert(s, Float.class));
		assertEquals((long) s, IuSingleValue.convert(s, Long.class));
		assertEquals((int) s, IuSingleValue.convert(s, Integer.class));
		assertThrows(ArithmeticException.class, () -> IuSingleValue.convert(s, Byte.class));
		assertEquals(new BigDecimal(s), IuSingleValue.convert(s, BigDecimal.class));
		assertEquals(new BigInteger(Integer.toString(s)), IuSingleValue.convert(s, BigInteger.class));
	}

	@Test
	public void testByte() throws Exception {
		final var b = (byte) ThreadLocalRandom.current().nextInt();
		assertSingleValue(b, Byte.class);
		assertSingleValue(b, byte.class);
		assertThrows(NumberFormatException.class, () -> IuSingleValue.deserialize(Byte.MAX_VALUE + "0", byte.class));
		assertEquals((double) b, IuSingleValue.convert(b, Double.class));
		assertEquals((float) b, IuSingleValue.convert(b, Float.class));
		assertEquals((long) b, IuSingleValue.convert(b, Long.class));
		assertEquals((int) b, IuSingleValue.convert(b, Integer.class));
		assertEquals((short) b, IuSingleValue.convert(b, Short.class));
		assertEquals(new BigDecimal(b), IuSingleValue.convert(b, BigDecimal.class));
		assertEquals(new BigInteger(Integer.toString(b)), IuSingleValue.convert(b, BigInteger.class));
	}

	@Test
	public void testDouble() throws Exception {
		final double d;
		{
			double a;
			while ((a = (double) ThreadLocalRandom.current().nextDouble()) % 1.0 == 0.0 || a == ((double) ((float) a)))
				;
			d = a;
		}
		assertSingleValue(d, Double.class);
		assertSingleValue(d, double.class);
		assertThrows(ArithmeticException.class, () -> IuSingleValue.convert(d, Float.class));
		assertThrows(ArithmeticException.class, () -> IuSingleValue.convert(d, Long.class));
		assertThrows(ArithmeticException.class, () -> IuSingleValue.convert(d, Integer.class));
		assertThrows(ArithmeticException.class, () -> IuSingleValue.convert(d, Short.class));
		assertThrows(ArithmeticException.class, () -> IuSingleValue.convert(d, Byte.class));
		assertEquals(new BigDecimal(d), IuSingleValue.convert(d, BigDecimal.class));
		assertThrows(ArithmeticException.class, () -> IuSingleValue.convert(d, BigInteger.class));
	}

	@Test
	public void testFloat() throws Exception {
		final float f;
		{
			float a;
			while ((a = (float) ThreadLocalRandom.current().nextFloat()) % 1.0f == 0.0f)
				;
			f = a;
		}
		assertSingleValue(f, Float.class);
		assertSingleValue(f, float.class);
		assertEquals((double) f, IuSingleValue.convert(f, Double.class));
		assertThrows(ArithmeticException.class, () -> IuSingleValue.convert(f, Long.class));
		assertThrows(ArithmeticException.class, () -> IuSingleValue.convert(f, Integer.class));
		assertThrows(ArithmeticException.class, () -> IuSingleValue.convert(f, Short.class));
		assertThrows(ArithmeticException.class, () -> IuSingleValue.convert(f, Byte.class));
		assertEquals(new BigDecimal(f), IuSingleValue.convert(f, BigDecimal.class));
		assertThrows(ArithmeticException.class, () -> IuSingleValue.convert(f, BigInteger.class));
	}

	@Test
	public void testBigDecimalDouble() throws Exception {
		final BigDecimal bd;
		{
			double a;
			while ((a = (double) ThreadLocalRandom.current().nextDouble()) % 1.0 == 0.0 || a == ((double) ((float) a)))
				;
			bd = new BigDecimal(a);
		}
		assertSingleValue(bd, BigDecimal.class);
		assertEquals(bd.doubleValue(), IuSingleValue.convert(bd, Double.class));
		assertThrows(ArithmeticException.class, () -> IuSingleValue.convert(bd, Float.class));
		assertThrows(ArithmeticException.class, () -> IuSingleValue.convert(bd, Long.class));
		assertThrows(ArithmeticException.class, () -> IuSingleValue.convert(bd, Integer.class));
		assertThrows(ArithmeticException.class, () -> IuSingleValue.convert(bd, Short.class));
		assertThrows(ArithmeticException.class, () -> IuSingleValue.convert(bd, Byte.class));
		assertThrows(ArithmeticException.class, () -> IuSingleValue.convert(bd, BigInteger.class));
	}

	@Test
	public void testBigDecimalByte() throws Exception {
		final var bi = new BigDecimal(ThreadLocalRandom.current().nextInt(Byte.MAX_VALUE));
		assertSingleValue(bi, BigDecimal.class);
		assertEquals(bi.doubleValue(), IuSingleValue.convert(bi, Double.class));
		assertEquals(bi.floatValue(), IuSingleValue.convert(bi, Float.class));
		assertEquals(bi.longValue(), IuSingleValue.convert(bi, Long.class));
		assertEquals(bi.intValue(), IuSingleValue.convert(bi, Integer.class));
		assertEquals(bi.shortValue(), IuSingleValue.convert(bi, Short.class));
		assertEquals(bi.byteValue(), IuSingleValue.convert(bi, Byte.class));
		assertEquals(bi.toBigInteger(), IuSingleValue.convert(bi, BigInteger.class));
	}

	@Test
	public void testBigIntegerDoubleLong() throws Exception {
		final BigInteger bi = new BigInteger(
				Long.toString(ThreadLocalRandom.current().nextLong(Integer.MAX_VALUE + 1, Long.MAX_VALUE))
						+ Long.toString(ThreadLocalRandom.current().nextLong(Integer.MAX_VALUE + 1, Long.MAX_VALUE)));
		assertSingleValue(bi, BigInteger.class);
		assertThrows(ArithmeticException.class, () -> IuSingleValue.convert(bi, Double.class));
		assertThrows(ArithmeticException.class, () -> IuSingleValue.convert(bi, Float.class));
		assertThrows(ArithmeticException.class, () -> IuSingleValue.convert(bi, Long.class));
		assertThrows(ArithmeticException.class, () -> IuSingleValue.convert(bi, Integer.class));
		assertThrows(ArithmeticException.class, () -> IuSingleValue.convert(bi, Short.class));
		assertThrows(ArithmeticException.class, () -> IuSingleValue.convert(bi, Byte.class));
		assertEquals(new BigDecimal(bi), IuSingleValue.convert(bi, BigDecimal.class));
	}

	@Test
	public void testBigIntegerByte() throws Exception {
		final var bi = new BigInteger(Integer.toString(ThreadLocalRandom.current().nextInt(Byte.MAX_VALUE)));
		assertSingleValue(bi, BigInteger.class);
		assertEquals(bi.doubleValue(), IuSingleValue.convert(bi, Double.class));
		assertEquals(bi.floatValue(), IuSingleValue.convert(bi, Float.class));
		assertEquals(bi.longValue(), IuSingleValue.convert(bi, Long.class));
		assertEquals(bi.intValue(), IuSingleValue.convert(bi, Integer.class));
		assertEquals(bi.shortValue(), IuSingleValue.convert(bi, Short.class));
		assertEquals(bi.byteValue(), IuSingleValue.convert(bi, Byte.class));
		assertEquals(new BigDecimal(bi), IuSingleValue.convert(bi, BigDecimal.class));
	}

	@Test
	public void testDoesntSupportAllNumbers() {
		final var n = mock(Number.class);
		assertThrows(IllegalArgumentException.class, () -> IuSingleValue.convert(n, BigDecimal.class));
		assertThrows(IllegalArgumentException.class, () -> IuSingleValue.convert(0, AtomicInteger.class));
	}

	@Test
	public void testVoid() {
		assertNull(IuSingleValue.convert(null, void.class));
		assertThrows(IllegalArgumentException.class, () -> IuSingleValue.convert("", Void.class));
	}

	@Test
	public void testStringBuilder() throws Exception {
		assertSingleValue(new StringBuilder(IdGenerator.generateId()), StringBuilder.class);
	}
	
}
