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
package edu.iu.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import edu.iu.GenericTypes;
import edu.iu.IuIterable;
import jakarta.json.JsonValue;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;

@SuppressWarnings({ "javadoc", "unused" })
public class IuJsonArrayAdapterTest {

	private List<String> list;
	private Set<String> set;
	private SortedSet<String> sortedSet;
	private Iterable<String> iterable;
	private Stream<String> stream;
	private Iterator<String> iterator;
	private Enumeration<String> enumeration;
	private Map<String, String> map;
	private List<Item> items;
	private Iterable<Item> iterableOfItems;

	public static class Item {
		public String value;
	}

	public static class Lenient implements JsonbDeserializer<Iterable<?>> {
		@Override
		public Iterable<?> deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
			if (Event.START_ARRAY.equals(parser.currentEvent()))
				return ctx.deserialize(rtType, parser);
			else
				return (Iterable<?>) IuJsonArrayAdapter.of(rtType)
						.collect(IuIterable.iter((Object) ctx.deserialize(GenericTypes.item(rtType), parser)));
		}
	}

	private static Type field(String name) {
		try {
			return IuJsonArrayAdapterTest.class.getDeclaredField(name).getGenericType();
		} catch (NoSuchFieldException e) {
			throw new IllegalArgumentException(e);
		}
	}

	private static <T> List<T> items(IuJsonArrayAdapter<?, T> adapter, Object value) {
		@SuppressWarnings("unchecked")
		final var items = ((IuJsonArrayAdapter<Object, T>) adapter).iterator(value);
		final List<T> list = new ArrayList<>();
		items.forEachRemaining(list::add);
		return list;
	}

	@Test
	public void testCollectionsAreNew() {
		final IuJsonArrayAdapter<List<String>, String> lists = IuJsonArrayAdapter.of(field("list"));
		final var list = lists.collect(List.of("a"));
		assertInstanceOf(ArrayList.class, list);
		assertEquals(List.of("a"), list);
		assertTrue(list.add("b"));
		assertEquals(List.of("a", "b"), items(lists, list));

		final IuJsonArrayAdapter<Set<String>, String> sets = IuJsonArrayAdapter.of(field("set"));
		assertInstanceOf(LinkedHashSet.class, sets.collect(List.of("b", "a")));
		assertEquals(List.of("b", "a"), items(sets, sets.collect(List.of("b", "a"))));

		final IuJsonArrayAdapter<SortedSet<String>, String> sortedSets = IuJsonArrayAdapter.of(field("sortedSet"));
		assertInstanceOf(TreeSet.class, sortedSets.collect(List.of("b", "a")));
		assertEquals(List.of("a", "b"), items(sortedSets, sortedSets.collect(List.of("b", "a"))));
	}

	@Test
	public void testIterableIsItems() {
		final IuJsonArrayAdapter<Iterable<String>, String> iterables = IuJsonArrayAdapter.of(field("iterable"));
		final var items = List.of("a");
		assertSame(items, iterables.collect(items));
		assertEquals(items, items(iterables, items));
	}

	@Test
	public void testArrays() {
		final IuJsonArrayAdapter<String[], String> strings = IuJsonArrayAdapter.of(String[].class);
		final var array = strings.collect(List.of("a", "b"));
		assertArrayEquals(new String[] { "a", "b" }, array);
		assertEquals(List.of("a", "b"), items(strings, array));

		final IuJsonArrayAdapter<Object, Object> ints = IuJsonArrayAdapter.of(int[].class);
		final var primitives = ints.collect(List.of(1, 2));
		assertArrayEquals(new int[] { 1, 2 }, (int[]) primitives);
		assertEquals(List.of(1, 2), items(ints, primitives));
	}

	@Test
	public void testSingleUse() {
		final IuJsonArrayAdapter<Stream<String>, String> streams = IuJsonArrayAdapter.of(field("stream"));
		assertEquals(List.of("a"), streams.collect(List.of("a")).toList());
		final var stream = Stream.of("a", "b");
		assertEquals(List.of("a", "b"), items(streams, stream));
		assertThrows(IllegalStateException.class, () -> stream.iterator());

		final IuJsonArrayAdapter<Iterator<String>, String> iterators = IuJsonArrayAdapter.of(field("iterator"));
		final var iterator = iterators.collect(List.of("a"));
		assertEquals(List.of("a"), items(iterators, iterator));
		assertFalse(iterator.hasNext());

		final IuJsonArrayAdapter<Enumeration<String>, String> enumerations = IuJsonArrayAdapter
				.of(field("enumeration"));
		final var enumeration = enumerations.collect(List.of("a"));
		assertEquals(List.of("a"), items(enumerations, enumeration));
		assertFalse(enumeration.hasMoreElements());
		assertEquals(List.of("b"), items(enumerations, Collections.enumeration(List.of("b"))));
	}

	@Test
	public void testSingleton() {
		final var item = IuJsonAdapter.of(String.class).fromJson(IuJson.string("a"));
		final IuJsonArrayAdapter<List<String>, String> lists = IuJsonArrayAdapter.of(field("list"));
		assertEquals(List.of("a"), lists.collect(List.of(item)));
		assertEquals(List.of("a"), IuIterable.stream(lists.collect(IuIterable.iter(item))).toList());
	}

	@Test
	public void testNotAnArray() {
		assertEquals("doesn't convert as a JSON array: java.lang.String",
				assertThrows(IllegalArgumentException.class, () -> IuJsonArrayAdapter.of(String.class)).getMessage());
		assertEquals("doesn't convert as a JSON array: java.util.Map<java.lang.String, java.lang.String>",
				assertThrows(IllegalArgumentException.class, () -> IuJsonArrayAdapter.of(field("map"))).getMessage());
		assertThrows(UnsupportedOperationException.class, () -> IuJsonArrayAdapter.of(Thread.class));
	}

	@Test
	public void testNoBuiltInOutsideACall() {
		assertThrows(UnsupportedOperationException.class, () -> IuJsonArrayAdapter.of(field("items")));
	}

	@Test
	public void testFollowsTheCall() throws Exception {
		try (final var jsonb = JsonbBuilder.create(new JsonbConfig().withDeserializers(new Lenient()))) {
			final Iterable<Item> one = jsonb.fromJson("{\"value\":\"a\"}", field("iterableOfItems"));
			assertEquals(List.of("a"), IuIterable.stream(one).map(i -> i.value).toList());

			final Iterable<Item> two = jsonb.fromJson("[{\"value\":\"a\"},{\"value\":\"b\"}]",
					field("iterableOfItems"));
			assertEquals(List.of("a", "b"), IuIterable.stream(two).map(i -> i.value).toList());
		}
	}

	@Test
	public void testExplicitConversions() {
		final IuJsonArrayAdapter<List<Item>, Item> items = IuJsonArrayAdapter.of(field("items"),
				t -> IuJsonAdapter.from(v -> new Item(), v -> JsonValue.NULL));
		final var list = items.fromJson(IuJson.array().add(IuJson.object()).build());
		assertEquals(1, list.size());
		assertInstanceOf(Item.class, list.get(0));

		assertThrows(IllegalArgumentException.class, () -> IuJsonArrayAdapter.of(String.class, null));
	}

}
