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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

import edu.iu.IuRuntimeEnvironment;
import edu.iu.dao.Distinct;
import edu.iu.dao.Filtered;
import edu.iu.dao.IuSqlBuilder;
import edu.iu.dao.SqlFilter;
import edu.iu.test.IuTestLogger;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.SecondaryTable;
import jakarta.persistence.Table;

@SuppressWarnings("javadoc")
public class IuSqlBuilderImplTest {

	private static DataSource DATA_SOURCE;
	private static final IuSqlBuilder SQL_BUILDER = new IuSqlBuilderImpl();

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
	public void testSelectFromSimpleEntity() throws SQLException {
		final var sql = SQL_BUILDER.getSelectStatement(ItemBean.class, List.of("a.id = 1"));
		try (var conn = DATA_SOURCE.getConnection(); var stmt = conn.prepareStatement(sql); var rs = stmt.executeQuery()) {
			assertTrue(rs.next());
			assertEquals(1L, rs.getLong("id"));
			assertEquals("ITEM1", rs.getString("code"));
			assertEquals("Seed Item", rs.getString("label"));
			assertTrue(rs.getBoolean("active"));
			assertFalse(rs.next());
		}
	}

	@Test
	public void testSelectWithWhereClause() throws SQLException {
		final var sql = SQL_BUILDER.getSelectStatement(ItemBean.class, List.of("a.code = ?"));
		try (var conn = DATA_SOURCE.getConnection(); var stmt = conn.prepareStatement(sql)) {
			stmt.setString(1, "ITEM2");
			try (var rs = stmt.executeQuery()) {
				assertTrue(rs.next());
				assertEquals(2L, rs.getLong("id"));
				assertEquals("Inactive Item", rs.getString("label"));
				assertFalse(rs.next());
			}
		}
	}

	@Test
	public void testInsertAndDelete() throws SQLException {
		final var item = new ItemBean(1001L, "ITEMX", "Inserted Item", true);
		try (var conn = DATA_SOURCE.getConnection()) {
			conn.setAutoCommit(false);
			try {
				executeUpdate(conn, SQL_BUILDER.getInsertStatement(ItemBean.class), SQL_BUILDER.getInsertArguments(item));
				assertEquals("Inserted Item", selectSingleLabel(conn, 1001L));
				executeUpdate(conn, SQL_BUILDER.getDeleteStatement(ItemBean.class), SQL_BUILDER.getDeleteArguments(item));
				assertFalse(exists(conn, "SELECT 1 FROM sql_builder_test.item WHERE id = ?", List.of(1001L)));
			} finally {
				conn.rollback();
			}
		}
	}

	@Test
	public void testUpdateStatement() throws SQLException {
		final var original = new ItemBean(1L, "ITEM1", "Seed Item", true);
		final var updated = new ItemBean(1L, "ITEM1", "Updated Label", false);
		final var properties = toList(SQL_BUILDER.getUpdateProperties(updated, original));
		try (var conn = DATA_SOURCE.getConnection()) {
			conn.setAutoCommit(false);
			try {
				executeUpdate(conn, SQL_BUILDER.getUpdateStatement(ItemBean.class, properties),
						SQL_BUILDER.getUpdateArguments(updated, properties));
				try (var stmt = conn.prepareStatement("SELECT label, active FROM sql_builder_test.item WHERE id = ?")) {
					stmt.setLong(1, 1L);
					try (var rs = stmt.executeQuery()) {
						assertTrue(rs.next());
						assertEquals("Updated Label", rs.getString("label"));
						assertFalse(rs.getBoolean("active"));
					}
				}
			} finally {
				conn.rollback();
			}
		}
	}

	@Test
	public void testSelectWithSecondaryTable() throws SQLException {
		final var sql = SQL_BUILDER.getSelectStatement(CatalogBean.class, List.of("a.id = 10"));
		try (var conn = DATA_SOURCE.getConnection(); var stmt = conn.prepareStatement(sql); var rs = stmt.executeQuery()) {
			assertTrue(rs.next());
			assertEquals("Catalog One", rs.getString("name"));
			assertEquals("Catalog One Description", rs.getString("description"));
			assertFalse(rs.next());
		}
	}

	@Test
	public void testEffectiveDateCriteria() throws SQLException {
		final var criteria = SQL_BUILDER.getEffectiveDateCriteria(PriceBean.class, "eff_date");
		final var sql = SQL_BUILDER.getSelectStatement(PriceBean.class, List.of("a.item_id = 1", criteria));
		try (var conn = DATA_SOURCE.getConnection(); var stmt = conn.prepareStatement(sql); var rs = stmt.executeQuery()) {
			assertTrue(rs.next());
			assertEquals(Date.valueOf("2024-06-01"), rs.getDate("eff_date"));
			assertEquals(new BigDecimal("12.50"), rs.getBigDecimal("amount"));
			assertFalse(rs.next());
		}
	}

	@Test
	public void testInCriteria() throws SQLException {
		final var literals = SQL_BUILDER.getLiterals(List.<Object>of(1L, 2L));
		final var criteria = SQL_BUILDER.getColumnMatchCriteria(ItemBean.class, "id", literals);
		final var sql = SQL_BUILDER.getSelectStatement(ItemBean.class, List.of(criteria));
		try (var conn = DATA_SOURCE.getConnection(); var stmt = conn.prepareStatement(sql); var rs = stmt.executeQuery()) {
			final var ids = new ArrayList<Long>();
			while (rs.next())
				ids.add(rs.getLong("id"));
			assertIterableEquals(List.of(1L, 2L), ids);
		}
	}

	@Test
	public void testMultipartKeyMatch() throws SQLException {
		final var criteria = SQL_BUILDER.getMultipartKeyListMatch(List.of("a.item_id", "a.eff_date"),
				List.of(toList(SQL_BUILDER.getLiterals(List.<Object>of(1L, Date.valueOf("2024-01-01")))),
						toList(SQL_BUILDER.getLiterals(List.<Object>of(2L, Date.valueOf("2024-01-01"))))));
		final var sql = SQL_BUILDER.getSelectStatement(PriceBean.class, List.of(criteria));
		try (var conn = DATA_SOURCE.getConnection(); var stmt = conn.prepareStatement(sql); var rs = stmt.executeQuery()) {
			int count = 0;
			while (rs.next())
				count++;
			assertEquals(2, count);
		}
	}

	@Test
	public void testGetUpdateProperties() {
		final var original = new ItemBean(1L, "ITEM1", "Seed Item", true);
		final var updated = new ItemBean(1L, "ITEM1", "Changed", true);
		assertIterableEquals(List.of("label"), toList(SQL_BUILDER.getUpdateProperties(updated, original)));
	}

	@Test
	public void testDistinctAnnotation() throws SQLException {
		final var sql = SQL_BUILDER.getSelectStatement(DistinctItemBean.class, List.of("a.id IN (1, 2)"));
		assertTrue(sql.startsWith("SELECT DISTINCT"));
		try (var conn = DATA_SOURCE.getConnection(); var stmt = conn.prepareStatement(sql); var rs = stmt.executeQuery()) {
			int count = 0;
			while (rs.next())
				count++;
			assertEquals(2, count);
		}
	}

	@Test
	public void testSqlFilterAnnotation() throws SQLException {
		final var sql = SQL_BUILDER.getSelectStatement(ActiveItemBean.class, List.of());
		try (var conn = DATA_SOURCE.getConnection(); var stmt = conn.prepareStatement(sql); var rs = stmt.executeQuery()) {
			int count = 0;
			while (rs.next()) {
				count++;
				assertTrue(rs.getBoolean("active"));
			}
			assertEquals(1, count);
		}
	}

	private static int executeUpdate(Connection conn, String sql, Iterable<?> args) throws SQLException {
		try (var stmt = conn.prepareStatement(sql)) {
			bind(stmt, args);
			return stmt.executeUpdate();
		}
	}

	private static void bind(PreparedStatement stmt, Iterable<?> args) throws SQLException {
		int i = 1;
		for (final var arg : args)
			stmt.setObject(i++, arg);
	}

	private static String selectSingleLabel(Connection conn, long id) throws SQLException {
		try (var stmt = conn.prepareStatement("SELECT label FROM sql_builder_test.item WHERE id = ?")) {
			stmt.setLong(1, id);
			try (var rs = stmt.executeQuery()) {
				assertTrue(rs.next());
				return rs.getString("label");
			}
		}
	}

	private static boolean exists(Connection conn, String sql, List<?> args) throws SQLException {
		try (var stmt = conn.prepareStatement(sql)) {
			bind(stmt, args);
			try (var rs = stmt.executeQuery()) {
				return rs.next();
			}
		}
	}

	private static <T> List<T> toList(Iterable<T> iterable) {
		final var list = new ArrayList<T>();
		for (final var value : iterable)
			list.add(value);
		return list;
	}

	@Entity
	@Table(name = "item", schema = "sql_builder_test")
	public static class ItemBean {
		private long id;
		private String code;
		private String label;
		private boolean active;

		public ItemBean() {
		}

		public ItemBean(long id, String code, String label, boolean active) {
			this.id = id;
			this.code = code;
			this.label = label;
			this.active = active;
		}

		@Id
		@Column(name = "id")
		public long getId() {
			return id;
		}

		public void setId(long id) {
			this.id = id;
		}

		@Column(name = "code")
		public String getCode() {
			return code;
		}

		public void setCode(String code) {
			this.code = code;
		}

		@Column(name = "label")
		public String getLabel() {
			return label;
		}

		public void setLabel(String label) {
			this.label = label;
		}

		@Column(name = "active")
		public boolean isActive() {
			return active;
		}

		public void setActive(boolean active) {
			this.active = active;
		}
	}

	@Entity
	@Table(name = "catalog", schema = "sql_builder_test")
	@SecondaryTable(name = "catalog_detail", schema = "sql_builder_test", pkJoinColumns = @PrimaryKeyJoinColumn(name = "catalog_id"))
	public static class CatalogBean {
		private long id;
		private String name;
		private String description;

		@Id
		@Column(name = "id")
		public long getId() {
			return id;
		}

		public void setId(long id) {
			this.id = id;
		}

		@Column(name = "name")
		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		@Column(name = "description", table = "catalog_detail")
		public String getDescription() {
			return description;
		}

		public void setDescription(String description) {
			this.description = description;
		}
	}

	@Entity
	@Table(name = "price", schema = "sql_builder_test")
	public static class PriceBean {
		private long itemId;
		private Date effDate;
		private BigDecimal amount;

		@Id
		@Column(name = "item_id")
		public long getItemId() {
			return itemId;
		}

		public void setItemId(long itemId) {
			this.itemId = itemId;
		}

		@Id
		@Column(name = "eff_date")
		public Date getEffDate() {
			return effDate;
		}

		public void setEffDate(Date effDate) {
			this.effDate = effDate;
		}

		@Column(name = "amount")
		public BigDecimal getAmount() {
			return amount;
		}

		public void setAmount(BigDecimal amount) {
			this.amount = amount;
		}
	}

	@Entity
	@Distinct
	@Table(name = "item", schema = "sql_builder_test")
	public static class DistinctItemBean extends ItemBean {
	}

	@Entity
	@Table(name = "item", schema = "sql_builder_test")
	@Filtered(filters = @SqlFilter(sql = "a.active = TRUE"))
	public static class ActiveItemBean extends ItemBean {
	}
}
