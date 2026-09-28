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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import edu.iu.client.IuJson;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.annotation.JsonbSubtype;
import jakarta.json.bind.annotation.JsonbTypeInfo;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.stream.JsonParser;

@SuppressWarnings("javadoc")
public class IuJsonbPolymorphismTest {

	@JsonbTypeInfo(key = "@animal", value = { @JsonbSubtype(alias = "dog", type = Dog.class),
			@JsonbSubtype(alias = "cat", type = Cat.class) })
	public interface Animal {
		String getName();
	}

	@JsonbTypeInfo(key = "@dog", value = @JsonbSubtype(alias = "lab", type = Labrador.class))
	public static class Dog implements Animal {
		private String name;
		public boolean barks;

		@Override
		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	public static class Labrador extends Dog {
		public String color;
	}

	public static class Cat implements Animal {
		private String name;
		public int lives;

		@Override
		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	public static class Rat implements Animal {
		@Override
		public String getName() {
			return "rat";
		}
	}

	public static class Zoo {
		public Animal star;
		public List<Animal> animals;
	}

	public static Labrador labrador() {
		final var labrador = new Labrador();
		labrador.setName("rex");
		labrador.barks = true;
		labrador.color = "yellow";
		return labrador;
	}

	public static final String LABRADOR = "{\"@animal\":\"dog\",\"@dog\":\"lab\",\"barks\":true,\"color\":\"yellow\","
			+ "\"name\":\"rex\"}";

	private static IuJsonb jsonb() {
		return IuJsonbTest.jsonb(new JsonbConfig());
	}

	/**
	 * Reads JSON as a type, streaming and from the tree.
	 */
	@SuppressWarnings("unchecked")
	private static <T> List<T> read(IuJsonb jsonb, String json, Type type) {
		return List.of(jsonb.fromJson(json, type), (T) jsonb.adapt(type).fromJson(IuJson.parse(json)));
	}

	@Test
	public void testWrite() {
		final var jsonb = jsonb();
		final var dog = new Dog();
		dog.setName("fido");
		assertEquals("{\"@animal\":\"dog\",\"barks\":false,\"name\":\"fido\"}", jsonb.toJson(dog));
		assertEquals(LABRADOR, jsonb.toJson(labrador()));
		assertEquals(LABRADOR, jsonb.adapt(Labrador.class).toJson(labrador()).toString());

		// an unlisted subtype has no alias
		assertEquals("{\"name\":\"rat\"}", jsonb.toJson(new Rat()));

		// declared by the supertype, a subtype writes its own properties
		final var zoo = new Zoo();
		zoo.star = labrador();
		zoo.animals = List.of(labrador());
		final var json = "{\"animals\":[" + LABRADOR + "],\"star\":" + LABRADOR + "}";
		assertEquals(json, jsonb.toJson(zoo));
		assertEquals(json, jsonb.adapt(Zoo.class).toJson(zoo).toString());
	}

	@Test
	public void testRead() {
		final var jsonb = jsonb();
		for (final Animal animal : IuJsonbPolymorphismTest.<Animal>read(jsonb, LABRADOR, Animal.class)) {
			final var labrador = assertInstanceOf(Labrador.class, animal);
			assertEquals("rex", labrador.getName());
			assertTrue(labrador.barks);
			assertEquals("yellow", labrador.color);
		}

		// declared by a subtype, the nearest type information applies
		for (final Dog dog : IuJsonbPolymorphismTest.<Dog>read(jsonb, LABRADOR, Dog.class))
			assertInstanceOf(Labrador.class, dog);
		for (final Labrador labrador : IuJsonbPolymorphismTest.<Labrador>read(jsonb, LABRADOR, Labrador.class))
			assertEquals("yellow", labrador.color);

		final var cat = "{\"@animal\":\"cat\",\"lives\":9,\"name\":\"tom\"}";
		for (final Animal animal : IuJsonbPolymorphismTest.<Animal>read(jsonb, cat, Animal.class))
			assertEquals(9, assertInstanceOf(Cat.class, animal).lives);

		// the key not first reads through first
		final var late = "{\"name\":\"tom\",\"@animal\":\"cat\",\"lives\":9}";
		for (final Animal animal : IuJsonbPolymorphismTest.<Animal>read(jsonb, late, Animal.class))
			assertEquals(9, assertInstanceOf(Cat.class, animal).lives);

		// no key reads as the declared type
		for (final Animal animal : IuJsonbPolymorphismTest.<Animal>read(jsonb, "{\"name\":\"any\"}", Animal.class))
			assertEquals("any", animal.getName());
		assertEquals("empty", ((Function<Animal, String>) a -> a.getName() == null ? "empty" : a.getName())
				.apply(jsonb.fromJson("{}", Animal.class)));

		final var zoo = jsonb.fromJson("{\"animals\":[" + LABRADOR + "," + cat + "],\"star\":" + cat + "}",
				Zoo.class);
		assertInstanceOf(Labrador.class, zoo.animals.get(0));
		assertInstanceOf(Cat.class, zoo.animals.get(1));
		assertInstanceOf(Cat.class, zoo.star);
	}

	@Test
	public void testInvalidAliases() {
		final var jsonb = jsonb();
		for (final var json : new String[] { "{\"@animal\":\"cow\"}", "{\"name\":\"x\",\"@animal\":\"cow\"}" }) {
			assertTrue(assertThrows(JsonbException.class, () -> jsonb.fromJson(json, Animal.class)).getMessage()
					.contains("unknown alias cow for @animal of " + Animal.class.getName()), json);
			assertThrows(JsonbException.class, () -> jsonb.adapt(Animal.class).fromJson(IuJson.parse(json)));
		}

		final var number = "{\"@animal\":1}";
		assertTrue(assertThrows(JsonbException.class, () -> jsonb.fromJson(number, Animal.class)).getMessage()
				.contains("expected an alias for @animal, found VALUE_NUMBER"));
		assertTrue(assertThrows(JsonbException.class, () -> jsonb.adapt(Animal.class).fromJson(IuJson.parse(number)))
				.getMessage().contains("expected an alias for @animal, found NUMBER"));
	}

	@JsonbTypeInfo(key = "@k", value = { @JsonbSubtype(alias = "a", type = A.class),
			@JsonbSubtype(alias = "b", type = B.class) })
	public interface Base {
	}

	public interface Sub extends Base {
	}

	public static class A implements Sub {
	}

	public static class B implements Base {
	}

	@Test
	public void testAliasMustNameASubtypeOfTheDeclaredType() {
		assertTrue(assertThrows(JsonbException.class, () -> jsonb().fromJson("{\"@k\":\"b\"}", Sub.class))
				.getMessage().contains("alias b names " + B.class.getName() + ", which isn't a " + Sub.class.getName()));
		assertInstanceOf(A.class, jsonb().fromJson("{\"@k\":\"a\"}", Sub.class));
	}

	@JsonbTypeInfo(key = "@x", value = @JsonbSubtype(alias = "s", type = String.class))
	public static class NotASubtype {
	}

	@JsonbTypeInfo(key = "@x", value = { @JsonbSubtype(alias = "s", type = TwiceAliased.class),
			@JsonbSubtype(alias = "s", type = TwiceAliased.class) })
	public static class TwiceAliased {
	}

	@JsonbTypeInfo(key = "@animal", value = @JsonbSubtype(alias = "d", type = KeyTwice.class))
	public static class KeyTwice extends Dog {
	}

	@JsonbTypeInfo(key = "@other")
	public interface Other {
	}

	public static class Merged extends Dog implements Other {
	}

	@JsonbTypeInfo(key = "kind", value = @JsonbSubtype(alias = "c", type = Clash.class))
	public static class Clash {
		public String kind;
	}

	@Test
	public void testInvalidDeclarations() {
		final var jsonb = jsonb();
		assertTrue(assertThrows(JsonbException.class, () -> jsonb.toJson(new NotASubtype())).getMessage()
				.contains("subtype java.lang.String of alias s isn't a " + NotASubtype.class.getName()));
		assertTrue(assertThrows(JsonbException.class, () -> jsonb.toJson(new TwiceAliased())).getMessage()
				.contains("alias s declared twice by " + TwiceAliased.class.getName()));
		assertTrue(assertThrows(JsonbException.class, () -> jsonb.toJson(new KeyTwice())).getMessage()
				.contains("type information key @animal declared twice for " + KeyTwice.class.getName()));
		assertTrue(assertThrows(JsonbException.class, () -> jsonb.toJson(new Merged())).getMessage()
				.contains(Merged.class.getName() + " inherits type information from both "));
		assertTrue(assertThrows(JsonbException.class, () -> jsonb.toJson(new Clash())).getMessage()
				.contains("property kind of " + Clash.class.getName()
						+ " conflicts with the type information key of the same name"));
		assertThrows(JsonbException.class, () -> jsonb.fromJson("{\"kind\":\"c\",\"x\":1}", Clash.class));
	}

	@JsonbTypeInfo(key = "@self", value = @JsonbSubtype(alias = "self", type = Self.class))
	public static class Self {
		public int n;
	}

	@JsonbTypeInfo(key = "@shape", value = @JsonbSubtype(alias = "round", type = Round.class))
	public interface Shape {
	}

	public interface Round extends Shape {
		int getRadius();
	}

	public static class CatDeserializer implements JsonbDeserializer<Cat> {
		@Override
		public Cat deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
			final var cat = new Cat();
			cat.lives = parser.getObject().getInt("lives") + 1;
			return cat;
		}
	}

	public static class Mixed {
		public int count = 1;
		public Object[] items = new String[] { "a" };
	}

	@Test
	public void testTypesWithoutTypeInformation() {
		// a primitive or array value whose runtime type differs from its declared
		// type converts as declared
		assertEquals("{\"count\":1,\"items\":[\"a\"]}", jsonb().toJson(new Mixed()));
	}

	@Test
	public void testSubtypesReadFromTheTree() {
		final var jsonb = jsonb();

		// the type itself, listed as its own subtype
		final var self = new Self();
		self.n = 1;
		assertEquals("{\"@self\":\"self\",\"n\":1}", jsonb.toJson(self));
		for (final Self read : IuJsonbPolymorphismTest.<Self>read(jsonb, "{\"@self\":\"self\",\"n\":1}", Self.class))
			assertEquals(1, read.n);

		// an interface subtype
		for (final Shape shape : IuJsonbPolymorphismTest.<Shape>read(jsonb, "{\"@shape\":\"round\",\"radius\":2}",
				Shape.class))
			assertEquals(2, assertInstanceOf(Round.class, shape).getRadius());

		// a subtype with a component of its own sees the whole object
		final var withComponent = IuJsonbTest.jsonb(new JsonbConfig().withDeserializers(new CatDeserializer()));
		for (final Animal animal : IuJsonbPolymorphismTest.<Animal>read(withComponent,
				"{\"@animal\":\"cat\",\"lives\":9}", Animal.class))
			assertEquals(10, assertInstanceOf(Cat.class, animal).lives);
	}

}
