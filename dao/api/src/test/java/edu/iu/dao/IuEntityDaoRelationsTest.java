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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IuIterable;
import jakarta.persistence.EntityNotFoundException;

/**
 * Covers the parent/child support in {@link IuEntityDao}: parent and input
 * context, parent-driven search, child collections, and entity decorators.
 */
@SuppressWarnings("unchecked")
public class IuEntityDaoRelationsTest {

	private IuDao dao;

	interface Entity extends SaveableEntity<Object>, MergeableEntity<Object> {
	}

	interface Owned extends Entity, ChildEntity {
	}

	interface Seq extends Entity, SequencedEntity {
	}

	private class SeqDao extends IuEntityDao<String, Object, Seq> {
		@Override
		protected boolean tryBinding(Object parent, Seq child) {
			return true;
		}

		@Override
		protected IuDao dao() {
			return dao;
		}
	}

	private class EntityOnlyDao extends IuEntityDao<String, Object, Entity> {
		@Override
		protected boolean tryBinding(Object parent, Entity child) {
			return true;
		}

		@Override
		protected IuDao dao() {
			return dao;
		}
	}

	private class PlainDao extends IuEntityDao<String, Object, Object> {
		@Override
		protected boolean tryBinding(Object parent, Object child) {
			return true;
		}

		@Override
		protected IuDao dao() {
			return dao;
		}
	}

	@BeforeEach
	void setup() {
		dao = mock(IuDao.class);
		for (final var type : List.of(Seq.class, Entity.class, Owned.class, Object.class))
			when(dao.getPrimaryKeyProperties(type)).thenReturn(IuIterable.iter("id"));

		// generic lookups for keys generated at runtime; specific stubs registered
		// later take precedence
		doThrow(EntityNotFoundException.class).when(dao).loadBean(eq(Seq.class), any());
		doReturn(mock(Seq.class)).when(dao).newBean(eq(Seq.class), any());
	}

	private <T> T stored(Class<T> type, String key) {
		final var entity = mock(type);
		doReturn(entity).when(dao).loadBean(type, Map.of("id", key));
		when(dao.getBeanKey(entity)).thenReturn(Map.of("id", key));
		return entity;
	}

	private <T> T fresh(Class<T> type, String key) {
		final var entity = mock(type);
		doThrow(EntityNotFoundException.class).when(dao).loadBean(type, Map.of("id", key));
		doReturn(entity).when(dao).newBean(type, Map.of("id", key));
		when(dao.getBeanKey(entity)).thenReturn(Map.of("id", key));
		return entity;
	}

	@Test
	void testEntityKey() {
		final var entity = stored(Seq.class, "a");
		assertEquals("a", new SeqDao().entityKey(entity));
	}

	private static <T> List<T> list(Iterable<T> items) {
		final var list = new ArrayList<T>();
		items.forEach(list::add);
		return list;
	}

	@Test
	void testDefaultSearchIsEmpty() {
		final var edao = new SeqDao();
		assertEquals(List.of(), list(edao.search()));
		assertEquals(List.of(), edao.search(new IuEntityDao.Search<>("parent", null, 1, 5)));
	}

	@Test
	void testSearchDelegatesToParentSearch() {
		final var a = stored(Seq.class, "a");
		final var edao = spy(new SeqDao());
		doReturn(List.of(a)).when(edao).search(any(IuEntityDao.Search.class));
		assertEquals(List.of(a), list(edao.search()));
	}

	@Test
	void testSearchFactoriesCaptureActiveParent() {
		final Predicate<Seq> filter = x -> true;
		assertEquals(new IuEntityDao.Search<Seq>(null, null, 0, null), IuEntityDao.Search.child());
		IuEntityDao.withParent("p", () -> {
			assertEquals(new IuEntityDao.Search<Seq>("p", null, 0, null), IuEntityDao.Search.child());
			assertEquals(new IuEntityDao.Search<Seq>("p", null, 1, 3), IuEntityDao.Search.range(1, 3));
			assertEquals(new IuEntityDao.Search<Seq>("p", filter, 0, null), IuEntityDao.Search.filtered(filter));
			assertEquals(new IuEntityDao.Search<Seq>("p", filter, 2, null),
					IuEntityDao.Search.filteredRange(filter, 2, null));
		});
	}

	@Test
	void testSearchAppliesFilterAfterParentAndBeforeRange() {
		final var rows = List.of(stored(Seq.class, "a"), stored(Seq.class, "b"), stored(Seq.class, "c"),
				stored(Seq.class, "d"));
		doReturn(rows).when(dao).searchBeans(Seq.class, Map.of());
		final var edao = spy(new SeqDao());
		final Predicate<Seq> notB = x -> x != rows.get(1);

		assertEquals(List.of(rows.get(0), rows.get(2), rows.get(3)),
				edao.search(Map.of(), IuEntityDao.Search.filtered(notB)));
		assertEquals(List.of(rows.get(2), rows.get(3)),
				edao.search(Map.of(), IuEntityDao.Search.filteredRange(notB, 1, null)));
		verify(edao, never()).afterLoad(rows.get(1));
	}

	@Test
	void testSearchRangeCapturesParentAndDefersLookup() {
		final var a = stored(Seq.class, "a");
		final var edao = spy(new SeqDao());
		doReturn(List.of(a)).when(edao).search(new IuEntityDao.Search<>("p", null, 0, null));
		final var found = IuEntityDao.withParent("p", () -> edao.search());
		verify(edao, never()).search(any(IuEntityDao.Search.class));
		assertEquals(List.of(a), list(found));
		verify(edao).search(new IuEntityDao.Search<>("p", null, 0, null));
	}

	@Test
	void testSearchCapturesParentAndDefersLookup() {
		final var a = stored(Seq.class, "a");
		final var p = stored(Seq.class, "p");
		final var child = spy(new SeqDao());
		doReturn(List.of(a)).when(child).search(any(IuEntityDao.Search.class));

		final var parentDao = spy(new SeqDao());
		final var captured = new ArrayList<Iterable<Seq>>();
		doAnswer(i -> captured.add(child.search())).when(parentDao).afterSave(any(), eq(p));
		parentDao.save("p");

		verify(child, never()).search(any(IuEntityDao.Search.class));
		assertEquals(List.of(a), list(captured.get(0)));
		verify(child).search(new IuEntityDao.Search<>(p, null, 0, null));
	}

	@Test
	void testDeleteRemovesSearchedChildren() {
		final var a = stored(Seq.class, "a");
		final var b = stored(Seq.class, "b");
		final var p = stored(Seq.class, "p");
		final var child = spy(new SeqDao());
		doReturn(List.of(a, b)).when(child).search(any(IuEntityDao.Search.class));

		final var parentDao = spy(new SeqDao());
		doAnswer(i -> {
			child.delete();
			return null;
		}).when(parentDao).afterDelete(p);
		parentDao.delete("p");

		verify(dao).deleteBean(a);
		verify(dao).deleteBean(b);
		verify(dao).deleteBean(p);
	}

	@Test
	void testDeleteWithoutSearchDeletesNothing() {
		new SeqDao().delete();
		verify(dao, never()).deleteBean(any());
	}

	@Test
	void testParentIsNullOutsideOperations() {
		assertNull(new SeqDao().parent());
	}

	@Test
	void testHooksReceiveParentAndInput() {
		final var p = stored(Seq.class, "p");
		final var edao = spy(new SeqDao());
		final var seen = new ArrayList<Object>();

		doAnswer(i -> seen.add(edao.parent())).when(edao).afterLoad(p);
		doAnswer(i -> seen.add(i.getArgument(0))).when(edao).beforeInput(any(), eq(p));
		doAnswer(i -> seen.add(i.getArgument(0))).when(edao).afterInput(any(), eq(p));
		doAnswer(i -> seen.add(i.getArgument(0))).when(edao).afterSave(any(), eq(p));
		doAnswer(i -> seen.add(edao.parent())).when(edao).afterMerge(any(), eq(p));
		doAnswer(i -> seen.add(edao.parent())).when(edao).beforeDelete(p);
		doAnswer(i -> seen.add(edao.parent())).when(edao).afterDelete(p);

		edao.load("p");
		assertEquals(List.of(p), seen);

		seen.clear();
		edao.save("p");
		assertEquals(List.of(p, "p", "p", "p"), seen);

		seen.clear();
		edao.merge("p");
		assertEquals(List.of(p, "p", "p", p), seen);

		seen.clear();
		edao.delete("p");
		assertEquals(List.of(p, p), seen);

		assertNull(edao.parent());
	}

	@Test
	void testHooksReceiveNullInput() {
		final var p = stored(Seq.class, "p");
		final var edao = spy(new SeqDao());
		edao.save("p", (Object) null);
		verify(edao).beforeInput(null, p);
		verify(edao).afterInput(null, p);
		verify(edao).afterSave(null, p);
		verify(p, never()).save(any());
	}

	@Test
	void testParentRestoredAfterHookFails() {
		final var p = stored(Seq.class, "p");
		final var edao = spy(new SeqDao());

		doThrow(IllegalStateException.class).when(edao).afterLoad(p);
		assertThrows(IllegalStateException.class, () -> edao.load("p"));
		assertNull(edao.parent());

		doThrow(IllegalStateException.class).when(edao).afterSave(any(), eq(p));
		assertThrows(IllegalStateException.class, () -> edao.save("p"));
		assertNull(edao.parent());

		doThrow(IllegalStateException.class).when(edao).afterMerge(any(), eq(p));
		assertThrows(IllegalStateException.class, () -> edao.merge("p"));
		assertNull(edao.parent());

		doThrow(IllegalStateException.class).when(edao).afterDelete(p);
		assertThrows(IllegalStateException.class, () -> edao.delete("p"));
		assertNull(edao.parent());
	}

	@Test
	void testNestedOperationsRestoreParent() {
		final var outer = fresh(Seq.class, "outer");
		final var n1 = stored(Seq.class, "n1");
		stored(Seq.class, "m1");
		stored(Seq.class, "d1");
		fresh(Seq.class, "n2");
		doReturn(List.of(n1)).when(dao).searchBeans(Seq.class, Map.of());

		final var edao = spy(new SeqDao());
		final var nested = new ArrayList<String>();
		doAnswer(i -> {
			if (!"outer".equals(i.getArgument(0)))
				return null;

			edao.load("n1");
			assertSame(outer, edao.parent());

			edao.save("n2");
			assertSame(outer, edao.parent());

			// existing row: afterLoad also restores the parent
			edao.save("n1");
			assertSame(outer, edao.parent());

			edao.merge("m1");
			assertSame(outer, edao.parent());

			edao.delete("d1");
			assertSame(outer, edao.parent());

			assertEquals(List.of(n1), list(edao.search(Map.of())));
			assertSame(outer, edao.parent());
			return nested.add("done");
		}).when(edao).afterSave(any(), any());

		assertEquals("outer", edao.save("outer"));
		assertEquals(List.of("done"), nested);
		assertNull(edao.parent());

		// nested saves delivered their own input, not the outer one
		verify(edao).afterSave(eq("n2"), any());
		verify(edao).afterSave(eq("n1"), any());
	}

	@Test
	void testNestedSaveDoesNotInheritSequence() {
		final var c1 = fresh(Seq.class, "c1");
		final var other = fresh(Seq.class, "other");

		final var edao = spy(new SeqDao());
		doAnswer(i -> {
			if ("c1".equals(i.getArgument(0)))
				edao.save("other");
			return null;
		}).when(edao).afterSave(any(), eq(c1));

		edao.save(List.<Object>of("c1"));

		verify(c1).setSeq(0);
		verify(other, never()).setSeq(any());
	}

	@Test
	void testSelfReferencingHierarchySequencesEachLevel() {
		final var c1 = fresh(Seq.class, "c1");
		final var g1 = fresh(Seq.class, "g1");
		final var g2 = fresh(Seq.class, "g2");
		final var c2 = fresh(Seq.class, "c2");

		final var edao = spy(new SeqDao());
		doAnswer(i -> {
			if ("c1".equals(i.getArgument(0)))
				edao.save(List.<Object>of("g1", "g2"));
			return null;
		}).when(edao).afterSave(any(), eq(c1));

		edao.save(List.<Object>of("c1", "c2"));

		verify(c1).setSeq(0);
		verify(g1).setSeq(0);
		verify(g2).setSeq(1);
		verify(c2).setSeq(1);
	}

	@Test
	void testSaveChildrenNullIsNoop() {
		final var edao = new SeqDao();
		edao.save((Iterable<Object>) null);
		verify(dao, never()).saveBean(any());
		verify(dao, never()).deleteBean(any());
	}

	@Test
	void testSaveChildrenSequencesAndDeletesLeftovers() {
		final var a = stored(Seq.class, "a");
		final var b = stored(Seq.class, "b");
		final var c = stored(Seq.class, "c");
		final var x = fresh(Seq.class, "x");

		final var edao = spy(new SeqDao());
		doReturn(List.of(a, b, c)).when(edao).search(any(IuEntityDao.Search.class));

		edao.save(List.<Object>of("a", "x"));

		verify(a).setSeq(0);
		verify(x).setSeq(1);
		verify(dao).saveBean(a);
		verify(dao).saveBean(x);
		verify(dao).deleteBean(b);
		verify(dao).deleteBean(c);
		verify(dao, never()).deleteBean(a);
		verify(dao, never()).deleteBean(x);

		// the sequence number applies to the collection only
		edao.save("a");
		verify(a, times(1)).setSeq(any());
	}

	@Test
	void testSaveChildrenWithoutSearchDeletesNothing() {
		final var a = fresh(Seq.class, "a");
		final var edao = new SeqDao();
		edao.save(List.<Object>of("a"));
		verify(a).setSeq(0);
		verify(dao).saveBean(a);
		verify(dao, never()).deleteBean(any());
	}

	@Test
	void testSaveChildrenWhenEntityIsNotSequenced() {
		final var a = fresh(Entity.class, "a");
		final var edao = new EntityOnlyDao();
		edao.save(List.<Object>of("a"));
		verify(a).save("a");
		verify(dao).saveBean(a);
	}

	@Test
	void testMergeChildrenNullIsNoop() {
		final var edao = new SeqDao();
		edao.merge((Iterable<Object>) null);
		verify(dao, never()).saveBean(any());
	}

	@Test
	void testMergeChildrenMergesKeyedAndSavesNew() {
		final var a = stored(Seq.class, "a");
		final var b = stored(Seq.class, "b");
		final var created = mock(Seq.class);
		doReturn(created).when(dao).newBean(eq(Seq.class), any());

		final var edao = spy(new SeqDao());
		doReturn(List.of(a, b)).when(edao).search(any(IuEntityDao.Search.class));

		final var fresh = new Object();
		edao.merge(List.of("a", fresh));

		verify(a).merge("a");
		verify(a).setSeq(0);
		verify(b, never()).merge(any());
		verify(b).setSeq(1);
		verify(dao).saveBean(a);
		verify(dao).saveBean(b);

		// unnamed sequenced rows are resequenced with no input; new rows follow
		verify(created).save(fresh);
		verify(created).setSeq(2);
		verify(dao).saveBean(created);
		verify(edao, never()).delete(any());
	}

	@Test
	void testMergeChildrenRejectsDuplicateKey() {
		final var a = stored(Seq.class, "a");
		final var edao = spy(new SeqDao());
		doReturn(List.of(a)).when(edao).search(any(IuEntityDao.Search.class));

		final var children = List.<Object>of("a", "a");
		assertThrows(IllegalArgumentException.class, () -> edao.merge(children));
		verify(dao, never()).saveBean(any());
	}

	@Test
	void testMergeChildrenRejectsUnknownKeyBeforeWriting() {
		final var a = stored(Seq.class, "a");
		final var edao = spy(new SeqDao());
		doReturn(List.of(a)).when(edao).search(any(IuEntityDao.Search.class));

		final var children = List.<Object>of("a", "z", new Object());
		assertThrows(IllegalArgumentException.class, () -> edao.merge(children));
		verify(a, never()).merge(any());
		verify(dao, never()).saveBean(any());
	}

	@Test
	void testMergeChildrenKeyedWithoutSearchIsRejected() {
		stored(Seq.class, "a");
		final var edao = new SeqDao();
		final var children = List.<Object>of("a");
		assertThrows(IllegalArgumentException.class, () -> edao.merge(children));
		verify(dao, never()).saveBean(any());
	}

	@Test
	void testNestedChildCollectionsSequenceIndependently() {
		final var created = mock(Seq.class);
		doReturn(created).when(dao).newBean(eq(Seq.class), any());
		final var c1 = fresh(Seq.class, "c1");
		final var c2 = fresh(Seq.class, "c2");
		final var m1 = stored(Seq.class, "m1");

		final var edao = spy(new SeqDao());
		doAnswer(i -> {
			edao.save(List.<Object>of("c2"));
			edao.merge(List.of(new Object()));
			edao.merge("m1");
			return null;
		}).when(edao).afterSave(any(), eq(c1));

		edao.save(List.<Object>of("c1"));

		verify(c1).setSeq(0);
		verify(c2).setSeq(0);
		verify(created).setSeq(0);
		// a plain merge inside a collection save takes no position
		verify(m1, never()).setSeq(any());
		verify(dao).saveBean(c2);
		verify(dao).saveBean(m1);
	}

	@Test
	void testMergeChildrenLeavesUnnamedRowsWhenNotSequenced() {
		final var a = stored(Entity.class, "a");
		final var b = stored(Entity.class, "b");

		final var edao = spy(new EntityOnlyDao());
		doReturn(List.of(a, b)).when(edao).search(any(IuEntityDao.Search.class));

		edao.merge(List.of("a"));

		verify(a).merge("a");
		verify(dao).saveBean(a);
		verify(dao, never()).saveBean(b);
	}

	@Test
	void testMergeWithinCollectionWhenEntityIsNotSequenced() {
		final var c1 = fresh(Entity.class, "c1");
		final var m1 = stored(Entity.class, "m1");

		final var edao = spy(new EntityOnlyDao());
		doAnswer(i -> edao.merge("m1")).when(edao).afterSave(any(), eq(c1));

		edao.save(List.<Object>of("c1"));

		verify(m1).merge("m1");
		verify(dao).saveBean(m1);
	}

	@Test
	void testSaveWithNullInputAppliesNothing() {
		final var e = fresh(Entity.class, "k");
		assertEquals("k", new EntityOnlyDao().save("k", (Object) null));
		verify(e, never()).save(any());
		verify(dao).saveBean(e);
	}

	@Test
	void testWithParentInjectsAndRestores() {
		final var seen = new ArrayList<Object>();
		final var edao = new SeqDao() {
			void note() {
				seen.add(parent());
			}
		};
		IuEntityDao.withParent("outer", () -> {
			edao.note();
			IuEntityDao.withParent("inner", (Runnable) edao::note);
			edao.note();
			assertEquals("v", IuEntityDao.withParent("x", () -> "v"));
		});
		edao.note();
		assertEquals(java.util.Arrays.asList("outer", "inner", "outer", null), seen);
	}

	@Test
	void testVerbsInjectParent() {
		final var seen = new ArrayList<Object>();
		final var p = stored(Seq.class, "p");
		final var a = stored(Seq.class, "a");
		final var fresh = fresh(Seq.class, "n");
		final var edao = spy(new SeqDao());
		doAnswer(i -> seen.add(edao.parent())).when(edao).beforeInput(any(), eq(a));
		doAnswer(i -> seen.add(edao.parent())).when(edao).beforeInput(any(), eq(p));
		doReturn(List.of(a)).when(edao).search(new IuEntityDao.Search<>("parent", null, 0, null));
		doAnswer(i -> seen.add(edao.parent())).when(edao).afterInput(any(), eq(fresh));

		assertSame(p, edao.load("parent", "p"));
		assertEquals("n", edao.save("parent", "n", (Object) "n"));
		assertEquals("a", edao.merge("parent", "a", (Object) "a"));
		assertEquals(List.of("parent", "parent"), seen);
		// a bare reference to hook state above must not leak
		assertNull(edao.parent());
	}

	@Test
	void testChildCollectionVerbsInjectParent() {
		final var a = stored(Seq.class, "a");
		final var n = fresh(Seq.class, "n");
		final var edao = spy(new SeqDao());
		doReturn(List.of(a)).when(edao).search(new IuEntityDao.Search<>("parent", null, 0, null));

		edao.save((Object) "parent", List.<Object>of("n"));
		verify(dao).saveBean(n);
		verify(dao).deleteBean(a);

		edao.merge((Object) "parent", List.<Object>of("a"));
		verify(a).merge("a");
		assertNull(edao.parent());
	}

	@Test
	void testDeleteWithParentAndDeleteChildren() {
		final var a = stored(Seq.class, "a");
		final var b = stored(Seq.class, "b");
		final var seen = new ArrayList<Object>();
		final var edao = spy(new SeqDao());
		doAnswer(i -> seen.add(edao.parent())).when(edao).beforeDelete(a);
		doReturn(List.of(a, b)).when(edao).search(new IuEntityDao.Search<>("parent", null, 0, null));

		edao.delete("parent", "a");
		assertEquals(List.of(a), seen);
		verify(dao).deleteBean(a);
		verify(dao, never()).deleteBean(b);

		edao.deleteChildren("parent");
		verify(dao, times(2)).deleteBean(a);
		verify(dao).deleteBean(b);
	}

	@Test
	void testSearchRangeIsClamped() {
		final var a = stored(Seq.class, "a");
		final var b = stored(Seq.class, "b");
		doReturn(List.of(a, b)).when(dao).searchBeans(Seq.class, Map.of());
		assertEquals(List.of(), list(new SeqDao().search(Map.of(), IuEntityDao.Search.range(5, 9))));
		assertEquals(List.of(b), list(new SeqDao().search(Map.of(), IuEntityDao.Search.range(1, 9))));
		assertEquals(List.of(a, b), list(new SeqDao().search(Map.of(), IuEntityDao.Search.range(-3, null))));
	}

	@Test
	void testSearchRangeSelectsFromFilteredRows() {
		final var rows = List.of(stored(Seq.class, "a"), stored(Seq.class, "b"), stored(Seq.class, "c"),
				stored(Seq.class, "d"));
		doReturn(rows).when(dao).searchBeans(Seq.class, Map.of());
		assertEquals(List.of(rows.get(0), rows.get(1)), list(new SeqDao().search(Map.of(), IuEntityDao.Search.range(0, 2))));
		assertEquals(List.of(rows.get(1), rows.get(2)), list(new SeqDao().search(Map.of(), IuEntityDao.Search.range(1, 3))));
		assertEquals(List.of(rows.get(2)), list(new SeqDao().search(Map.of(), IuEntityDao.Search.range(2, 3))));
		assertEquals(List.of(rows.get(2), rows.get(3)), list(new SeqDao().search(Map.of(), IuEntityDao.Search.range(2, null))));
	}

	@Test
	void testChildEntityBindFailsFastWhenNotBound() {
		final var child = new ChildEntity() {
			@Override
			public boolean tryBinding(Object parent) {
				return "p".equals(parent);
			}

			@Override
			public String toString() {
				return "child-a";
			}
		};
		child.bind("p");
		assertEquals("unexpected parent q for child child-a",
				assertThrows(IllegalArgumentException.class, () -> child.bind("q")).getMessage());
	}

	private class StrictDao extends IuEntityDao<String, Object, Seq> {
		@Override
		protected IuDao dao() {
			return dao;
		}
	}

	private class OwnedDao extends IuEntityDao<String, Object, Seq> {
		@Override
		protected boolean tryBinding(Object parent, Seq child) {
			return "owner".equals(parent);
		}

		@Override
		protected IuDao dao() {
			return dao;
		}
	}

	@Test
	void testDefaultIsChildOfRequiresNoParent() {
		final var edao = new StrictDao();
		final var a = stored(Seq.class, "a");
		assertSame(a, edao.load("a"));
		assertThrows(IllegalArgumentException.class, () -> edao.load("someone", "a"));
	}

	@Test
	void testIsChildOfGuardsLoad() {
		final var a = stored(Seq.class, "a");
		final var edao = new OwnedDao();
		assertSame(a, edao.load("owner", "a"));
		assertThrows(IllegalArgumentException.class, () -> edao.load("other", "a"));
		assertThrows(IllegalArgumentException.class, () -> edao.load("a"));
	}

	@Test
	void testIsChildOfGuardsSaveExistingBeforeHooks() {
		final var a = stored(Seq.class, "a");
		final var edao = spy(new OwnedDao());
		assertEquals("a", edao.save("owner", "a", (Object) "a"));
		verify(dao).saveBean(a);

		assertThrows(IllegalArgumentException.class, () -> edao.save("other", "a", (Object) "a"));
		verify(edao, times(1)).beforeInput(any(), eq(a));
		verify(dao, times(1)).saveBean(a);
	}

	@Test
	void testIsChildOfGuardsSaveNew() {
		final var n = fresh(Seq.class, "n");
		final var edao = new OwnedDao();
		assertThrows(IllegalArgumentException.class, () -> edao.save("other", "n", (Object) "n"));
		verify(dao, never()).saveBean(any());
		assertEquals("n", edao.save("owner", "n", (Object) "n"));
		verify(dao).saveBean(n);
	}

	@Test
	void testIsChildOfGuardsMerge() {
		final var a = stored(Seq.class, "a");
		final var edao = new OwnedDao();
		assertThrows(IllegalArgumentException.class, () -> edao.merge("other", "a", (Object) "a"));
		verify(dao, never()).saveBean(any());
		assertEquals("a", edao.merge("owner", "a", (Object) "a"));
		verify(dao).saveBean(a);
	}

	@Test
	void testIsChildOfGuardsDelete() {
		final var a = stored(Seq.class, "a");
		final var edao = new OwnedDao();
		assertThrows(IllegalArgumentException.class, () -> edao.delete("other", "a"));
		verify(dao, never()).deleteBean(any());

		// a missing row is not an invalid child
		doThrow(EntityNotFoundException.class).when(dao).loadBean(eq(Seq.class), eq(Map.of("id", "gone")));
		edao.delete("other", "gone");

		edao.delete("owner", "a");
		verify(dao).deleteBean(a);
	}

	private class OwnedChildDao extends IuEntityDao<String, Object, Owned> {
		@Override
		protected IuDao dao() {
			return dao;
		}
	}

	@Test
	void testChildEntityBindsActiveParent() {
		final var a = stored(Owned.class, "a");
		final var edao = new OwnedChildDao();

		// no parent: bind(null) is allowed
		assertSame(a, edao.load("a"));
		verify(a).bind(null);

		assertSame(a, edao.load("p", "a"));
		verify(a).bind("p");

		doThrow(IllegalArgumentException.class).when(a).bind("q");
		assertThrows(IllegalArgumentException.class, () -> edao.load("q", "a"));
	}

	@Test
	void testChildEntityIsBoundWhenCreatedAndWhenStored() {
		final var a = stored(Owned.class, "a");
		final var n = fresh(Owned.class, "n");
		final var edao = new OwnedChildDao();

		assertEquals("a", edao.save("p", "a", (Object) "a"));
		verify(a).bind("p");
		verify(dao).saveBean(a);

		assertEquals("n", edao.save("p", "n", (Object) "n"));
		verify(n).bind("p");
		verify(dao).saveBean(n);

		doThrow(IllegalArgumentException.class).when(a).bind("q");
		assertThrows(IllegalArgumentException.class, () -> edao.save("q", "a", (Object) "a"));
		verify(dao, times(1)).saveBean(a);
	}

	@Test
	void testChildEntityIsBoundOnMergeAndDelete() {
		final var a = stored(Owned.class, "a");
		final var edao = new OwnedChildDao();

		assertEquals("a", edao.merge("p", "a", (Object) "a"));
		verify(dao).saveBean(a);
		verify(a).bind("p");

		doThrow(IllegalArgumentException.class).when(a).bind("q");
		assertThrows(IllegalArgumentException.class, () -> edao.delete("q", "a"));
		verify(dao, never()).deleteBean(any());
		edao.delete("p", "a");
		verify(dao).deleteBean(a);
	}

	@Test
	void testRejectionNamesParentAndChild() {
		final var a = stored(Seq.class, "a");
		final var error = assertThrows(IllegalArgumentException.class, () -> new StrictDao().load("p", "a"));
		assertEquals("unexpected parent p for child " + a, error.getMessage());
	}

	@Test
	void testEntityWithoutDecoratorsIsLeftUntouched() {
		final var created = new Object();
		doReturn(created).when(dao).newBean(Object.class, Map.of("id", "k"));
		doThrow(EntityNotFoundException.class).when(dao).loadBean(Object.class, Map.of("id", "k"));

		final var edao = new PlainDao();
		assertEquals("k", edao.save("k"));
		verify(dao).saveBean(created);

		final var stored = new Object();
		doReturn(stored).when(dao).loadBean(Object.class, Map.of("id", "m"));
		assertEquals("m", edao.merge("m"));
		verify(dao).saveBean(stored);
	}

	@Test
	void testMergeHookOrder() {
		final var a = stored(Seq.class, "a");
		final var edao = spy(new SeqDao());
		edao.merge("a");
		final var order = inOrder(edao, a, dao);
		order.verify(edao).beforeInput(any(), eq(a));
		order.verify(a).merge("a");
		order.verify(edao).afterInput(any(), eq(a));
		order.verify(dao).saveBean(a);
		order.verify(edao).afterMerge(any(), eq(a));
	}

	@Test
	void testSaveHookOrderInjectsSequenceBeforeAfterInput() {
		final var a = stored(Seq.class, "a");
		final var edao = spy(new SeqDao());
		edao.save(List.<Object>of("a"));
		final var order = inOrder(edao, a, dao);
		order.verify(edao).beforeInput(any(), eq(a));
		order.verify(a).save("a");
		order.verify(a).setSeq(0);
		order.verify(edao).afterInput(any(), eq(a));
		order.verify(dao).saveBean(a);
		order.verify(edao).afterSave(any(), eq(a));
	}

	@Test
	void testMergeHookOrderInjectsSequenceBeforeAfterInput() {
		final var a = stored(Seq.class, "a");
		final var edao = spy(new SeqDao());
		doReturn(List.of(a)).when(edao).search(any(IuEntityDao.Search.class));
		edao.merge(List.<Object>of("a"));
		final var order = inOrder(edao, a, dao);
		order.verify(edao).beforeInput(any(), eq(a));
		order.verify(a).merge("a");
		order.verify(a).setSeq(0);
		order.verify(edao).afterInput(any(), eq(a));
		order.verify(dao).saveBean(a);
		order.verify(edao).afterMerge(any(), eq(a));
	}

	@Test
	void testSearchFiltersChildEntitiesByParent() {
		final var a = stored(Owned.class, "a");
		final var b = stored(Owned.class, "b");
		when(a.tryBinding("p")).thenReturn(true);
		doReturn(List.of(a, b)).when(dao).searchBeans(Owned.class, Map.of());
		final var edao = new OwnedChildDao();

		assertEquals(List.of(a), IuEntityDao.withParent("p", () -> list(edao.search(Map.of()))));
		assertEquals(List.of(), IuEntityDao.withParent("q", () -> list(edao.search(Map.of()))));
		assertEquals(List.of(), list(edao.search(Map.of())));
	}

	@Test
	void testSearchFiltersBeforeRangeAndAfterLoadOnlyForVisibleRows() {
		final var a = stored(Owned.class, "a");
		final var b = stored(Owned.class, "b");
		final var c = stored(Owned.class, "c");
		when(a.tryBinding("p")).thenReturn(true);
		when(c.tryBinding("p")).thenReturn(true);
		doReturn(List.of(a, b, c)).when(dao).searchBeans(Owned.class, Map.of());
		final var edao = spy(new OwnedChildDao());

		assertEquals(List.of(c), IuEntityDao.withParent("p", () -> list(edao.search(Map.of(), IuEntityDao.Search.range(1, null)))));
		verify(edao).afterLoad(c);
		verify(edao, never()).afterLoad(a);
		verify(edao, never()).afterLoad(b);
	}

	@Test
	void testSearchFiltersAndOrdersChildEntities() {
		final var a = stored(Owned.class, "a");
		final var b = stored(Owned.class, "b");
		when(a.tryBinding("p")).thenReturn(true);
		when(b.tryBinding("p")).thenReturn(true);
		doReturn(List.of(a, b)).when(dao).searchBeans(Owned.class, Map.of());
		final var edao = new OwnedChildDao() {
			@Override
			protected java.util.Comparator<? super Owned> order() {
				return (x, y) -> x == a ? 1 : -1;
			}
		};

		assertEquals(List.of(b, a), IuEntityDao.withParent("p", () -> list(edao.search(Map.of()))));
	}

}
