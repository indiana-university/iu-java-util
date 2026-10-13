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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuIterable;
import jakarta.persistence.EntityNotFoundException;

@SuppressWarnings("unchecked")
public class IuEntityDaoTest {

	private IuDao dao;

	private class EntityDao<K, I, E extends Consumer<I>> extends IuEntityDao<K, I, E> {
		@Override
		protected IuDao dao() {
			return dao;
		}
	}

	private class StringConsumerDao extends EntityDao<String, Object, Consumer<Object>> {
	};

	private class ObjectConsumerDao extends EntityDao<Object, Object, Consumer<Object>> {
	};

	@BeforeEach
	void setup() {
		dao = mock(IuDao.class);
	}

	@Test
	void testId() {
		final var id = IdGenerator.generateId();
		final var prop = IdGenerator.generateId();
		when(dao.getPrimaryKeyProperties(Consumer.class)).thenReturn(IuIterable.iter(prop));
		final var edao = new StringConsumerDao();
		assertEquals(Map.of(prop, id), edao.id(id));
	}

	@Test
	void testKeyFromEntity() {
		final var entity = mock(Consumer.class);
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
		when(dao.getPrimaryKeyProperties(Consumer.class)).thenReturn(IuIterable.iter(prop));
		final var edao = spy(new StringConsumerDao());
		edao.newEntity(id);
		verify(dao).newBean(Consumer.class, Map.of(prop, id));
	}

	@Test
	void testNoOrder() {
		final var edao = spy(new StringConsumerDao());
		assertNull(edao.order());
	}

	interface NaturallyOrdered extends Consumer<Object>, Comparable<NaturallyOrdered> {
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
		final var entity = mock(Consumer.class);
		when(dao.getPrimaryKeyProperties(Consumer.class)).thenReturn(IuIterable.iter(prop));
		when(dao.loadBean(Consumer.class, Map.of(prop, id))).thenReturn(entity);
		final var edao = spy(new StringConsumerDao());
		assertEquals(entity, edao.load(id));
		verify(edao).afterLoad(id, entity);
	}

	@Test
	void testSearchByParams() {
		final var key = IdGenerator.generateId();
		final var prop = IdGenerator.generateId();
		final var params = Map.of(prop, key);
		final var id = IdGenerator.generateId();
		final var entity = mock(Consumer.class);
		when(dao.getPrimaryKeyProperties(Consumer.class)).thenReturn(IuIterable.iter(prop));
		when(dao.searchBeans(Consumer.class, params)).thenReturn(List.of(entity));
		when(dao.getBeanKey(entity)).thenReturn(Map.of(IdGenerator.generateId(), id));
		final var edao = spy(new StringConsumerDao());
		assertEquals(entity, edao.search(params).iterator().next());
		verify(edao).afterLoad(id, entity);
	}

	@Test
	void testSearchByWhere() {
		class A implements Consumer<Object>, Comparable<A> {
			String id;
			int i;

			@Override
			public int compareTo(A o) {
				return -Integer.compare(i, o.i);
			}

			@Override
			public void accept(Object t) {

			}
		}
		class ADao extends EntityDao<String, Object, A>{}

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
		final var search = edao.searchWhere(where).iterator();
		for (var i = 0; i < 10; i++) {
			final var a = search.next();
			assertEquals(9 - i, a.i);
			assertEquals(a, results.get(a.i));
			verify(edao).afterLoad(a.id, a);
		}

		reset(edao);
		final var range = edao.searchWhere(IuIterable.iter(where), IuIterable.iter(), 2, 7).iterator();
		for (var i = 0; i < 5; i++) {
			final var a = range.next();
			assertEquals(7 - i, a.i);
			assertEquals(a, results.get(a.i));
			verify(edao).afterLoad(a.id, a);
		}
		assertFalse(range.hasNext());
		for (final var a : results)
			if (a.i < 3 || a.i > 7)
				verify(edao, never()).afterLoad(a.id, a);

		reset(edao);
		final var range2 = edao.searchWhere(IuIterable.iter(where), IuIterable.iter(), 0, 3).iterator();
		for (var i = 0; i < 3; i++) {
			final var a = range2.next();
			assertEquals(9 - i, a.i);
			assertEquals(a, results.get(a.i));
			verify(edao).afterLoad(a.id, a);
		}
		assertFalse(range2.hasNext());
		for (final var a : results)
			if (a.i < 7)
				verify(edao, never()).afterLoad(a.id, a);
	}

	@Test
	void testSaveCreate() {
		final var prop = IdGenerator.generateId();
		when(dao.getPrimaryKeyProperties(Consumer.class)).thenReturn(IuIterable.iter(prop));

		final var id = IdGenerator.generateId();
		final var entity = mock(Consumer.class);
		when(dao.newBean(Consumer.class, Map.of(prop, id))).thenReturn(entity);

		final var input = new Object();
		final var edao = spy(new StringConsumerDao());
		when(edao.key(input)).thenReturn(id);

		when(edao.load(id)).thenThrow(EntityNotFoundException.class);
		assertEquals(id, edao.save(input));
		verify(entity).accept(input);
		verify(edao).beforeSave(id, entity, input);
		verify(dao).saveBean(entity);
		verify(edao).afterSave(id, entity, input);
	}

	@Test
	void testSaveUpdate() {
		final var prop = IdGenerator.generateId();
		when(dao.getPrimaryKeyProperties(Consumer.class)).thenReturn(IuIterable.iter(prop));

		final var id = IdGenerator.generateId();
		final var entity = mock(Consumer.class);
		final var input = new Object();
		final var edao = spy(new StringConsumerDao());
		when(edao.key(input)).thenReturn(id);

		when(edao.load(id)).thenReturn(entity);
		assertEquals(id, edao.save(input));
		verify(entity).accept(input);
		verify(edao).beforeSave(id, entity, input);
		verify(dao).saveBean(entity);
		verify(edao).afterSave(id, entity, input);
	}

	@Test
	void testDelete() {
		final var id = IdGenerator.generateId();
		final var prop = IdGenerator.generateId();
		final var entity = mock(Consumer.class);
		when(dao.getPrimaryKeyProperties(Consumer.class)).thenReturn(IuIterable.iter(prop));
		when(dao.loadBean(Consumer.class, Map.of(prop, id))).thenReturn(entity);
		final var edao = spy(new StringConsumerDao());
		edao.delete(id);
		verify(edao, never()).afterLoad(id, entity);
		verify(edao).beforeDelete(id, entity);
		verify(dao).deleteBean(entity);
	}

	@Test
	void testDeleteNoop() {
		final var id = IdGenerator.generateId();
		final var prop = IdGenerator.generateId();
		final var entity = mock(Consumer.class);
		when(dao.getPrimaryKeyProperties(Consumer.class)).thenReturn(IuIterable.iter(prop));
		when(dao.loadBean(Consumer.class, Map.of(prop, id))).thenThrow(EntityNotFoundException.class);
		final var edao = spy(new StringConsumerDao());
		edao.delete(id);
		verify(edao, never()).afterLoad(id, entity);
		verify(edao, never()).beforeDelete(id, entity);
		verify(dao, never()).deleteBean(entity);
	}

}
