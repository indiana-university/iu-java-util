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
package edu.iu.dao.spi;

import javax.sql.DataSource;

import edu.iu.dao.IuDao;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionSynchronizationRegistry;

/**
 * Creates {@link IuDao} instances on behalf of
 * {@link IuDao#of(DataSource, TransactionManager, TransactionSynchronizationRegistry)}.
 *
 * <p>
 * Providers are discovered with {@link java.util.ServiceLoader}, so an
 * implementation needs a public no-argument constructor and must be declared by
 * its module. Because the loader may share one provider instance across every
 * caller, implementations must be stateless and thread-safe; per-DAO state
 * belongs on the object returned by
 * {@link #create(DataSource, TransactionManager, TransactionSynchronizationRegistry)}.
 * </p>
 */
public interface IuDaoSpi {

	/**
	 * Creates a DAO backed by the supplied infrastructure.
	 *
	 * <p>
	 * Arguments have already been checked for {@code null} by
	 * {@link IuDao#of(DataSource, TransactionManager, TransactionSynchronizationRegistry)}.
	 * </p>
	 *
	 * @param dataSource                         JDBC source the DAO will use for
	 *                                           every operation
	 * @param transactionManager                 transaction manager associated with
	 *                                           {@code dataSource}
	 * @param transactionSynchronizationRegistry registry in which the DAO stores
	 *                                           transaction-scoped read caches
	 * @return a new DAO instance
	 */
	IuDao create(DataSource dataSource, TransactionManager transactionManager,
			TransactionSynchronizationRegistry transactionSynchronizationRegistry);
}
