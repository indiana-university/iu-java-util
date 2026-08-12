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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

import edu.iu.IuRuntimeEnvironment;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class DaoSampleTest {

	private static DataSource DATA_SOURCE;

	@BeforeAll
	public static void setupClass() {
		final var ds = new PGSimpleDataSource();
		ds.setUrl("jdbc:postgresql://" + IuRuntimeEnvironment.env("postgres.host") + ":"
				+ IuRuntimeEnvironment.env("postgres.port") + "/postgres");
		ds.setUser(IuRuntimeEnvironment.env("postgres.user"));
		ds.setPassword(IuRuntimeEnvironment.env("postgres.password"));
		DATA_SOURCE = ds;
	}
	
	@BeforeEach
	public void setup() {
		IuTestLogger.allow("org.postgresql", Level.FINE);
	}

	@Test
	public void testLiquibaseSeedRowPresent() throws SQLException {
		try (var conn = DATA_SOURCE.getConnection();
				var stmt = conn.prepareStatement("SELECT label FROM dao_test.dao_sample WHERE label = ?")) {
			stmt.setString(1, "seed");
			try (var rs = stmt.executeQuery()) {
				assertTrue(rs.next(), "Expected Liquibase-managed seed row");
				assertEquals("seed", rs.getString("label"));
			}
		}
	}

	@Test
	public void testInsertAndRetrieveDynamicValue() throws SQLException {
		final var dynamicLabel = "dynamic-" + UUID.randomUUID();
		try (var conn = DATA_SOURCE.getConnection()) {
			conn.setAutoCommit(false);
			try {
				try (var insert = conn.prepareStatement("INSERT INTO dao_test.dao_sample (label) VALUES (?)")) {
					insert.setString(1, dynamicLabel);
					assertEquals(1, insert.executeUpdate());
				}
				try (var select = conn.prepareStatement("SELECT label FROM dao_test.dao_sample WHERE label = ?")) {
					select.setString(1, dynamicLabel);
					try (var rs = select.executeQuery()) {
						assertTrue(rs.next(), "Expected dynamically inserted row");
						assertEquals(dynamicLabel, rs.getString("label"));
					}
				}
			} finally {
				conn.rollback();
			}
		}
	}

}
