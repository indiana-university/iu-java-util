package edu.iu.jdbc.pool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@SuppressWarnings("javadoc")
@ExtendWith(TestDatabase.class)
public class DatabaseStabilityTest {

	@Test
	public void testValdationQuery() throws SQLException {
		final var pc = TestDatabase.dataSource.getPooledConnection();
		try (final var c = pc.getConnection(); //
				final var s = c.prepareStatement("select 1"); //
				final var rs = s.executeQuery()) {
			assertTrue(rs.next());
			assertEquals(1, rs.getInt(1));
			assertFalse(rs.next());
		} finally {
			pc.close();
		}
	}

}
