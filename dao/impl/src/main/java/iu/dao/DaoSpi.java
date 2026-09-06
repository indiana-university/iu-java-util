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
package iu.dao;

import java.util.function.Supplier;

import javax.sql.DataSource;

import edu.iu.IuRefreshableCacheConfiguration;

import edu.iu.dao.IuDao;
import edu.iu.dao.spi.IuDaoSpi;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionSynchronizationRegistry;

/**
 * Default {@link IuDaoSpi} provider, creating {@link JdbcDao} instances backed by
 * {@link IuSqlBuilderImpl}.
 */
public final class DaoSpi implements IuDaoSpi {

	/**
	 * Default constructor, required to be public and no-argument so that
	 * {@link java.util.ServiceLoader} can instantiate this provider.
	 */
	public DaoSpi() {
	}

	@Override
	public IuDao create(DataSource dataSource, TransactionManager transactionManager,
			TransactionSynchronizationRegistry transactionSynchronizationRegistry) {
		return new JdbcDao(dataSource, transactionManager, transactionSynchronizationRegistry, new IuSqlBuilderImpl());
	}

	@Override
	public IuDao create(DataSource dataSource, TransactionManager transactionManager,
			TransactionSynchronizationRegistry transactionSynchronizationRegistry,
			Supplier<IuRefreshableCacheConfiguration> config) {
		// one builder, shared: the cache reads an entity's mapped key with the same
		// builder the delegate generates its statements with, so the key it publishes
		// a row under is the one a load of that row would be made with
		final var sqlBuilder = new IuSqlBuilderImpl();
		return new CachedDao(
				new JdbcDao(dataSource, transactionManager, transactionSynchronizationRegistry, sqlBuilder), sqlBuilder,
				transactionManager, transactionSynchronizationRegistry, config);
	}
}
