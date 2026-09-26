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
package edu.iu.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IuIterable;
import jakarta.persistence.EntityNotFoundException;

/**
 * Exercises {@link IuEntityDao} over an in-memory stand-in for {@link IuDao}
 * that behaves like a table: a read answers a copy of what was written, and a
 * search answers an unmodifiable list.
 */
@SuppressWarnings("javadoc")
public class IuEntityDaoTest {

	/** What callers read and write. */
	interface Thing {
		String getName();

		default Iterable<String> getTags() {
			return null;
		}
	}

	/** Stores a {@link Thing}, and remembers what it belongs to and when it was first written. */
	static class ThingEntity implements Thing, Consumer<Thing>, Comparable<ThingEntity> {
		String id;
		String parent;
		String name;
		Integer created;
		String boundTo;

		@Override
		public String getName() {
			return name;
		}

		void bind(String parent) {
			if (this.parent != null && !this.parent.equals(parent))
				throw new IllegalArgumentException("belongs to a different parent");
			this.parent = parent;
		}

		@Override
		public void accept(Thing value) {
			name = value.getName();
		}

		@Override
		public int compareTo(ThingEntity o) {
			return name.compareTo(o.name);
		}

		ThingEntity copy() {
			final var copy = new ThingEntity();
			copy.id = id;
			copy.parent = parent;
			copy.name = name;
			copy.created = created;
			return copy;
		}
	}

	/** An entity with no natural order. */
	static class PlainEntity implements Consumer<Thing> {
		@Override
		public void accept(Thing value) {
		}
	}

	/** Entity type used to test input-key discovery with an {@link Object} contract. */
	static class KeyEntity implements Consumer<Object> {
		@Override
		public void accept(Object value) {
		}
	}

	static Thing thing(String name) {
		return () -> name;
	}

	static Thing thing(String name, String... tags) {
		return new Thing() {
			@Override
			public String getName() {
				return name;
			}

			@Override
			public Iterable<String> getTags() {
				return IuIterable.iter(tags);
			}
		};
	}

	/** The table: rows by ID, in the order they were first written. */
	private Map<String, ThingEntity> table;
	private IuDao dao;
	private List<String> afterLoad;
	private List<String> removed;
	private int clock;

	class ThingDao extends IuEntityDao<String, Thing, ThingEntity> {
		@Override
		protected IuDao dao() {
			return dao;
		}

		@Override
		protected Class<String> keyClass() {
			return String.class;
		}

		@Override
		protected Class<ThingEntity> entityClass() {
			return ThingEntity.class;
		}

		@Override
		protected void afterLoad(String key, ThingEntity entity) {
			afterLoad.add(key);
		}

		@Override
		protected void beforeSave(String key, ThingEntity entity, Thing input) {
			if (entity.created == null)
				entity.created = ++clock;
		}

		@Override
		protected void afterSave(String key, ThingEntity entity, Thing input) {
			replace(input.getTags(), IuIterable.iter("old", "kept"), tag -> tag, removed::add);
		}

		@Override
		protected void beforeDelete(String key, ThingEntity entity) {
			removed.add("children of " + key);
		}

		ThingEntity save(String parent, String id, Thing input) {
			return save(id, input, entity -> entity.bind(parent));
		}

		String saveOrCreate(String parent, Thing input) {
			return saveOrCreate(input, entity -> entity.bind(parent));
		}

		Iterable<ThingEntity> searchBound(String parent) {
			return search(Map.of("parent", parent), entity -> entity.boundTo = parent);
		}

		Iterable<ThingEntity> named(String name) {
			return searchWhere("a.name = ?", name);
		}

		ThingEntity loadBound(String id, String context) {
			return load(id, entity -> entity.boundTo = context);
		}
	}

	/** A DAO that retains the no-op lifecycle hooks supplied by the base class. */
	class DefaultThingDao extends IuEntityDao<String, Thing, ThingEntity> {
		@Override
		protected IuDao dao() {
			return dao;
		}

		@Override
		protected Class<String> keyClass() {
			return String.class;
		}

		@Override
		protected Class<ThingEntity> entityClass() {
			return ThingEntity.class;
		}
	}

	/** Exposes input-key discovery for its contract-neutral base implementation. */
	class KeyInputDao extends IuEntityDao<String, Object, KeyEntity> {
		@Override
		protected IuDao dao() {
			return dao;
		}

		@Override
		protected Class<String> keyClass() {
			return String.class;
		}

		@Override
		protected Class<KeyEntity> entityClass() {
			return KeyEntity.class;
		}

		String keyFrom(Object input) {
			return key(input);
		}
	}


	@BeforeEach
	void setUp() {
		table = new TreeMap<>();
		afterLoad = new ArrayList<>();
		removed = new ArrayList<>();

		dao = mock(IuDao.class);
		when(dao.getPrimaryKeyProperties(any())).thenReturn(List.of("id"));
		when(dao.getBeanKey(any())).thenAnswer(a -> Map.of("id", ((ThingEntity) a.getArgument(0)).id));
		when(dao.newBean(any(), anyMap())).thenAnswer(a -> {
			final var entity = new ThingEntity();
			entity.id = (String) ((Map<?, ?>) a.getArgument(1)).get("id");
			return entity;
		});
		when(dao.loadBean(any(), anyMap())).thenAnswer(a -> {
			final var row = table.get(((Map<?, ?>) a.getArgument(1)).get("id"));
			if (row == null)
				throw new EntityNotFoundException();
			return row.copy();
		});
		when(dao.searchBeans(any(), anyMap())).thenAnswer(a -> {
			final var params = (Map<?, ?>) a.getArgument(1);
			final List<ThingEntity> found = new ArrayList<>();
			for (final var row : table.values())
				if (!params.containsKey("parent") || Objects.equals(params.get("parent"), row.parent))
					found.add(row.copy());
			return Collections.unmodifiableList(found);
		});
		when(dao.getBeanQuery(any(), any(Iterable.class), any(Iterable.class))).thenAnswer(a -> {
			final var name = IuIterable.single((Iterable<?>) a.getArgument(2));
			final List<ThingEntity> found = new ArrayList<>();
			for (final var row : table.values())
				if (row.name.equals(name))
					found.add(row.copy());
			@SuppressWarnings("unchecked")
			final SqlQuery<ThingEntity> query = mock(SqlQuery.class);
			when(query.getResults()).thenReturn(Collections.unmodifiableList(found));
			return query;
		});
		org.mockito.Mockito.doAnswer(a -> {
			final ThingEntity entity = a.getArgument(0);
			table.put(entity.id, entity.copy());
			return null;
		}).when(dao).saveBean(any());
		org.mockito.Mockito.doAnswer(a -> {
			final ThingEntity entity = a.getArgument(0);
			if (table.remove(entity.id) == null)
				throw new EntityNotFoundException();
			return null;
		}).when(dao).deleteBean(any());
	}

	@Test
	void testKeepsColumnsTheInputDoesNotCarryAcrossAnUpdate() {
		final var things = new ThingDao();
		final var created = things.save("parent-a", "t1", thing("first"));
		assertEquals(1, created.created);
		assertEquals("parent-a", created.parent);

		final var updated = things.save("t1", thing("renamed"));
		assertEquals("renamed", updated.name);
		assertEquals(1, updated.created);
		assertEquals("parent-a", updated.parent);
		assertEquals(List.of("t1", "t1"), afterLoad);
	}

	@Test
	void testRefusesToMoveARowToAnotherParent() {
		final var things = new ThingDao();
		things.save("parent-a", "t1", thing("first"));

		assertThrows(IllegalArgumentException.class, () -> things.save("parent-b", "t1", thing("moved")));
		assertEquals("parent-a", table.get("t1").parent);
	}

	@Test
	void testGeneratesAKeyForInputThatCarriesNone() {
		final var things = new ThingDao();
		final var created = things.create(thing("generated"));
		assertNotNull(created.id);
		assertEquals(32, created.id.length());

		final var id = things.saveOrCreate("parent-a", thing("child"));
		assertEquals("parent-a", table.get(id).parent);

		// read back, the entity carries its key, so saving it updates in place
		assertEquals(id, things.saveOrCreate("parent-a", things.load(id)));
		assertEquals(2, table.size());
	}

	@Test
	void testRefusesInputWithoutAKey() {
		final var things = new ThingDao();
		assertThrows(IllegalArgumentException.class, () -> things.save(thing("keyless")));
		assertThrows(IllegalArgumentException.class, () -> things.save("t1", (Thing) null));
		assertThrows(IllegalArgumentException.class, () -> things.load(null));
		assertThrows(IllegalArgumentException.class, () -> things.delete(null));
	}

	@Test
	void testSavesAnEntityReadBackUnderItsOwnKey() {
		final var things = new ThingDao();
		things.save("t1", thing("first"));

		final var loaded = things.load("t1");
		loaded.name = "edited";
		assertEquals("edited", things.save(loaded).name);
	}

	@Test
	void testLoadsWithContextTheRowDoesNotStore() {
		final var things = new ThingDao();
		things.save("t1", thing("first"));

		assertEquals("request", things.loadBound("t1", "request").boundTo);
		assertThrows(EntityNotFoundException.class, () -> things.load("missing"));
	}

	@Test
	void testReplacesRelatedRowsWithWhatTheInputNames() {
		final var things = new ThingDao();

		things.save("t1", thing("untagged"));
		assertEquals(List.of(), removed);

		things.save("t1", thing("tagged", "kept", "new"));
		assertEquals(List.of("old"), removed);

		removed.clear();
		things.save("t1", thing("cleared", new String[0]));
		assertEquals(List.of("old", "kept"), removed);
	}

	@Test
	void testDeletesARowAfterItsRelatedRows() {
		final var things = new ThingDao();
		things.save("t1", thing("first"));

		things.delete("t1");
		assertTrue(table.isEmpty());
		assertEquals(List.of("children of t1"), removed);

		// nothing registered: nothing happens, and no hook runs
		things.delete("t1");
		assertEquals(List.of("children of t1"), removed);
	}

	@Test
	void testListsARangeInOrderPopulatingOnlyThatRange() {
		final var things = new ThingDao();
		things.save("p", "t1", thing("charlie"));
		things.save("p", "t2", thing("alpha"));
		things.save("p", "t3", thing("bravo"));
		things.save("q", "t4", thing("delta"));
		afterLoad.clear();

		assertEquals(List.of("alpha", "bravo", "charlie"),
				IuIterable.stream(things.search(Map.of("parent", "p"))).map(ThingEntity::getName).toList());

		afterLoad.clear();
		assertEquals(List.of("bravo"),
				IuIterable.stream(things.search(Map.of("parent", "p"), 1, 2)).map(ThingEntity::getName).toList());
		assertEquals(List.of("t3"), afterLoad);

		assertEquals(List.of("bravo", "charlie", "delta"),
				IuIterable.stream(things.search(Map.of(), 1, null)).map(ThingEntity::getName).toList());
		assertFalse(things.search(Map.of(), 9, null).iterator().hasNext());
		assertFalse(things.search(Map.of(), 2, 1).iterator().hasNext());
		assertEquals(4, IuIterable.stream(things.search(Map.of(), -1, 99)).count());
	}

	@Test
	void testBindsEachResultBeforeItsRelatedRowsAreRead() {
		final var things = new ThingDao();
		things.save("p", "t1", thing("first"));

		assertEquals("p", IuIterable.single(things.searchBound("p")).boundTo);
	}

	@Test
	void testSearchesByCriteria() {
		final var things = new ThingDao();
		things.save("t1", thing("first"));
		things.save("t2", thing("second"));

		assertEquals("t2", IuIterable.single(things.named("second")).id);
	}

	@Test
	void testLeavesResultsInTheOrderTheDatabaseAnsweredWithoutAnOrder() {
		final var things = new ThingDao() {
			@Override
			protected java.util.Comparator<? super ThingEntity> order() {
				return null;
			}
		};
		things.save("t2", thing("alpha"));
		things.save("t1", thing("bravo"));

		assertEquals(List.of("t1", "t2"), IuIterable.stream(things.search(Map.of())).map(e -> e.id).toList());
	}

	@Test
	void testOrdersNaturallyOnlyAComparableEntity() {
		final var plain = new IuEntityDao<String, Thing, PlainEntity>() {
			@Override
			protected IuDao dao() {
				return dao;
			}

			@Override
			protected Class<String> keyClass() {
				return String.class;
			}

			@Override
			protected Class<PlainEntity> entityClass() {
				return PlainEntity.class;
			}
		};
		assertNull(plain.order());
		assertNotNull(new ThingDao().order());
	}

	@Test
	void testGeneratesOnlyStringKeys() {
		final var longs = new IuEntityDao<Long, Thing, ThingEntity>() {
			@Override
			protected IuDao dao() {
				return dao;
			}

			@Override
			protected Class<Long> keyClass() {
				return Long.class;
			}

			@Override
			protected Class<ThingEntity> entityClass() {
				return ThingEntity.class;
			}
		};
		assertThrows(UnsupportedOperationException.class, () -> longs.create(thing("x")));
		assertNull(longs.key(thing("x")));
		org.mockito.Mockito.doReturn(Map.of("id", 7L)).when(dao).getBeanKey(any());
		assertEquals(7L, longs.key(new ThingEntity()));
		assertEquals(Map.of("id", 7L), longs.id(7L));
	}

	@Test
	void testRecognizesKeysSuppliedDirectlyAsInput() {
		final var keys = new KeyInputDao();
		org.mockito.Mockito.doReturn(Map.of("id", "t2")).when(dao).getBeanKey(any());
		assertEquals("t1", keys.keyFrom("t1"));
		assertEquals("t2", keys.keyFrom(new KeyEntity()));
		assertNull(keys.keyFrom(thing("not an entity")));
	}

	@Test
	void testDefaultLifecycleHooksAreNoOps() {
		final var things = new DefaultThingDao();
		things.save("t1", thing("first"));
		assertEquals("first", things.load("t1").name);
		things.delete("t1");
		assertTrue(table.isEmpty());
	}

}
