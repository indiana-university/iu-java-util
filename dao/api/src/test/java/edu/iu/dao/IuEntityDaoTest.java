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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuIterable;
import jakarta.persistence.EntityNotFoundException;

@SuppressWarnings("unchecked")
public class IuEntityDaoTest {

	private IuDao dao;

	interface Entity extends SaveableEntity<Object>, MergeableEntity<Object> {
	}

	private class EntityDao<K, I, E extends SaveableEntity<I> & MergeableEntity<I>> extends IuEntityDao<K, I, E> {
		@Override
		protected IuDao dao() {
			return dao;
		}
	}

	private class StringConsumerDao extends EntityDao<String, Object, Entity> {
	};

	private class IntegerConsumerDao extends EntityDao<Integer, Object, Entity> {
	};

	private class ObjectConsumerDao extends EntityDao<Object, Object, Entity> {
	};

	@BeforeEach
	void setup() {
		dao = mock(IuDao.class);
	}

	@Test
	void testId() {
		final var id = IdGenerator.generateId();
		final var prop = IdGenerator.generateId();
		when(dao.getPrimaryKeyProperties(Entity.class)).thenReturn(IuIterable.iter(prop));
		final var edao = new StringConsumerDao();
		assertEquals(Map.of(prop, id), edao.id(id));
	}

	@Test
	void testKeyFromEntity() {
		final var entity = mock(Entity.class);
		final var id = IdGenerator.generateId();
		when(dao.getBeanKey(entity)).thenReturn(Map.of(IdGenerator.generateId(), id));
		final var edao = new StringConsumerDao();
		assertEquals(id, edao.key(entity));
		assertEquals(id, edao.key((Object) entity));
	}

	@Test
	void testKeyFromKey() {
		final var id = IdGenerator.generateId();
		final var edao = new StringConsumerDao();
		assertEquals(id, edao.key(id));
	}

	@Test
	void testKeyFromInput() {
		final var edao = new StringConsumerDao();
		assertNull(edao.key(new Object()));
	}

	@Test
	void testGenerateString() {
		final var edao = new StringConsumerDao();
		assertDoesNotThrow(() -> IdGenerator.verifyId(edao.generate(), 0L));
	}

	@Test
	void testGenerateUnsupported() {
		final var edao = new ObjectConsumerDao();
		assertThrows(UnsupportedOperationException.class, edao::generate);
	}

	@Test
	void testNewEntity() {
		final var id = IdGenerator.generateId();
		final var prop = IdGenerator.generateId();
		when(dao.getPrimaryKeyProperties(Entity.class)).thenReturn(IuIterable.iter(prop));
		final var edao = spy(new StringConsumerDao());
		edao.newEntity(id);
		verify(dao).newBean(Entity.class, Map.of(prop, id));
	}

	@Test
	void testNoOrder() {
		final var edao = spy(new StringConsumerDao());
		assertNull(edao.order());
	}

	interface NaturallyOrdered extends Entity, Comparable<NaturallyOrdered> {
	}

	class NaturallyOrderedDao extends EntityDao<String, Object, NaturallyOrdered> {
	}

	@Test
	void testNaturalOrder() {
		final var edao = new NaturallyOrderedDao();
		final var order = edao.order();
		final var a = mock(NaturallyOrdered.class);
		final var b = mock(NaturallyOrdered.class);
		order.compare(a, b);
		verify(a).compareTo(b);
	}

	@Test
	void testLoad() {
		final var id = IdGenerator.generateId();
		final var prop = IdGenerator.generateId();
		final var entity = mock(Entity.class);
		when(dao.getPrimaryKeyProperties(Entity.class)).thenReturn(IuIterable.iter(prop));
		when(dao.loadBean(Entity.class, Map.of(prop, id))).thenReturn(entity);
		final var edao = spy(new StringConsumerDao());
		assertEquals(entity, edao.load(id));
		verify(edao).afterLoad(entity);
	}

	@Test
	void testSearchByParams() {
		final var key = IdGenerator.generateId();
		final var prop = IdGenerator.generateId();
		final var params = Map.of(prop, key);
		final var id = IdGenerator.generateId();
		final var entity = mock(Entity.class);
		when(dao.getPrimaryKeyProperties(Entity.class)).thenReturn(IuIterable.iter(prop));
		when(dao.searchBeans(Entity.class, params)).thenReturn(List.of(entity));
		when(dao.getBeanKey(entity)).thenReturn(Map.of(IdGenerator.generateId(), id));
		final var edao = spy(new StringConsumerDao());
		final Iterable<Entity> deferred = edao.search(params);
		verify(edao, never()).afterLoad(entity);
		final List<Entity> found = new ArrayList<>();
		deferred.forEach(found::add);
		assertEquals(List.of(entity), found);
		verify(edao).afterLoad(entity);
	}

	@Test
	void testSearchByWhere() {
		class A implements Entity, Comparable<A> {
			String id;
			int i;

			@Override
			public int compareTo(A o) {
				return -Integer.compare(i, o.i);
			}

			@Override
			public void save(Object input) {

			}

			@Override
			public void merge(Object input) {

			}
		}
		class ADao extends EntityDao<String, Object, A> {
		}

		final List<A> results = new ArrayList<>();
		for (var i = 0; i < 10; i++) {
			final var a = new A();
			a.id = IdGenerator.generateId();
			a.i = i;
			results.add(a);
			when(dao.getBeanKey(a)).thenReturn(Map.of(IdGenerator.generateId(), a.id));
		}

		final var where = IdGenerator.generateId();
		final var query = mock(SqlQuery.class);
		when(query.getResults()).thenReturn(results);
		when(dao.getBeanQuery(A.class, IuIterable.iter(where), IuIterable.iter())).thenReturn(query);
		when(dao.getPrimaryKeyProperties(A.class)).thenReturn(IuIterable.iter("i"));

		final var edao = spy(new ADao());
		final List<A> all = edao.searchWhere(IuIterable.iter(where), IuIterable.iter(), new IuEntityDao.Search<>(null, null, 0, null));
		assertEquals(10, all.size());
		assertThrows(UnsupportedOperationException.class, () -> all.clear());
		final var search = all.iterator();
		for (var i = 0; i < 10; i++) {
			final var a = search.next();
			assertEquals(9 - i, a.i);
			assertEquals(a, results.get(a.i));
			verify(edao).afterLoad(a);
		}

		reset(edao);
		final var range = edao.searchWhere(IuIterable.iter(where), IuIterable.iter(), new IuEntityDao.Search<>(null, null, 2, 7)).iterator();
		for (var i = 0; i < 5; i++) {
			final var a = range.next();
			assertEquals(7 - i, a.i);
			assertEquals(a, results.get(a.i));
			verify(edao).afterLoad(a);
		}
		assertFalse(range.hasNext());
		for (final var a : results)
			if (a.i < 3 || a.i > 7)
				verify(edao, never()).afterLoad(a);

		reset(edao);
		final var range2 = edao.searchWhere(IuIterable.iter(where), IuIterable.iter(), new IuEntityDao.Search<>(null, null, 0, 3)).iterator();
		for (var i = 0; i < 3; i++) {
			final var a = range2.next();
			assertEquals(9 - i, a.i);
			assertEquals(a, results.get(a.i));
			verify(edao).afterLoad(a);
		}
		assertFalse(range2.hasNext());
		for (final var a : results)
			if (a.i < 7)
				verify(edao, never()).afterLoad(a);
	}

	@Test
	void testSaveCreate() {
		final var prop = IdGenerator.generateId();
		when(dao.getPrimaryKeyProperties(Entity.class)).thenReturn(IuIterable.iter(prop));

		final var id = IdGenerator.generateId();
		final var entity = mock(Entity.class);
		when(dao.newBean(Entity.class, Map.of(prop, id))).thenReturn(entity);

		final var input = new Object();
		final var edao = spy(new StringConsumerDao());
		when(edao.key(input)).thenReturn(id);

		when(dao.loadBean(Entity.class, Map.of(prop, id))).thenThrow(EntityNotFoundException.class);
		assertEquals(id, edao.save(null, input));
		verify(edao, never()).afterLoad(entity);
		verify(entity).save(input);
		verify(edao).afterInput(any(), eq(entity));
		verify(dao).saveBean(entity);
		verify(edao).afterSave(any(), eq(entity));
	}

	@Test
	void testSaveUpdate() {
		final var prop = IdGenerator.generateId();
		when(dao.getPrimaryKeyProperties(Entity.class)).thenReturn(IuIterable.iter(prop));

		final var id = IdGenerator.generateId();
		final var entity = mock(Entity.class);
		final var input = new Object();
		final var edao = spy(new StringConsumerDao());
		when(edao.key(input)).thenReturn(id);

		when(dao.loadBean(Entity.class, Map.of(prop, id))).thenReturn(entity);
		assertEquals(id, edao.save(null, input));
		verify(edao).afterLoad(entity);
		verify(entity).save(input);
		verify(edao).afterInput(any(), eq(entity));
		verify(dao).saveBean(entity);
		verify(edao).afterSave(any(), eq(entity));
	}

	@Test
	void testSaveUpdateInvokesBeforeInput() {
		final var prop = IdGenerator.generateId();
		when(dao.getPrimaryKeyProperties(Entity.class)).thenReturn(IuIterable.iter(prop));

		final var id = IdGenerator.generateId();
		final var entity = mock(Entity.class);
		final var input = new Object();
		final var edao = spy(new StringConsumerDao());
		when(dao.loadBean(Entity.class, Map.of(prop, id))).thenReturn(entity);

		assertEquals(id, edao.save(id, input));
		final var order = inOrder(edao, entity, dao);
		order.verify(edao).beforeInput(any(), eq(entity));
		order.verify(entity).save(input);
		order.verify(edao).afterInput(any(), eq(entity));
		order.verify(dao).saveBean(entity);
		order.verify(edao).afterSave(any(), eq(entity));
		verify(edao, never()).afterMerge(any(), eq(entity));
		verify(entity, never()).merge(input);
	}

	@Test
	void testSaveCreateSkipsBeforeInput() {
		final var prop = IdGenerator.generateId();
		when(dao.getPrimaryKeyProperties(Entity.class)).thenReturn(IuIterable.iter(prop));

		final var id = IdGenerator.generateId();
		final var entity = mock(Entity.class);
		final var input = new Object();
		when(dao.newBean(Entity.class, Map.of(prop, id))).thenReturn(entity);
		when(dao.loadBean(Entity.class, Map.of(prop, id))).thenThrow(EntityNotFoundException.class);

		final var edao = spy(new StringConsumerDao());
		assertEquals(id, edao.save(id, input));
		verify(edao, never()).beforeInput(any(), eq(entity));
		verify(entity).save(input);
		verify(edao).afterInput(any(), eq(entity));
		verify(dao).saveBean(entity);
		verify(edao).afterSave(any(), eq(entity));
	}

	@Test
	void testBeforeInputNotFoundPropagatesFromSave() {
		final var prop = IdGenerator.generateId();
		when(dao.getPrimaryKeyProperties(Entity.class)).thenReturn(IuIterable.iter(prop));

		final var id = IdGenerator.generateId();
		final var entity = mock(Entity.class);
		final var input = new Object();
		when(dao.loadBean(Entity.class, Map.of(prop, id))).thenReturn(entity);

		final var edao = spy(new StringConsumerDao());
		doThrow(EntityNotFoundException.class).when(edao).beforeInput(any(), eq(entity));
		assertThrows(EntityNotFoundException.class, () -> edao.save(id, input));
		verify(dao, never()).newBean(any(), any());
		verify(dao, never()).saveBean(any());
	}

	@Test
	void testSaveExplicitKeyOnly() {
		final var prop = IdGenerator.generateId();
		when(dao.getPrimaryKeyProperties(Entity.class)).thenReturn(IuIterable.iter(prop));

		final var id = IdGenerator.generateId();
		final var entity = mock(Entity.class);
		final var input = new Object();
		when(dao.newBean(Entity.class, Map.of(prop, id))).thenReturn(entity);
		when(dao.loadBean(Entity.class, Map.of(prop, id))).thenThrow(EntityNotFoundException.class);

		final var edao = new StringConsumerDao();
		assertEquals(id, edao.save(id, input));
		verify(dao).saveBean(entity);
	}

	@Test
	void testSaveMatchingKeys() {
		final var prop = IdGenerator.generateId();
		when(dao.getPrimaryKeyProperties(Entity.class)).thenReturn(IuIterable.iter(prop));

		final var id = IdGenerator.generateId();
		final var entity = mock(Entity.class);
		when(dao.loadBean(Entity.class, Map.of(prop, id))).thenReturn(entity);

		final var edao = new StringConsumerDao();
		assertEquals(id, edao.save(id, id));
		verify(dao).saveBean(entity);
	}

	@Test
	void testSaveMismatchedKeys() {
		final var edao = new StringConsumerDao();
		final var id = IdGenerator.generateId();
		final var other = IdGenerator.generateId();
		assertThrows(IllegalArgumentException.class, () -> edao.save(id, other));
		verify(dao, never()).saveBean(any());
	}

	@Test
	void testSaveGeneratesKey() {
		final var prop = IdGenerator.generateId();
		when(dao.getPrimaryKeyProperties(Entity.class)).thenReturn(IuIterable.iter(prop));
		final var entity = mock(Entity.class);
		when(dao.newBean(eq(Entity.class), any())).thenReturn(entity);
		when(dao.loadBean(eq(Entity.class), any())).thenThrow(EntityNotFoundException.class);

		final var edao = new StringConsumerDao();
		final var key = edao.save(null, new Object());
		assertDoesNotThrow(() -> IdGenerator.verifyId(key, 0L));
		verify(dao).newBean(Entity.class, Map.of(prop, key));
		verify(dao).saveBean(entity);
	}

	@Test
	void testSaveUnsupportedGenerate() {
		final var edao = new IntegerConsumerDao();
		assertThrows(UnsupportedOperationException.class, () -> edao.save(null, new Object()));
		verify(dao, never()).saveBean(any());
	}

	@Test
	void testMerge() {
		final var prop = IdGenerator.generateId();
		when(dao.getPrimaryKeyProperties(Entity.class)).thenReturn(IuIterable.iter(prop));

		final var id = IdGenerator.generateId();
		final var entity = mock(Entity.class);
		final var input = new Object();
		when(dao.loadBean(Entity.class, Map.of(prop, id))).thenReturn(entity);

		final var edao = spy(new StringConsumerDao());
		assertEquals(id, edao.merge(id, input));

		final var order = inOrder(edao, entity, dao);
		order.verify(edao).afterLoad(entity);
		order.verify(edao).beforeInput(any(), eq(entity));
		order.verify(entity).merge(input);
		order.verify(edao).afterInput(any(), eq(entity));
		order.verify(dao).saveBean(entity);
		order.verify(edao).afterMerge(any(), eq(entity));
		verify(entity, never()).save(input);
		verify(edao, never()).afterSave(any(), eq(entity));
	}

	@Test
	void testMergeKeyFromInput() {
		final var prop = IdGenerator.generateId();
		when(dao.getPrimaryKeyProperties(Entity.class)).thenReturn(IuIterable.iter(prop));

		final var id = IdGenerator.generateId();
		final var entity = mock(Entity.class);
		when(dao.loadBean(Entity.class, Map.of(prop, id))).thenReturn(entity);

		final var edao = new StringConsumerDao();
		assertEquals(id, edao.merge(null, id));
		assertEquals(id, edao.merge(id, id));
		verify(entity, times(2)).merge(id);
	}

	@Test
	void testMergeKeyMismatch() {
		final var edao = new StringConsumerDao();
		final var id = IdGenerator.generateId();
		final var other = IdGenerator.generateId();
		assertThrows(IllegalArgumentException.class, () -> edao.merge(id, other));
		verify(dao, never()).saveBean(any());
	}

	@Test
	void testMergeNoKey() {
		final var edao = new StringConsumerDao();
		assertThrows(NullPointerException.class, () -> edao.merge(null, new Object()));
		verify(dao, never()).saveBean(any());
	}

	@Test
	void testMergeNotFound() {
		final var prop = IdGenerator.generateId();
		when(dao.getPrimaryKeyProperties(Entity.class)).thenReturn(IuIterable.iter(prop));
		final var id = IdGenerator.generateId();
		when(dao.loadBean(Entity.class, Map.of(prop, id))).thenThrow(EntityNotFoundException.class);

		final var edao = new StringConsumerDao();
		assertThrows(EntityNotFoundException.class, () -> edao.merge(id, new Object()));
		verify(dao, never()).saveBean(any());
	}

	@Test
	void testSaveInputCarryingKey() {
		final var prop = IdGenerator.generateId();
		when(dao.getPrimaryKeyProperties(Entity.class)).thenReturn(IuIterable.iter(prop));

		final var id = IdGenerator.generateId();
		final var entity = mock(Entity.class);
		when(dao.loadBean(Entity.class, Map.of(prop, id))).thenReturn(entity);

		final var edao = spy(new StringConsumerDao());
		assertEquals(id, edao.save(id));
		verify(edao).save(null, id);
		verify(entity).save(id);
		verify(dao).saveBean(entity);
		verify(edao).afterSave(any(), eq(entity));
	}

	@Test
	void testMergeInputCarryingKey() {
		final var prop = IdGenerator.generateId();
		when(dao.getPrimaryKeyProperties(Entity.class)).thenReturn(IuIterable.iter(prop));

		final var id = IdGenerator.generateId();
		final var entity = mock(Entity.class);
		when(dao.loadBean(Entity.class, Map.of(prop, id))).thenReturn(entity);

		final var edao = spy(new StringConsumerDao());
		assertEquals(id, edao.merge(id));
		verify(edao).merge(null, id);
		verify(entity).merge(id);
		verify(dao).saveBean(entity);
		verify(edao).afterMerge(any(), eq(entity));
	}

	@Test
	void testDelete() {
		final var id = IdGenerator.generateId();
		final var prop = IdGenerator.generateId();
		final var entity = mock(Entity.class);
		when(dao.getPrimaryKeyProperties(Entity.class)).thenReturn(IuIterable.iter(prop));
		when(dao.loadBean(Entity.class, Map.of(prop, id))).thenReturn(entity);
		final var edao = spy(new StringConsumerDao());
		edao.delete(id);
		verify(edao, never()).afterLoad(entity);
		final var order = inOrder(edao, dao);
		order.verify(edao).beforeDelete(entity);
		order.verify(dao).deleteBean(entity);
		order.verify(edao).afterDelete(entity);
	}

	@Test
	void testDeleteNoop() {
		final var id = IdGenerator.generateId();
		final var prop = IdGenerator.generateId();
		final var entity = mock(Entity.class);
		when(dao.getPrimaryKeyProperties(Entity.class)).thenReturn(IuIterable.iter(prop));
		when(dao.loadBean(Entity.class, Map.of(prop, id))).thenThrow(EntityNotFoundException.class);
		final var edao = spy(new StringConsumerDao());
		edao.delete(id);
		verify(edao, never()).afterLoad(entity);
		verify(edao, never()).beforeDelete(entity);
		verify(edao, never()).afterDelete(entity);
		verify(dao, never()).deleteBean(entity);
	}

}
