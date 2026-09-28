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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonSerializationOptions;
import iu.client.BindingMetadata;
import iu.client.jsonb.nillable.PackageNillableBean;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.annotation.JsonbNillable;
import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.bind.annotation.JsonbTransient;
import jakarta.json.bind.config.BinaryDataStrategy;

@SuppressWarnings({ "javadoc", "deprecation" })
public class JsonbMetadataTest {

	public static class Members {
		public String plain;
		@JsonbProperty("named")
		public String named;
		@JsonbProperty(nillable = true)
		public String nillableProperty;
		@JsonbProperty
		public String unnamed;
		@JsonbTransient
		public String transientOnly;
		@Deprecated
		public String otherAnnotation;
		@JsonbTransient
		@JsonbProperty("x")
		public String transientNamed;
		@JsonbNillable(false)
		public String notNillable;
	}

	@JsonbNillable
	public static class NillableType {
		public String value;
	}

	private static java.lang.reflect.Field field(String name) {
		return edu.iu.IuException.unchecked(() -> Members.class.getField(name));
	}

	@Test
	public void testPresent() {
		assertSame(JsonbMetadata.INSTANCE, BindingMetadata.get());
	}

	@Test
	public void testName() {
		final var metadata = JsonbMetadata.INSTANCE;
		assertNull(metadata.name(field("plain")));
		assertEquals("named", metadata.name(field("named")));
		assertNull(metadata.name(field("unnamed")));
	}

	@Test
	public void testTransientAndCustomized() {
		final var metadata = JsonbMetadata.INSTANCE;
		assertTrue(metadata.isTransient(field("transientOnly")));
		assertFalse(metadata.isTransient(field("plain")));
		assertFalse(metadata.isCustomized(field("plain")));
		assertFalse(metadata.isCustomized(field("transientOnly")));
		assertFalse(metadata.isCustomized(field("otherAnnotation")));
		assertTrue(metadata.isCustomized(field("transientNamed")));
		assertTrue(metadata.isCustomized(field("named")));
	}

	@Test
	public void testNillable() {
		final var metadata = JsonbMetadata.INSTANCE;
		assertNull(metadata.nillable(field("plain")));
		assertNull(metadata.nillable(field("named")));
		assertTrue(metadata.nillable(field("nillableProperty")));
		assertFalse(metadata.nillable(field("notNillable")));
		assertTrue(metadata.nillable(NillableType.class));
		assertTrue(metadata.nillable(PackageNillableBean.class));
		assertNull(metadata.nillable(Members.class));
	}

	@Test
	public void testTransientConflict() {
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig());
		final var error = assertThrows(JsonbException.class, () -> jsonb.toJson(new Members()));
		assertTrue(error.getCause() instanceof IllegalStateException, error::toString);
		assertTrue(error.getCause().getMessage().startsWith("property transientNamed of " + Members.class.getName()
				+ " is transient, so can't be customized by "), error.getCause()::getMessage);
	}

	public static class NillableBean {
		@JsonbNillable
		public String nil;
		@JsonbNillable(false)
		public String omitted;
		public String plain;
	}

	@Test
	public void testNillableProperties() {
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig());
		assertEquals("{\"nil\":null}", jsonb.toJson(new NillableBean()));
		assertEquals("{\"nil\":null}", jsonb.adapt(NillableBean.class).toJson(new NillableBean()).toString());
		assertEquals("{\"value\":null}", jsonb.toJson(new NillableType()));
		assertEquals("{\"value\":null}", jsonb.toJson(new PackageNillableBean()));

		// declared false wins over the call's null option
		final var nulls = IuJsonbTest.jsonb(new JsonbConfig().withNullValues(true));
		assertEquals("{\"nil\":null,\"plain\":null}", nulls.toJson(new NillableBean()));
	}

	public static class OptionalBean {
		public Optional<String> value = Optional.empty();
	}

	enum Letter {
		A, B;

		@Override
		public String toString() {
			return name().toLowerCase();
		}
	}

	public static class Letters {
		public Letter letter = Letter.A;
		public Map<Letter, String> map = Map.of(Letter.B, "b");
	}

	@Test
	public void testLegacyOptions() {
		final Supplier<IuJsonSerializationOptions> legacy = () -> IuJsonSerializationOptions.LEGACY;
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig().setProperty(IuJsonAdapter.SERIALIZATION_OPTIONS, legacy));
		assertEquals("{\"value\":null}", jsonb.toJson(new OptionalBean()));
		assertEquals("{\"letter\":\"a\",\"map\":{\"b\":\"b\"}}", jsonb.toJson(new Letters()));
		assertEquals("{\"letter\":\"a\",\"map\":{\"b\":\"b\"}}", jsonb.adapt(Letters.class).toJson(new Letters()).toString());

		final var standard = IuJsonbTest.jsonb(new JsonbConfig());
		assertEquals("{}", standard.toJson(new OptionalBean()));
		assertEquals("{\"letter\":\"A\",\"map\":{\"B\":\"b\"}}", standard.toJson(new Letters()));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testKeyAdapterOutsideCall() {
		final Supplier<IuJsonSerializationOptions> legacy = () -> IuJsonSerializationOptions.LEGACY;
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig().setProperty(IuJsonAdapter.SERIALIZATION_OPTIONS, legacy));
		final var keys = (edu.iu.client.IuJsonAdapter<Letter>) jsonb.keyAdapter(Letter.class);
		assertEquals(IuJson.string("b"), keys.toJson(Letter.B));
		assertSame(Letter.B, keys.fromJson(IuJson.string("B")));
	}

	@Test
	public void testBinaryConflict() {
		final Supplier<IuJsonSerializationOptions> legacy = () -> IuJsonSerializationOptions.LEGACY;
		final var base64 = IuJsonbTest.jsonb(new JsonbConfig().withBinaryDataStrategy(BinaryDataStrategy.BASE_64)
				.setProperty(IuJsonAdapter.SERIALIZATION_OPTIONS, legacy));
		assertEquals("\"+/8B\"", base64.toJson(new byte[] { (byte) 0xfb, (byte) 0xff, 1 }));

		final Supplier<IuJsonSerializationOptions> standard = () -> IuJsonSerializationOptions.DEFAULT;
		final var conflict = IuJsonbTest.jsonb(new JsonbConfig().withBinaryDataStrategy(BinaryDataStrategy.BASE_64)
				.setProperty(IuJsonAdapter.SERIALIZATION_OPTIONS, standard));
		final var error = assertThrows(JsonbException.class, () -> conflict.toJson(new byte[0]));
		assertEquals(jakarta.json.bind.JsonbConfig.BINARY_DATA_STRATEGY + " BASE_64 conflicts with "
				+ IuJsonAdapter.SERIALIZATION_OPTIONS + " binary data strategy BYTE", error.getMessage());
	}

}
