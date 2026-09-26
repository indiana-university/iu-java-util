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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.util.List;

import org.junit.jupiter.api.Test;

import edu.iu.client.IuJson;
import jakarta.json.JsonValue;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.json.bind.serializer.SerializationContext;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;

@SuppressWarnings("javadoc")
public class IuJsonbValueAdapterTest {

	public static class Money {
		public final String amount;

		public Money(String amount) {
			this.amount = amount;
		}
	}

	public static class MoneyAdapter implements JsonbAdapter<Money, String> {
		@Override
		public String adaptToJson(Money obj) {
			return obj == null ? "none" : "$" + obj.amount;
		}

		@Override
		public Money adaptFromJson(String obj) {
			return new Money(obj == null ? "none" : obj.substring(1));
		}
	}

	public static class BrokenAdapter implements JsonbAdapter<Money, String> {
		@Override
		public String adaptToJson(Money obj) throws Exception {
			throw new Exception("to");
		}

		@Override
		public Money adaptFromJson(String obj) throws Exception {
			throw new Exception("from");
		}
	}

	public static class Child {
		public String value;

		public Child() {
		}

		Child(String value) {
			this.value = value;
		}
	}

	public static class Wallet {
		public Money money;
		public Child child;
		public List<Child> children;
		public Child[] array;
		public int count;
	}

	static IuJsonb jsonb(JsonbConfig config) {
		return IuJsonbTest.jsonb(config);
	}

	static String tree(IuJsonb jsonb, Object value) {
		return jsonb.adapt(value.getClass()).toJson(value).toString();
	}

	static <T> T fromTree(IuJsonb jsonb, Class<T> type, String json) {
		return type.cast(jsonb.adapt(type).fromJson(IuJson.parse(json)));
	}

	@Test
	public void testAdapter() {
		final var jsonb = jsonb(new JsonbConfig().withAdapters(new MoneyAdapter()));
		final var wallet = new Wallet();
		wallet.money = new Money("5");
		final var json = "{\"count\":0,\"money\":\"$5\"}";
		assertEquals(json, jsonb.toJson(wallet));
		assertEquals(json, tree(jsonb, wallet));
		assertEquals("5", jsonb.fromJson(json, Wallet.class).money.amount);
		assertEquals("5", fromTree(jsonb, Wallet.class, json).money.amount);
		// the adapter sees null too, and an undefined value
		assertEquals("none", jsonb.fromJson("{\"money\":null}", Wallet.class).money.amount);
		assertEquals("none", fromTree(jsonb, Wallet.class, "{\"money\":null}").money.amount);
		assertEquals("none", ((Money) jsonb.adapt(Money.class).fromJson(null)).amount);
	}

	@Test
	public void testAdapterFailures() {
		final var jsonb = jsonb(new JsonbConfig().withAdapters(new BrokenAdapter()));
		final var wallet = new Wallet();
		wallet.money = new Money("5");
		final var toJson = assertThrows(JsonbException.class, () -> jsonb.toJson(wallet));
		assertTrue(toJson.getMessage().contains("adapter failed to convert"), toJson.getMessage());
		assertEquals("to", toJson.getCause().getCause().getMessage());
		final var fromJson = assertThrows(JsonbException.class,
				() -> jsonb.fromJson("{\"money\":\"$5\"}", Wallet.class));
		assertEquals("from", fromJson.getCause().getCause().getMessage());
	}

	/**
	 * Writes a child as its value, or delegates for itself when asked to.
	 */
	public static class ChildSerializer implements JsonbSerializer<Child> {
		@Override
		public void serialize(Child obj, JsonGenerator generator, SerializationContext ctx) {
			if (obj == null)
				generator.write("none");
			else if ("delegate".equals(obj.value))
				ctx.serialize(obj, generator);
			else
				generator.write("child " + obj.value);
		}
	}

	@Test
	public void testSerializer() {
		final var jsonb = jsonb(new JsonbConfig().withSerializers(new ChildSerializer()));
		final var wallet = new Wallet();
		wallet.child = new Child("x");
		wallet.children = List.of(new Child("y"), new Child("delegate"));
		final var json = "{\"child\":\"child x\",\"children\":[\"child y\",{\"value\":\"delegate\"}],\"count\":0}";
		assertEquals(json, jsonb.toJson(wallet));
		assertEquals(json, tree(jsonb, wallet));
		assertEquals("{\"value\":\"delegate\"}", jsonb.adapt(Child.class).toJson(new Child("delegate")).toString());

		// the serializer sees null when null properties are included
		final var nulls = jsonb(new JsonbConfig().withSerializers(new ChildSerializer()).withNullValues(true));
		assertTrue(nulls.toJson(new Wallet()).contains("\"child\":\"none\""));
		assertTrue(tree(nulls, new Wallet()).contains("\"child\":\"none\""));
	}

	/**
	 * Delegates to a tree conversion of the same value, which gets the built-in
	 * conversion.
	 */
	public static class TreeDelegatingSerializer implements JsonbSerializer<Child> {
		IuJsonb jsonb;

		@Override
		public void serialize(Child obj, JsonGenerator generator, SerializationContext ctx) {
			generator.write(jsonb.adapt(Child.class).toJson(obj));
		}
	}

	@Test
	public void testSerializerDelegatingToTree() {
		final var serializer = new TreeDelegatingSerializer();
		final var jsonb = jsonb(new JsonbConfig().withSerializers(serializer));
		serializer.jsonb = jsonb;
		final var wallet = new Wallet();
		wallet.child = new Child("x");
		assertEquals("{\"child\":{\"value\":\"x\"},\"count\":0}", jsonb.toJson(wallet));
		assertEquals("{\"child\":{\"value\":\"x\"},\"count\":0}", tree(jsonb, wallet));
	}

	/**
	 * Reads a child from any value; delegates for itself when given an object.
	 */
	public static class ChildDeserializer implements JsonbDeserializer<Child> {
		@Override
		public Child deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
			switch (parser.currentEvent()) {
			case VALUE_NULL:
				return new Child("was null");

			case START_OBJECT: {
				final var child = ctx.deserialize(Child.class, parser);
				child.value = child.value.toUpperCase();
				return child;
			}

			default:
				return new Child("from " + parser.getString());
			}
		}
	}

	@Test
	public void testDeserializer() {
		final var jsonb = jsonb(new JsonbConfig().withDeserializers(new ChildDeserializer()));
		final var json = "{\"child\":\"text\",\"children\":[{\"value\":\"a\"},null],\"array\":[\"b\"]}";
		final var wallet = jsonb.fromJson(json, Wallet.class);
		assertEquals("from text", wallet.child.value);
		assertEquals("A", wallet.children.get(0).value);
		assertEquals("was null", wallet.children.get(1).value);
		assertEquals("from b", wallet.array[0].value);
		assertEquals(0, wallet.count);
	}

	/**
	 * Reads a child in a tree conversion, where only the value is available.
	 */
	public static class ValueDeserializer implements JsonbDeserializer<Child> {
		@Override
		public Child deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
			return new Child("tree " + parser.getValue());
		}
	}

	public interface HasChild {
		Child getChild();
	}

	@Test
	public void testDeserializerInTreeConversion() {
		final var jsonb = jsonb(new JsonbConfig().withDeserializers(new ValueDeserializer()));
		assertEquals("tree \"x\"", fromTree(jsonb, Wallet.class, "{\"child\":\"x\"}").child.value);
		assertEquals("tree null", fromTree(jsonb, Wallet.class, "{\"child\":null}").child.value);
		// an undefined value has no event to give the deserializer
		final var hasChild = (HasChild) jsonb.fromJson("{}", HasChild.class);
		assertNull(hasChild.getChild());
	}

	public static class Node {
		public String name;
		public Node next;
	}

	/**
	 * Delegates for itself, so nested nodes, at later positions, come back here.
	 */
	public static class NodeDeserializer implements JsonbDeserializer<Node> {
		int calls;

		@Override
		public Node deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
			final var call = ++calls;
			final Node node = ctx.deserialize(Node.class, parser);
			node.name = node.name + call;
			return node;
		}
	}

	@Test
	public void testNestedValuesOfTheSameTypeReachTheDeserializer() {
		final var deserializer = new NodeDeserializer();
		final var jsonb = jsonb(new JsonbConfig().withDeserializers(deserializer));
		final var node = jsonb.fromJson("{\"name\":\"a\",\"next\":{\"name\":\"b\"}}", Node.class);
		assertEquals(2, deserializer.calls);
		assertEquals("b2", node.next.name);
		assertEquals("a1", node.name);
	}

	public static class Outer {
		public Child child;
	}

	/**
	 * Reads an outer value through another type's deserializer, at the same
	 * position.
	 */
	public static class OuterDeserializer implements JsonbDeserializer<Outer> {
		@Override
		public Outer deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
			final var outer = new Outer();
			outer.child = ctx.deserialize(Child.class, parser);
			return outer;
		}
	}

	/**
	 * Reads the whole value, then converts part of it as a tree.
	 */
	public static class TreeOuterDeserializer implements JsonbDeserializer<Outer> {
		IuJsonb jsonb;

		@Override
		public Outer deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
			final var outer = new Outer();
			outer.child = (Child) jsonb.adapt(Child.class).fromJson(parser.getObject().get("child"));
			return outer;
		}
	}

	@Test
	public void testDeserializersForOtherTypes() {
		final var jsonb = jsonb(
				new JsonbConfig().withDeserializers(new OuterDeserializer(), new ChildDeserializer()));
		assertEquals("from x", jsonb.fromJson("\"x\"", Outer.class).child.value);

		final var tree = new TreeOuterDeserializer();
		final var jsonb2 = jsonb(new JsonbConfig().withDeserializers(tree, new ValueDeserializer()));
		tree.jsonb = jsonb2;
		assertEquals("tree \"y\"", jsonb2.fromJson("{\"child\":\"y\"}", Outer.class).child.value);
	}

	@Test
	public void testBuiltInTypes() {
		final var jsonb = jsonb(new JsonbConfig());
		final var wallet = jsonb.fromJson("{\"count\":3,\"array\":[{\"value\":\"a\"}]}", Wallet.class);
		assertEquals(3, wallet.count);
		assertEquals("a", wallet.array[0].value);
		assertEquals("{\"array\":[{\"value\":\"a\"}],\"count\":3}", jsonb.toJson(wallet));
	}

	@Test
	public void testWithoutACallInProgress() {
		final var jsonb = jsonb(new JsonbConfig().withAdapters(new MoneyAdapter()));
		final var adapter = jsonb.adapt(Money.class);
		try (final var parser = IuJson.PROVIDER.createParser(new StringReader("\"$7\""))) {
			parser.next();
			assertEquals("7", ((Money) adapter.read(parser)).amount);
		}

		final var writer = new StringWriter();
		try (final var generator = IuJson.PROVIDER.createGenerator(writer)) {
			adapter.write(new Money("8"), generator);
		}
		assertEquals("\"$8\"", writer.toString());
		assertEquals(IuJson.string("$9"), adapter.toJson(new Money("9")));
		assertEquals("9", ((Money) adapter.fromJson(IuJson.string("$9"))).amount);
	}

	@Test
	public void testNullPropertiesWithoutAdapter() {
		final var jsonb = jsonb(new JsonbConfig());
		assertNull(jsonb.adapt(Child.class).nullProperty(null, List.of()));
		assertEquals(JsonValue.NULL, jsonb.adapt(Child.class).toJson(null));
		assertArrayEquals(new Child[0], jsonb.fromJson("[]", Child[].class));
	}

	public static class Animal {
		public String kind = "animal";
	}

	public static class Dog extends Animal {
	}

	public static class Cat extends Animal {
	}

	public static class Pets {
		public Animal animal;
		public Dog dog;
		public int legs;
	}

	/**
	 * Delegates for the type it was asked to read.
	 */
	public static class AsRequested implements JsonbDeserializer<Animal> {
		@Override
		public Animal deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
			final Animal animal = ctx.deserialize(rtType, parser);
			animal.kind = "read " + animal.kind;
			return animal;
		}
	}

	/**
	 * Delegates for its own type, whatever it was asked to read.
	 */
	public static class AsAnimal implements JsonbDeserializer<Animal> {
		@Override
		public Animal deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
			return ctx.deserialize(Animal.class, parser);
		}
	}

	@Test
	public void testDeserializerForASupertype() {
		final var jsonb = jsonb(new JsonbConfig().withDeserializers(new AsRequested()));
		final var pets = jsonb.fromJson("{\"animal\":{\"kind\":\"a\"},\"dog\":{\"kind\":\"d\"}}", Pets.class);
		assertEquals("read a", pets.animal.kind);
		assertInstanceOf(Dog.class, pets.dog);
		assertEquals("read d", pets.dog.kind);
	}

	@Test
	public void testDeserializerMustReadTheDeclaredType() {
		final var jsonb = jsonb(new JsonbConfig().withDeserializers(new AsAnimal()));
		assertEquals("a", jsonb.fromJson("{\"animal\":{\"kind\":\"a\"}}", Pets.class).animal.kind);

		// delegating for its own type gets the built-in conversion, which reads an
		// Animal, not the Dog the property declares
		final var error = assertThrows(JsonbException.class,
				() -> jsonb.fromJson("{\"dog\":{\"kind\":\"d\"}}", Pets.class));
		assertTrue(error.getMessage().startsWith("failed to read Pets.dog (line 1"), error.getMessage());
		assertTrue(error.getMessage().endsWith("deserializer " + AsAnimal.class.getName() + " read "
				+ Animal.class.getName() + ", which is not a " + Dog.class.getName()), error.getMessage());
	}

	/**
	 * Reads each kind of animal back as its own class.
	 */
	public static class AnimalAdapter implements JsonbAdapter<Animal, String> {
		@Override
		public String adaptToJson(Animal obj) {
			return obj == null ? null : obj.getClass().getSimpleName().toLowerCase() + ":" + obj.kind;
		}

		@Override
		public Animal adaptFromJson(String obj) {
			if (obj == null)
				return null;
			final Animal animal = obj.startsWith("dog:") ? new Dog() : obj.startsWith("cat:") ? new Cat() : new Animal();
			animal.kind = obj.substring(obj.indexOf(':') + 1);
			return animal;
		}
	}

	@Test
	public void testAdapterForASupertype() {
		final var jsonb = jsonb(new JsonbConfig().withAdapters(new AnimalAdapter()));
		final var pets = new Pets();
		pets.animal = new Cat();
		pets.dog = new Dog();
		pets.dog.kind = "rex";
		final var json = "{\"animal\":\"cat:animal\",\"dog\":\"dog:rex\",\"legs\":0}";
		assertEquals(json, jsonb.toJson(pets));

		final var read = jsonb.fromJson(json, Pets.class);
		assertInstanceOf(Cat.class, read.animal);
		assertEquals("rex", read.dog.kind);
		assertNull(jsonb.fromJson("{\"dog\":null}", Pets.class).dog);

		final var error = assertThrows(JsonbException.class,
				() -> fromTree(jsonb, Pets.class, "{\"dog\":\"cat:tom\"}"));
		assertEquals("failed to read Pets.dog: adapter for " + Animal.class.getName() + " read "
				+ Cat.class.getName() + ", which is not a " + Dog.class.getName(), error.getMessage());
	}

	public static class Counts implements JsonbAdapter<Integer, String> {
		@Override
		public String adaptToJson(Integer obj) {
			return "n" + obj;
		}

		@Override
		public Integer adaptFromJson(String obj) {
			return Integer.valueOf(obj.substring(1));
		}
	}

	@Test
	public void testAdapterForAPrimitive() {
		final var jsonb = jsonb(new JsonbConfig().withAdapters(new Counts()));
		final var pets = new Pets();
		pets.legs = 4;
		assertEquals("{\"legs\":\"n4\"}", jsonb.toJson(pets));
		assertEquals(4, jsonb.fromJson("{\"legs\":\"n4\"}", Pets.class).legs);
	}

}
