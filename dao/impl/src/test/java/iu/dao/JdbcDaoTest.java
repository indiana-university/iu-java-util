/* Copyright © 2026 Indiana University. BSD 3-Clause License. */
package iu.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.beans.Introspector;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Date;
import java.sql.Time;
import java.io.StringReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import edu.iu.dao.IuDao;
import edu.iu.dao.IuSqlBuilder;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.NonUniqueResultException;
import jakarta.transaction.Status;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionSynchronizationRegistry;

/** Unit tests for the JDBC DAO using JDBC interface doubles. */
@SuppressWarnings("javadoc")
public class JdbcDaoTest {

	private enum Choice { YES }

	public static class Values {
		private String string;
		private byte[] bytes;
		private Reader reader;
		private Date date;
		private Time time;
		private Timestamp timestamp;
		private Choice choice;
		private byte byteValue;
		private short shortValue;
		private int intValue;
		private long longValue;
		private float floatValue;
		private double doubleValue;
		private boolean booleanValue;
		private char charValue;
		public void setString(String value) { string = value; }
		public void setBytes(byte[] value) { bytes = value; }
		public void setReader(Reader value) { reader = value; }
		public void setDate(Date value) { date = value; }
		public void setTime(Time value) { time = value; }
		public void setTimestamp(Timestamp value) { timestamp = value; }
		public void setChoice(Choice value) { choice = value; }
		public void setByteValue(byte value) { byteValue = value; }
		public void setShortValue(short value) { shortValue = value; }
		public void setIntValue(int value) { intValue = value; }
		public void setLongValue(long value) { longValue = value; }
		public void setFloatValue(float value) { floatValue = value; }
		public void setDoubleValue(double value) { doubleValue = value; }
		public void setBooleanValue(boolean value) { booleanValue = value; }
		public void setCharValue(char value) { charValue = value; }
	}

	private static final class PrivateBean {
		private PrivateBean() { }
	}

	/** Has no no-argument constructor, so it cannot back a query result. */
	public static class NoDefaultConstructor {
		public NoDefaultConstructor(String unused) {
		}
	}

	public static class ThrowingBean {
		public void setValue(String value) { throw new IllegalStateException(value); }
	}

	public static class Bean {
		private String firstName;
		private int age;
		public String getFirstName() { return firstName; }
		public void setFirstName(String firstName) { this.firstName = firstName; }
		public int getAge() { return age; }
		public void setAge(int age) { this.age = age; }
	}

	@Test
	public void testFactoryStatementQueryMetadataAndCrud() {
		final var jdbc = new Jdbc();
		final var registry = registry();
		final var dao = new JdbcDao(jdbc.dataSource(), transactionManager(Status.STATUS_NO_TRANSACTION), registry,
				builder());

		assertEquals(1, dao.getStatement("update t set a=?", List.of("a")).execute());
		assertEquals("a", jdbc.bound.get(1));
		assertEquals("factory", dao.getFactoryQuery(rs -> "factory", "select 1").getSingleResult());
		assertEquals(2, dao.getQuery(Bean.class, "select first_name, age").getResults().size());
		final var page = dao.getQuery(Bean.class, "select first_name, age");
		assertEquals(1, page.getResults(1).size());
		assertEquals(1, page.getResults(1).size());
		page.close();
		assertEquals("Ada", dao.getQuery(Bean.class, "select first_name, age").getFirstRecord().getFirstName());
		assertThrows(EntityNotFoundException.class, () -> dao.getQuery(Bean.class, "empty").getSingleResult());
		assertThrows(NonUniqueResultException.class, () -> dao.getQuery(Bean.class, "select first_name, age").getSingleResult());
		assertEquals(2, dao.getQuery(Bean.class, "select first_name, age").getResultStream().count());

		final var table = dao.getTableDefinition("thing");
		assertEquals(null, table.getTableCat());
		assertEquals(null, table.getTableSchem());
		assertEquals("THING", table.getTableName());
		assertEquals("TABLE", table.getTableType());
		final var column = table.getColumns().iterator().next();
		assertEquals("ID", column.getColumnName());
		assertEquals(4, column.getDataType());
		assertEquals("INTEGER", column.getTypeName());
		assertEquals(8, column.getColumnSize());
		assertEquals(0, column.getDecimalDigits());
		assertEquals(10, column.getNumPrecRadix());
		assertThrows(EntityNotFoundException.class, () -> dao.getTableDefinition("missing"));

		final var bean = new Bean();
		bean.setFirstName("Grace");
		dao.updateBean(bean);
		dao.saveBean(bean);
		dao.deleteBean(bean);
		dao.insertBeans(List.of(bean));
		dao.updateBeans(List.of(bean));
		dao.saveBeans(List.of(bean));
		assertTrue(jdbc.closed.get() > 0);
	}

	@Test
	public void testSearchCacheLoadAndValidation() {
		final var jdbc = new Jdbc();
		final var resources = new HashMap<Object, Object>();
		final var dao = new JdbcDao(jdbc.dataSource(), transactionManager(Status.STATUS_ACTIVE), registry(resources), builder());
		final var params = Map.of("id", 1);
		assertEquals(1, dao.searchBeans(Bean.class, params, false, 1).size());
		final var prepared = jdbc.prepared.get();
		assertEquals(1, dao.searchBeans(Bean.class, params, true, 1).size());
		assertEquals(prepared, jdbc.prepared.get());
		assertEquals("Ada", dao.loadBean(Bean.class, params).getFirstName());
		assertEquals("Ada", dao.loadBean(Bean.class, params).getFirstName());
		assertEquals(1, dao.searchBeans(Bean.class, params, false, 0).size());
		dao.clear(Values.class);
		dao.clear(Bean.class);
		dao.clear();
		assertThrows(IllegalArgumentException.class, () -> dao.searchBeans(Bean.class, params, false, -1));
		assertThrows(IllegalArgumentException.class, () -> dao.getQuery(Bean.class, "select").getResults(0));
		// A class with no no-argument constructor cannot back a query result.
		assertThrows(IllegalArgumentException.class,
				() -> dao.getQuery(NoDefaultConstructor.class, "select first_name, age").getResults());
	}

	@Test
	public void testSpiFactory() {
		final var jdbc = new Jdbc();
		final IuDao dao = IuDao.of(jdbc.dataSource(), transactionManager(Status.STATUS_NO_TRANSACTION), registry());
		assertInstanceOf(JdbcDao.class, dao);
		assertThrows(NullPointerException.class, () -> IuDao.of(null, transactionManager(0), registry()));
	}

	@Test
	public void testEveryQueryOverloadAndInfrastructureFailure() {
		final var jdbc = new Jdbc();
		final var dao = new JdbcDao(jdbc.dataSource(), transactionManager(Status.STATUS_NO_TRANSACTION), registry(), builder());
		assertEquals(1, dao.getBeanQuery(Bean.class, List.of()).getResults().size());
		assertEquals(1, dao.getBeanQuery(Bean.class, List.of(), List.of("first_name"), List.of()).getResults().size());
		assertEquals(1, dao.getLockingBeanQuery(Bean.class, List.of(), 1, List.of()).getResults().size());
		assertEquals(1, dao.getLockingBeanQuery(Bean.class, List.of(), List.of("first_name"), 1, List.of()).getResults().size());
		final var bean = new Bean();
		assertEquals(1, dao.getBeanUpdate(bean, () -> bean).execute());
		assertEquals(1, dao.getBeanUpdate(bean, null).execute());
		assertEquals(1, dao.getStatement("update", null).execute());
		new JdbcDao(jdbc.dataSource(), transactionManager(0), registry());
		// A non-public no-argument constructor is still usable.
		assertInstanceOf(PrivateBean.class,
				dao.getQuery(PrivateBean.class, "select first_name, age").getFirstRecord());
		assertThrows(IllegalStateException.class, () -> new JdbcDao(failingDataSource(), transactionManager(0), registry(), builder())
				.getStatement("select").getPreparedStatement());
		assertThrows(IllegalStateException.class, () -> new JdbcDao(jdbc.dataSource(), throwingTransactionManager(), registry(), builder()).clear());
		assertThrows(IllegalStateException.class, () -> new JdbcDao(failingDataSource(), transactionManager(0), registry(), builder()).getTableDefinition("t"));
		assertThrows(NullPointerException.class, () -> dao.getBeanQuery(Bean.class, (Map<String, ?>) null));
		// A null entity must fail before the cache-eviction finally block, which would
		// otherwise replace the failure with its own NullPointerException.
		assertEquals("bean", assertThrows(NullPointerException.class, () -> dao.updateBean(null)).getMessage());
		assertEquals("bean", assertThrows(NullPointerException.class, () -> dao.deleteBean(null)).getMessage());
		assertEquals("SQL (1 args): update t set a=?", dao.getStatement("update t set a=?", List.of("a")).toString());
		assertEquals("Ada", new JdbcDao(jdbc.dataSource(), transactionManager(Status.STATUS_ACTIVE), registry(new HashMap<>()), builder())
				.loadBean(Bean.class, Map.of("id", 1)).getFirstName());
		assertEquals(1, dao.searchBeans(Bean.class, Map.of("id", 1), false, 1).size());
	}

	@Test
	public void testPrivateJdbcConversionAndCleanupBranches() throws Throwable {
		final var jdbc = new Jdbc();
		final var statement = jdbc.statement("select");
		invoke("prepare", new Class<?>[] { Connection.class, String.class, Iterable.class }, jdbc.connection(), "select",
				List.of(new char[] { 'a' }, new StringReader("b"), new byte[] { 1 }, 4));
		assertEquals(4, jdbc.bound.size());
		invoke("closeQuietly", new Class<?>[] { AutoCloseable.class }, (AutoCloseable) () -> { throw new Exception(); });
		invoke("closeQuietly", new Class<?>[] { AutoCloseable.class }, new Object[] { null });

		final var rs = valueResultSet();
		assertEquals("s", invoke("columnValue", new Class<?>[] { ResultSet.class, int.class, Class.class }, rs, 1, String.class));
		assertEquals(2, ((byte[]) invoke("columnValue", new Class<?>[] { ResultSet.class, int.class, Class.class }, rs, 1, byte[].class)).length);
		assertInstanceOf(Reader.class, invoke("columnValue", new Class<?>[] { ResultSet.class, int.class, Class.class }, rs, 1, Reader.class));
		assertInstanceOf(Date.class, invoke("columnValue", new Class<?>[] { ResultSet.class, int.class, Class.class }, rs, 1, Date.class));
		assertInstanceOf(Time.class, invoke("columnValue", new Class<?>[] { ResultSet.class, int.class, Class.class }, rs, 1, Time.class));
		assertInstanceOf(Timestamp.class, invoke("columnValue", new Class<?>[] { ResultSet.class, int.class, Class.class }, rs, 1, Timestamp.class));
		assertInstanceOf(Timestamp.class, invoke("columnValue", new Class<?>[] { ResultSet.class, int.class, Class.class }, rs, 1, java.util.Date.class));
		assertEquals(Choice.YES, invoke("columnValue", new Class<?>[] { ResultSet.class, int.class, Class.class }, stringValueResultSet("YES"), 1, Choice.class));
		assertEquals((byte) 4, invoke("columnValue", new Class<?>[] { ResultSet.class, int.class, Class.class }, rs, 1, byte.class));
		assertEquals((short) 4, invoke("columnValue", new Class<?>[] { ResultSet.class, int.class, Class.class }, rs, 1, short.class));
		assertEquals(4, invoke("columnValue", new Class<?>[] { ResultSet.class, int.class, Class.class }, rs, 1, int.class));
		assertEquals(4L, invoke("columnValue", new Class<?>[] { ResultSet.class, int.class, Class.class }, rs, 1, long.class));
		assertEquals(4f, invoke("columnValue", new Class<?>[] { ResultSet.class, int.class, Class.class }, rs, 1, float.class));
		assertEquals(4d, invoke("columnValue", new Class<?>[] { ResultSet.class, int.class, Class.class }, rs, 1, double.class));
		assertEquals(4, invoke("columnValue", new Class<?>[] { ResultSet.class, int.class, Class.class }, rs, 1, Number.class));
		assertEquals(4, invoke("columnValue", new Class<?>[] { ResultSet.class, int.class, Class.class }, rs, 1, Integer.class));
		assertEquals(Boolean.TRUE, invoke("columnValue", new Class<?>[] { ResultSet.class, int.class, Class.class }, stringValueResultSet("true"), 1, boolean.class));
		assertEquals(Boolean.TRUE, invoke("columnValue", new Class<?>[] { ResultSet.class, int.class, Class.class }, booleanValueResultSet(), 1, boolean.class));
		assertEquals(Boolean.TRUE, invoke("columnValue", new Class<?>[] { ResultSet.class, int.class, Class.class }, stringValueResultSet("true"), 1, Boolean.class));
		assertEquals('x', invoke("columnValue", new Class<?>[] { ResultSet.class, int.class, Class.class }, stringValueResultSet("x"), 1, char.class));
		assertEquals(null, invoke("columnValue", new Class<?>[] { ResultSet.class, int.class, Class.class }, stringValueResultSet(""), 1, char.class));
		assertEquals('x', invoke("columnValue", new Class<?>[] { ResultSet.class, int.class, Class.class }, stringValueResultSet("x"), 1, Character.class));
		assertEquals("x", invoke("columnValue", new Class<?>[] { ResultSet.class, int.class, Class.class }, stringValueResultSet("x"), 1, Object.class));
		assertEquals("x", invoke("columnValue", new Class<?>[] { ResultSet.class, int.class, Class.class }, stringValueResultSet("x"), 1, Bean.class));
		assertEquals(null, invoke("columnValue", new Class<?>[] { ResultSet.class, int.class, Class.class }, nullValueResultSet(), 1, String.class));
		assertEquals(null, invoke("columnValue", new Class<?>[] { ResultSet.class, int.class, Class.class }, nullValueResultSet(), 1, int.class));
		for (final var numberType : List.of(Byte.class, Short.class, Integer.class, Long.class, Float.class, Double.class, Number.class))
			assertInstanceOf(Number.class, invoke("number", new Class<?>[] { Class.class, Number.class }, numberType, Integer.valueOf(4)));
		assertThrows(EntityNotFoundException.class, () -> invoke("assertExactlyOne", new Class<?>[] { int.class, String.class, Object.class }, 0, "x", "b"));
		assertThrows(NonUniqueResultException.class, () -> invoke("assertExactlyOne", new Class<?>[] { int.class, String.class, Object.class }, 2, "x", "b"));
		// Row mapping failure surfaces as IllegalStateException, and a column matching
		// neither a mapped nor a lexically similar property is ignored.
		assertThrows(IllegalStateException.class, () -> new JdbcDao(dataSourceReturning(metadataFailingResultSet()),
				transactionManager(0), registry(), builder()).getQuery(Bean.class, "select").getResults());
		final var unmapped = new JdbcDao(dataSourceReturning(jdbc.resultSet(List.of(Map.of("UNKNOWN", "x")))),
				transactionManager(0), registry(), builder()).getQuery(Bean.class, "select").getResults();
		assertEquals(1, unmapped.size());
		assertEquals(null, unmapped.get(0).getFirstName());

		final var property = java.util.Arrays.stream(Introspector.getBeanInfo(Bean.class).getPropertyDescriptors())
				.filter(p -> "age".equals(p.getName())).findFirst().orElseThrow();
		invoke("setProperty", new Class<?>[] { Object.class, java.beans.PropertyDescriptor.class, Object.class }, new Bean(), property, null);
		final var throwing = java.util.Arrays.stream(Introspector.getBeanInfo(ThrowingBean.class).getPropertyDescriptors())
				.filter(p -> "value".equals(p.getName())).findFirst().orElseThrow();
		assertThrows(IllegalArgumentException.class, () -> invoke("setProperty", new Class<?>[] { Object.class, java.beans.PropertyDescriptor.class, Object.class }, new ThrowingBean(), throwing, "x"));

		jdbc.updateAbsent = true;
		new JdbcDao(jdbc.dataSource(), transactionManager(0), registry(), builder()).saveBean(new Bean());
		jdbc.updateAbsent = false;
		jdbc.updateCount = 2;
		assertThrows(NonUniqueResultException.class, () -> new JdbcDao(jdbc.dataSource(), transactionManager(0), registry(), builder()).updateBean(new Bean()));
		assertDoesNotThrow(() -> new JdbcDao(jdbc.dataSource(), transactionManager(0), registry(), unchangedBuilder()).updateBean(new Bean()));
	}

	/**
	 * Entity whose key column name bears no lexical resemblance to its property
	 * name, so it can only be populated through the {@code @Column} mapping.
	 */
	@Entity
	@Table(name = "person", schema = "s")
	public static class Person {
		private String employeeId;
		private String label;

		@Id
		@Column(name = "EMPLID")
		public String getEmployeeId() {
			return employeeId;
		}

		public void setEmployeeId(String employeeId) {
			this.employeeId = employeeId;
		}

		@Column
		public String getLabel() {
			return label;
		}

		public void setLabel(String label) {
			this.label = label;
		}
	}

	/**
	 * Interface-typed entity, exercising mapped getters, getters for columns the
	 * query does not select, a default method, and methods that are not getters.
	 */
	@Entity
	@Table(name = "person", schema = "s")
	public interface PersonView {
		@Id
		@Column(name = "EMPLID")
		String getEmployeeId();

		@Column
		String getLabel();

		String getMissing();

		int getCount();

		boolean isActive();

		String isNotAGetter();

		String value();

		String id();

		void setLabel(String label);

		default String describe() {
			return "person:" + getEmployeeId();
		}
	}

	private static IuDao daoOver(Map<String, Object> row) {
		final var jdbc = new Jdbc();
		return new JdbcDao(dataSourceReturning(jdbc.resultSet(List.of(row))),
				transactionManager(Status.STATUS_NO_TRANSACTION), registry());
	}

	private static Map<String, Object> personRow(String employeeId, String label) {
		final var row = new LinkedHashMap<String, Object>();
		row.put("EMPLID", employeeId);
		row.put("LABEL", label);
		return row;
	}

	@Test
	public void testInterfaceEntityIsAnImmutableViewOfItsRow() {
		final var view = daoOver(personRow("0000123", "seed")).getBeanQuery(PersonView.class, List.of())
				.getSingleResult();

		// Mapped columns answer from the snapshot, which outlives the closed cursor.
		assertEquals("0000123", view.getEmployeeId());
		assertEquals("seed", view.getLabel());
		assertEquals("person:0000123", view.describe());

		// Columns the query did not select answer with the return type's zero value.
		assertEquals(null, view.getMissing());
		assertEquals(0, view.getCount());
		assertEquals(false, view.isActive());

		// A read-only view cannot honor anything that is not a getter.
		assertThrows(UnsupportedOperationException.class, view::id);
		assertThrows(UnsupportedOperationException.class, view::value);
		assertThrows(UnsupportedOperationException.class, view::isNotAGetter);
		assertThrows(UnsupportedOperationException.class, () -> view.setLabel("other"));
	}

	@Test
	public void testInterfaceEntityComparesByResolvedValues() {
		final var view = daoOver(personRow("0000123", "seed")).getBeanQuery(PersonView.class, List.of())
				.getSingleResult();
		final var same = daoOver(personRow("0000123", "seed")).getBeanQuery(PersonView.class, List.of())
				.getSingleResult();
		final var other = daoOver(personRow("0000456", "seed")).getBeanQuery(PersonView.class, List.of())
				.getSingleResult();

		assertEquals("PersonView{employeeId=0000123, label=seed}", view.toString());
		assertEquals(view.hashCode(), same.hashCode());
		assertEquals(view, same);
		assertNotEquals(view, other);
		assertNotEquals(view, null);
		assertNotEquals(view, "not a proxy");
		// A proxy backed by some other handler is never equal.
		assertNotEquals(view, registry());
	}

	@Test
	public void testMappedColumnNamesPopulateTheirProperties() {
		// The default SQL builder, so that column resolution runs against real entity
		// metadata rather than a stub.
		final var person = daoOver(personRow("0000123", "seed")).getBeanQuery(Person.class, List.of())
				.getSingleResult();

		assertEquals("0000123", person.getEmployeeId());
		assertEquals("seed", person.getLabel());
	}

	@Test
	public void testCursorEdgesAndJdbcFailuresSurfaceAsIllegalState() {
		final var jdbc = new Jdbc();
		final var dao = new JdbcDao(jdbc.dataSource(), transactionManager(0), registry(), builder());

		assertEquals(List.of("a", 1), List.copyOf((List<?>) dao.getStatement("update", List.of("a", 1)).getArguments()));

		// A short final page and an exhausted stream are normal cursor outcomes.
		try (var page = dao.getQuery(Bean.class, "select first_name, age")) {
			assertEquals(2, page.getResults(5).size());
			assertEquals(0, page.getResults(5).size());
		}
		assertEquals(0, dao.getQuery(Bean.class, "empty").getResultStream().count());
		assertThrows(EntityNotFoundException.class, () -> dao.getQuery(Bean.class, "empty").getFirstRecord());

		// Every driver failure is reported as IllegalStateException, whether it occurs
		// on execution, on the first fetch, or partway through a cursor.
		assertThrows(IllegalStateException.class,
				() -> new JdbcDao(dataSourceThrowingOn("executeUpdate"), transactionManager(0), registry(), builder())
						.getStatement("update").execute());
		assertThrows(IllegalStateException.class,
				() -> new JdbcDao(dataSourceThrowingOn("executeQuery"), transactionManager(0), registry(), builder())
						.getQuery(Bean.class, "select").getResults());
		assertThrows(IllegalStateException.class, () -> new JdbcDao(dataSourceReturning(failingCursor(0)),
				transactionManager(0), registry(), builder()).getQuery(Bean.class, "select").getResults());
		assertThrows(IllegalStateException.class, () -> new JdbcDao(dataSourceReturning(failingCursor(1)),
				transactionManager(0), registry(), builder()).getQuery(Bean.class, "select").getSingleResult());
	}

	/** JDBC chain whose statements fail on the named method. */
	private static DataSource dataSourceThrowingOn(String failing) {
		final var statement = proxy(PreparedStatement.class, (method, args) -> {
			if (failing.equals(method.getName()))
				throw new SQLException();
			return null;
		});
		final var connection = proxy(Connection.class,
				(method, args) -> "prepareStatement".equals(method.getName()) ? statement : null);
		return proxy(DataSource.class, (method, args) -> "getConnection".equals(method.getName()) ? connection : null);
	}

	/** Cursor yielding {@code rows} rows, then failing instead of reporting the end. */
	private static ResultSet failingCursor(int rows) {
		final var read = new AtomicInteger();
		return proxy(ResultSet.class, (method, args) -> switch (method.getName()) {
		case "next" -> {
			if (read.getAndIncrement() >= rows)
				throw new SQLException();
			yield true;
		}
		case "getMetaData" -> proxy(ResultSetMetaData.class, (m, a) -> 0);
		default -> null;
		});
	}

	private static Object invoke(String name, Class<?>[] parameterTypes, Object... args) throws Throwable {
		final Method method = JdbcDao.class.getDeclaredMethod(name, parameterTypes);
		method.setAccessible(true);
		try {
			return method.invoke(null, args);
		} catch (InvocationTargetException e) {
			throw e.getCause();
		}
	}

	private static DataSource failingDataSource() {
		return proxy(DataSource.class, (method, args) -> { if ("getConnection".equals(method.getName())) throw new SQLException(); return null; });
	}

	private static TransactionManager throwingTransactionManager() {
		return proxy(TransactionManager.class, (method, args) -> { if ("getStatus".equals(method.getName())) throw new jakarta.transaction.SystemException(); return null; });
	}

	private static ResultSet nullValueResultSet() {
		return proxy(ResultSet.class, (method, args) -> "getObject".equals(method.getName()) ? null : null);
	}

	private static ResultSet stringValueResultSet(String value) {
		return proxy(ResultSet.class, (method, args) -> "getObject".equals(method.getName()) ? value : null);
	}

	private static ResultSet booleanValueResultSet() {
		return proxy(ResultSet.class, (method, args) -> "getObject".equals(method.getName()) ? Boolean.TRUE : null);
	}

	/** Yields one row whose metadata cannot be read, failing column resolution. */
	private static ResultSet metadataFailingResultSet() {
		return proxy(ResultSet.class, (method, args) -> switch (method.getName()) {
		case "next" -> true;
		case "getMetaData" -> throw new SQLException();
		default -> null;
		});
	}

	/** Minimal JDBC chain whose every query returns the supplied result set. */
	private static DataSource dataSourceReturning(ResultSet resultSet) {
		final var statement = proxy(PreparedStatement.class,
				(method, args) -> "executeQuery".equals(method.getName()) ? resultSet : null);
		final var connection = proxy(Connection.class,
				(method, args) -> "prepareStatement".equals(method.getName()) ? statement : null);
		return proxy(DataSource.class, (method, args) -> "getConnection".equals(method.getName()) ? connection : null);
	}

	private static ResultSet valueResultSet() {
		final var now = new Timestamp(0L);
		return proxy(ResultSet.class, (method, args) -> switch (method.getName()) {
		case "getString" -> args[0] instanceof Integer ? "s" : "x";
		case "getBytes" -> new byte[] { 1, 2 };
		case "getCharacterStream" -> new StringReader("r");
		case "getDate" -> new Date(0L);
		case "getTime" -> new Time(0L);
		case "getTimestamp" -> now;
		case "getObject" -> Integer.valueOf(4);
		default -> null;
		});
	}

	private static IuSqlBuilder builder() {
		return proxy(IuSqlBuilder.class, (method, args) -> switch (method.getName()) {
		case "getSelectStatement", "getOrderedSelectStatement" -> "bean";
		case "getBeanKeyCriteria" -> List.of("id = ?");
		case "getBeanKeyArgs" -> List.of(1);
		case "getUpdateProperties" -> {
			if (args.length == 2 && args[1] != null) ((java.util.function.Supplier<?>) args[1]).get();
			yield List.of("firstName");
		}
		case "getUpdateStatement" -> "update thing set first_name=?";
		case "getUpdateArguments" -> List.of("name", 1);
		case "getInsertStatement" -> "insert into thing values(?)";
		case "getInsertArguments" -> List.of("name");
		case "getDeleteStatement" -> "delete from thing where id=?";
		case "getDeleteArguments" -> List.of(1);
		// Mapped only for FIRST_NAME, so that both the @Column-mapped and the
		// lexical fallback resolution paths are exercised by one query.
		case "getPropertyNameFromBean" -> "FIRST_NAME".equals(args[1]) ? "firstName" : null;
		default -> throw new UnsupportedOperationException(method.getName());
		});
	}

	private static IuSqlBuilder unchangedBuilder() {
		return proxy(IuSqlBuilder.class, (method, args) -> {
			if ("getUpdateProperties".equals(method.getName())) return List.of();
			if ("getUpdateStatement".equals(method.getName())) throw new edu.iu.dao.IuSqlUnchangedException();
			throw new UnsupportedOperationException(method.getName());
		});
	}

	private static TransactionManager transactionManager(int status) {
		return proxy(TransactionManager.class, (method, args) -> "getStatus".equals(method.getName()) ? status : null);
	}

	private static TransactionSynchronizationRegistry registry() {
		return registry(new HashMap<>());
	}

	private static TransactionSynchronizationRegistry registry(Map<Object, Object> resources) {
		return proxy(TransactionSynchronizationRegistry.class, (method, args) -> switch (method.getName()) {
		case "getResource" -> resources.get(args[0]);
		case "putResource" -> resources.put(args[0], args[1]);
		default -> null;
		});
	}

	@FunctionalInterface
	private interface Call { Object call(java.lang.reflect.Method method, Object[] args) throws Throwable; }

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, Call call) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (p, method, args) -> {
			if (method.getDeclaringClass() == Object.class)
				return method.getName().equals("toString") ? type.getSimpleName() : null;
			final var result = call.call(method, args == null ? new Object[0] : args);
			if (result != null || !method.getReturnType().isPrimitive())
				return result;
			if (method.getReturnType() == boolean.class) return false;
			if (method.getReturnType() == int.class) return 0;
			if (method.getReturnType() == long.class) return 0L;
			return 0;
		});
	}

	private static final class Jdbc {
		private final AtomicInteger prepared = new AtomicInteger();
		private final AtomicInteger closed = new AtomicInteger();
		private final Map<Integer, Object> bound = new HashMap<>();
		private int updateCount = 1;
		private boolean updateAbsent;

		private DataSource dataSource() {
			return proxy(DataSource.class, (method, args) -> "getConnection".equals(method.getName()) ? connection() : null);
		}

		private Connection connection() {
			return proxy(Connection.class, (method, args) -> switch (method.getName()) {
			case "prepareStatement" -> statement((String) args[0]);
			case "getMetaData" -> metadata();
			case "close" -> { closed.incrementAndGet(); yield null; }
			default -> null;
			});
		}

		private PreparedStatement statement(String sql) {
			prepared.incrementAndGet();
			return proxy(PreparedStatement.class, (method, args) -> switch (method.getName()) {
			case "setObject", "setBytes", "setClob", "setCharacterStream" -> { bound.put((Integer) args[0], args[1]); yield null; }
			case "executeUpdate" -> updateAbsent && sql.startsWith("update") ? 0 : updateCount;
			case "executeQuery" -> resultSet(sql.contains("empty") ? List.of()
					: (sql.equals("bean") || sql.equals("select 1")) ? List.of(row("Ada", 37))
							: List.of(row("Ada", 37), row("Grace", 42)));
			case "close" -> { closed.incrementAndGet(); yield null; }
			default -> null;
			});
		}

		private DatabaseMetaData metadata() {
			return proxy(DatabaseMetaData.class, (method, args) -> switch (method.getName()) {
			case "getTables" -> resultSet("missing".equalsIgnoreCase((String) args[2]) ? List.of() : List.of(Map.of("TABLE_NAME", "THING", "TABLE_TYPE", "TABLE")));
			case "getColumns" -> resultSet(List.of(Map.of("COLUMN_NAME", "ID", "DATA_TYPE", 4, "TYPE_NAME", "INTEGER", "COLUMN_SIZE", 8, "DECIMAL_DIGITS", 0, "NUM_PREC_RADIX", 10)));
			default -> null;
			});
		}

		private static Map<String, Object> row(String name, int age) {
			final var row = new HashMap<String, Object>();
			row.put("FIRST_NAME", name); row.put("AGE", age); return row;
		}

		private ResultSet resultSet(List<Map<String, Object>> rows) {
			final var index = new AtomicInteger(-1);
			return proxy(ResultSet.class, (method, args) -> switch (method.getName()) {
			case "next" -> index.incrementAndGet() < rows.size();
			case "getMetaData" -> resultSetMetadata(rows.isEmpty() ? List.of("FIRST_NAME", "AGE") : new ArrayList<>(rows.get(0).keySet()));
			case "getObject" -> rows.get(index.get()).get(column(rows, args[0]));
			// Absent keys read as null rather than "null", so that metadata rows without
			// a catalog or schema behave the way a real driver's do.
			case "getString" -> java.util.Objects.toString(rows.get(index.get()).get(column(rows, args[0])), null);
			case "getInt" -> rows.get(index.get()).get(column(rows, args[0]));
			case "close" -> { closed.incrementAndGet(); yield null; }
			default -> null;
			});
		}

		private static String column(List<Map<String, Object>> rows, Object value) {
			if (value instanceof String name) return name;
			final var columns = rows.isEmpty() ? List.of("FIRST_NAME", "AGE") : new ArrayList<>(rows.get(0).keySet());
			return columns.get((Integer) value - 1);
		}

		private static ResultSetMetaData resultSetMetadata(List<String> columns) {
			return proxy(ResultSetMetaData.class, (method, args) -> switch (method.getName()) {
			case "getColumnCount" -> columns.size();
			case "getColumnLabel" -> columns.get((Integer) args[0] - 1);
			default -> null;
			});
		}
	}
}
