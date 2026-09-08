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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Function;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import edu.iu.dao.spi.IuDaoSpi;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionSynchronizationRegistry;

@SuppressWarnings("javadoc")
public class IuDaoTest {

	public static class Bean {
	}

	/**
	 * Runs {@code test} with {@link ServiceLoader#load(Class, ClassLoader)} stubbed
	 * to find the supplied providers.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static void withProviders(List<IuDaoSpi> providers, Runnable test) {
		final var serviceLoader = mock(ServiceLoader.class);
		when(serviceLoader.findFirst()).thenReturn(providers.stream().findFirst().map(Optional::of).orElse(Optional.empty()));
		try (var mockServiceLoader = mockStatic(ServiceLoader.class)) {
			mockServiceLoader.when(() -> ServiceLoader.load(IuDaoSpi.class, IuDaoSpi.class.getClassLoader()))
					.thenReturn(serviceLoader);
			test.run();
		}
	}

	@Test
	public void testOfDelegatesToInstalledProvider() {
		final var dataSource = mock(DataSource.class);
		final var transactionManager = mock(TransactionManager.class);
		final var registry = mock(TransactionSynchronizationRegistry.class);
		final var dao = mock(IuDao.class);
		final var provider = mock(IuDaoSpi.class);
		when(provider.create(dataSource, transactionManager, registry)).thenReturn(dao);

		withProviders(List.of(provider), () -> assertSame(dao, IuDao.of(dataSource, transactionManager, registry)));
		verify(provider).create(dataSource, transactionManager, registry);
	}

	@Test
	public void testOfRequiresAProvider() {
		final var dataSource = mock(DataSource.class);
		final var transactionManager = mock(TransactionManager.class);
		final var registry = mock(TransactionSynchronizationRegistry.class);
		withProviders(List.of(), () -> assertThrows(IllegalStateException.class,
				() -> IuDao.of(dataSource, transactionManager, registry)));
	}

	@Test
	public void testOfRejectsNullArguments() {
		final var dataSource = mock(DataSource.class);
		final var transactionManager = mock(TransactionManager.class);
		final var registry = mock(TransactionSynchronizationRegistry.class);
		assertThrows(NullPointerException.class, () -> IuDao.of(null, transactionManager, registry));
		assertThrows(NullPointerException.class, () -> IuDao.of(dataSource, null, registry));
		assertThrows(NullPointerException.class, () -> IuDao.of(dataSource, transactionManager, null));
	}

	@Test
	public void testStatementDefaultsToNoArguments() {
		final var dao = mock(IuDao.class, CALLS_REAL_METHODS);
		dao.getStatement("update t set a = 1");
		verify(dao).getStatement("update t set a = 1", Collections.emptyList());
	}

	@Test
	public void testBeanQueryDefaultsToNoArguments() {
		final var dao = mock(IuDao.class, CALLS_REAL_METHODS);
		dao.getBeanQuery(Bean.class, List.of("a = 1"));
		verify(dao).getBeanQuery(Bean.class, List.of("a = 1"), Collections.emptyList());
	}

	@Test
	public void testQueryDefaultsToNoArguments() {
		final var dao = mock(IuDao.class, CALLS_REAL_METHODS);
		dao.getQuery(Bean.class, "select 1");
		verify(dao).getQuery(Bean.class, "select 1", Collections.emptyList());
	}

	@Test
	public void testFactoryQueryDefaultsToNoArguments() {
		final var dao = mock(IuDao.class, CALLS_REAL_METHODS);
		final Function<ResultSet, Bean> factory = resultSet -> new Bean();
		dao.getFactoryQuery(factory, "select 1");
		verify(dao).getFactoryQuery(factory, "select 1", Collections.emptyList());
	}

	@Test
	public void testSearchDefaultsToUnobservedAndUnbounded() {
		final var dao = mock(IuDao.class, CALLS_REAL_METHODS);
		final Map<String, ?> idParams = Map.of("id", 1);
		dao.searchBeans(Bean.class, idParams);
		verify(dao).searchBeans(Bean.class, idParams, false, 0);
	}

	@Test
	public void testSearchWithObserveFlagIsUnbounded() {
		final var dao = mock(IuDao.class, CALLS_REAL_METHODS);
		final Map<String, ?> idParams = Map.of("id", 1);
		dao.searchBeans(Bean.class, idParams, true);
		verify(dao).searchBeans(Bean.class, idParams, true, 0);
	}

	@Test
	public void testSearchWithMaxResultsIsUnobserved() {
		final var dao = mock(IuDao.class, CALLS_REAL_METHODS);
		final Map<String, ?> idParams = Map.of("id", 1);
		dao.searchBeans(Bean.class, idParams, 5);
		verify(dao).searchBeans(Bean.class, idParams, false, 5);
	}
}
