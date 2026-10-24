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
package iu.client.jsonb;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJson;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.config.BinaryDataStrategy;
import jakarta.json.bind.config.PropertyNamingStrategy;

@SuppressWarnings("javadoc")
public class IuJsonbConfigTest {

	static IuJsonb jsonb(JsonbConfig config) {
		return IuJsonbTest.jsonb(config);
	}

	public static class Data {
		public byte[] bytes;
	}

	static Data data(byte... bytes) {
		final var data = new Data();
		data.bytes = bytes;
		return data;
	}

	// 0xfb 0xff encodes with + and / in base64, - and _ in base64url
	static final byte[] BYTES = { (byte) 0xfb, (byte) 0xff, 1 };

	@Test
	public void testBinaryDefaultsToBytes() {
		final var jsonb = jsonb(new JsonbConfig());
		final var json = "{\"bytes\":[-5,-1,1]}";
		assertEquals(json, jsonb.toJson(data(BYTES)));
		assertEquals(json, jsonb.adapt(Data.class).toJson(data(BYTES)).toString());
		assertArrayEquals(BYTES, jsonb.fromJson(json, Data.class).bytes);
		assertArrayEquals(BYTES, ((Data) jsonb.adapt(Data.class).fromJson(IuJson.parse(json))).bytes);
		assertNull(jsonb.fromJson("{\"bytes\":null}", Data.class).bytes);
		assertNull(((Data) jsonb.adapt(Data.class).fromJson(IuJson.parse("{\"bytes\":null}"))).bytes);

		// null, written and undefined
		final var nulls = jsonb(new JsonbConfig().withNullValues(true));
		assertEquals("{\"bytes\":null}", nulls.toJson(new Data()));
		assertEquals("{\"bytes\":null}", nulls.adapt(Data.class).toJson(new Data()).toString());
		assertNull(jsonb.binary().fromJson(null));
		final var base64 = jsonb(new JsonbConfig().withBinaryDataStrategy(BinaryDataStrategy.BASE_64)
				.withNullValues(true));
		assertEquals("{\"bytes\":null}", base64.toJson(new Data()));
		assertEquals("{\"bytes\":null}", base64.adapt(Data.class).toJson(new Data()).toString());

		for (final var bad : new String[] { "{\"bytes\":\"+/8B\"}", "{\"bytes\":[128]}" }) {
			assertThrows(JsonbException.class, () -> jsonb.fromJson(bad, Data.class), bad);
			assertThrows(JsonbException.class, () -> jsonb.adapt(Data.class).fromJson(IuJson.parse(bad)), bad);
		}
	}

	@Test
	public void testBase64() {
		final var jsonb = jsonb(new JsonbConfig().withBinaryDataStrategy(BinaryDataStrategy.BASE_64));
		final var json = "{\"bytes\":\"+/8B\"}";
		assertEquals(json, jsonb.toJson(data(BYTES)));
		assertEquals(json, jsonb.adapt(Data.class).toJson(data(BYTES)).toString());
		assertArrayEquals(BYTES, jsonb.fromJson(json, Data.class).bytes);
		assertArrayEquals(BYTES, ((Data) jsonb.adapt(Data.class).fromJson(IuJson.parse(json))).bytes);

		// padded when the length calls for it, and read either way
		assertEquals("{\"bytes\":\"AQ==\"}", jsonb.toJson(data((byte) 1)));
		assertArrayEquals(new byte[] { 1 }, jsonb.fromJson("{\"bytes\":\"AQ\"}", Data.class).bytes);

		for (final var bad : new String[] { "{\"bytes\":[1]}", "{\"bytes\":\"*\"}" }) {
			assertThrows(JsonbException.class, () -> jsonb.fromJson(bad, Data.class), bad);
			assertThrows(JsonbException.class, () -> jsonb.adapt(Data.class).fromJson(IuJson.parse(bad)), bad);
		}
	}

	@Test
	public void testBase64Url() {
		final var jsonb = jsonb(new JsonbConfig().withBinaryDataStrategy(BinaryDataStrategy.BASE_64_URL));
		assertEquals("{\"bytes\":\"-_8B\"}", jsonb.toJson(data(BYTES)));
		assertEquals("{\"bytes\":\"AQ==\"}", jsonb.toJson(data((byte) 1)));
		assertArrayEquals(new byte[] { 1 }, jsonb.fromJson("{\"bytes\":\"AQ\"}", Data.class).bytes);
		assertArrayEquals(new byte[] { 1 }, jsonb.fromJson("{\"bytes\":\"AQ==\"}", Data.class).bytes);

		final var unpadded = jsonb(new JsonbConfig().withBinaryDataStrategy(BinaryDataStrategy.BASE_64_URL)
				.setProperty(IuJsonAdapter.BASE64_URL_UNPADDED, true));
		assertEquals("{\"bytes\":\"AQ\"}", unpadded.toJson(data((byte) 1)));
		assertEquals("{\"bytes\":\"AQ\"}", unpadded.adapt(Data.class).toJson(data((byte) 1)).toString());
		assertArrayEquals(new byte[] { 1 }, unpadded.fromJson("{\"bytes\":\"AQ==\"}", Data.class).bytes);

		assertEquals("unsupported binary data strategy HEX; expected BYTE, BASE_64, or BASE_64_URL",
				assertThrows(UnsupportedOperationException.class,
						() -> jsonb(new JsonbConfig().withBinaryDataStrategy("HEX"))).getMessage());

		// the unpadded option is a property of its own, not a strategy
		assertEquals("unsupported binary data strategy " + IuJsonAdapter.BASE64_URL_UNPADDED
				+ "; expected BYTE, BASE_64, or BASE_64_URL; " + IuJsonAdapter.BASE64_URL_UNPADDED
				+ " is a property to set true, with BASE_64_URL, to write it unpadded",
				assertThrows(UnsupportedOperationException.class,
						() -> jsonb(new JsonbConfig().withBinaryDataStrategy(IuJsonAdapter.BASE64_URL_UNPADDED)))
						.getMessage());
	}

	public static class Big {
		public BigInteger value;
	}

	/**
	 * Converts a big integer to its bytes, as JOSE key parameters do.
	 */
	public static class BigIntegerBytes implements JsonbAdapter<BigInteger, byte[]> {
		@Override
		public byte[] adaptToJson(BigInteger obj) {
			return obj.toByteArray();
		}

		@Override
		public BigInteger adaptFromJson(byte[] obj) {
			return new BigInteger(obj);
		}
	}

	@Test
	public void testAdapterOutputUsesTheBinaryStrategy() {
		final var jsonb = jsonb(new JsonbConfig().withAdapters(new BigIntegerBytes())
				.withBinaryDataStrategy(BinaryDataStrategy.BASE_64_URL).setProperty(IuJsonAdapter.BASE64_URL_UNPADDED, true));
		final var big = new Big();
		big.value = BigInteger.valueOf(65537);
		assertEquals("{\"value\":\"AQAB\"}", jsonb.toJson(big));
		assertEquals(big.value, jsonb.fromJson("{\"value\":\"AQAB\"}", Big.class).value);
	}

	public static class Text {
		public String text = "été";
	}

	@Test
	public void testEncoding() {
		final var utf16 = jsonb(new JsonbConfig().withEncoding("UTF-16"));
		final var out = new ByteArrayOutputStream();
		utf16.toJson(new Text(), out);
		final var bytes = out.toByteArray();
		assertEquals("{\"text\":\"été\"}", new String(bytes, StandardCharsets.UTF_16));
		assertEquals("été", utf16.fromJson(new ByteArrayInputStream(bytes), Text.class).text);

		// UTF-8, and detected, by default
		final var utf8 = jsonb(new JsonbConfig());
		final var out8 = new ByteArrayOutputStream();
		utf8.toJson(new Text(), out8);
		assertEquals("{\"text\":\"été\"}", new String(out8.toByteArray(), StandardCharsets.UTF_8));
		assertEquals("été", utf8.fromJson(new ByteArrayInputStream(bytes), Text.class).text);
	}

	public enum Level {
		LOW, HIGH {
			@Override
			public String toString() {
				return "high";
			}
		}
	}

	public static class Levels {
		public EnumMap<Level, Integer> byLevel;
		public Map<Level, Integer> map;
	}

	@Test
	public void testEnumsByName() {
		final var jsonb = jsonb(new JsonbConfig());
		final var levels = new Levels();
		levels.byLevel = new EnumMap<>(Map.of(Level.HIGH, 2));
		levels.map = Map.of(Level.HIGH, 3);
		final var json = "{\"byLevel\":{\"HIGH\":2},\"map\":{\"HIGH\":3}}";
		assertEquals(json, jsonb.toJson(levels));
		assertEquals(json, jsonb.adapt(Levels.class).toJson(levels).toString());
		assertEquals(Map.of(Level.HIGH, 2), jsonb.fromJson(json, Levels.class).byLevel);
		assertEquals("\"HIGH\"", jsonb.toJson(Level.HIGH));
		assertEquals(Level.HIGH, jsonb.fromJson("\"HIGH\"", Level.class));
	}

	@Test
	public void testEnumObjectFormByStrategy() {
		final var upper = jsonb(new JsonbConfig().withPropertyNamingStrategy(PropertyNamingStrategy.UPPER_CAMEL_CASE));
		assertEquals(Level.HIGH, upper.fromJson("{\"Name\":\"HIGH\"}", Level.class));
		assertEquals(Level.HIGH, upper.adapt(Level.class).fromJson(IuJson.parse("{\"Name\":\"HIGH\"}")));

		final var insensitive = jsonb(
				new JsonbConfig().withPropertyNamingStrategy(PropertyNamingStrategy.CASE_INSENSITIVE));
		assertEquals(Level.LOW, insensitive.fromJson("{\"x\":1,\"NAME\":\"LOW\"}", Level.class));
		assertEquals(Level.LOW, insensitive.adapt(Level.class).fromJson(IuJson.parse("{\"x\":1,\"NAME\":\"LOW\"}")));
	}

	public interface Named {
		String getFirstName();

		String getLastName();
	}

	@Test
	public void testInterfacesByStrategy() {
		final var dashes = jsonb(
				new JsonbConfig().withPropertyNamingStrategy(PropertyNamingStrategy.LOWER_CASE_WITH_DASHES));
		final var named = dashes.fromJson("{\"first-name\":\"f\"}", Named.class);
		assertEquals("f", named.getFirstName());
		assertNull(named.getLastName());

		final var insensitive = jsonb(
				new JsonbConfig().withPropertyNamingStrategy(PropertyNamingStrategy.CASE_INSENSITIVE));
		final var json = "{\"FIRSTNAME\":\"f\",\"lastName\":\"l\"}";
		for (final var read : new Named[] { insensitive.fromJson(json, Named.class),
				(Named) insensitive.adapt(Named.class).fromJson(IuJson.parse(json)) }) {
			assertEquals("f", read.getFirstName());
			assertEquals("l", read.getLastName());
		}
		assertTrue(IuJson.unwrap(insensitive.fromJson(json, Named.class)).containsKey("FIRSTNAME"));

		// matching no name, in any case
		assertNull(insensitive.fromJson("{\"FIRSTNAME\":\"f\"}", Named.class).getLastName());
	}

}
