/**
 * Database connection pooling utilities.
 */
module iu.util.jdbc.pool {
	exports edu.iu.jdbc.pool;

	requires transitive iu.util;
	requires transitive java.sql;
	requires transitive java.logging;
}
