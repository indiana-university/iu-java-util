package edu.iu.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class SqlJoinTypeTest {

	@Test
	public void testJoinType() {
		@SqlJoinType
		class A {
		}
		assertEquals(SqlJoinType.Type.INNER, A.class.getAnnotation(SqlJoinType.class).value());
	}

}
