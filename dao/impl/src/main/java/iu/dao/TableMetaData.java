package iu.dao;

import edu.iu.IuIterable;
import jakarta.persistence.SecondaryTable;

/**
 * Holds table-level metadata for a primary or secondary table in a JPA entity
 * mapping.
 *
 * <p>
 * One instance is created for the primary table (alias {@code "a"}) and one for
 * each {@link SecondaryTable} annotation found on the entity class. Secondary
 * tables receive sequential aliases starting at {@code "b"}.
 * </p>
 *
 * <p>
 * After all column metadata for an entity has been resolved,
 * {@link #initializeJoinConditions(EntityMetaData)} must be called on each
 * secondary table to build the SQL equality conditions used in
 * {@code JOIN … ON} clauses.
 * </p>
 */
class TableMetaData {

	/** Unqualified table name as it appears in the database. */
	final String name;

	/**
	 * Schema-qualified table name ({@code schema.name}), or the bare {@link #name}
	 * when no schema is present.
	 */
	final String fullName;

	/**
	 * SQL alias assigned to this table in generated queries (e.g. {@code "a"},
	 * {@code "b"}).
	 */
	final String alias;

	/**
	 * {@code true} for the entity's primary table; {@code false} for secondary
	 * tables.
	 */
	final boolean primary;

	/**
	 * The {@link SecondaryTable} annotation that declared this table, or
	 * {@code null} for the primary table.
	 */
	final SecondaryTable secondaryTable;

	/**
	 * Ordered sequence of SQL equality conditions used to join this secondary table
	 * to the primary table. Empty for primary tables and for secondary tables whose
	 * {@link #initializeJoinConditions(EntityMetaData)} has not yet been called.
	 */
	Iterable<JoinCondition> joinConditions = IuIterable.empty();

	/**
	 * Constructs table metadata.
	 *
	 * @param name           unqualified table name; must not be {@code null}
	 * @param schema         schema name; {@code null} or blank produces an
	 *                       unqualified {@link #fullName}
	 * @param alias          SQL alias for this table in generated queries
	 * @param primary        {@code true} for the entity's primary table
	 * @param secondaryTable the declaring {@link SecondaryTable} annotation, or
	 *                       {@code null} for the primary table
	 */
	TableMetaData(String name, String schema, String alias, boolean primary, SecondaryTable secondaryTable) {
		this.name = name;
		this.fullName = DaoUtils.qualifyName(schema, name);
		this.alias = alias;
		this.primary = primary;
		this.secondaryTable = secondaryTable;
	}

	/**
	 * Initializes the join conditions for this secondary table.
	 *
	 * <p>
	 * This method is called once per secondary table during {@link EntityMetaData}
	 * construction, after all column metadata has been resolved. It is a no-op for
	 * primary tables.
	 * </p>
	 *
	 * <p>
	 * Condition derivation rules:
	 * </p>
	 * <ul>
	 * <li>When {@link SecondaryTable#pkJoinColumns()} is empty, one condition is
	 * created per {@link jakarta.persistence.Id}-mapped column in the entity, using
	 * the same column name on both sides.</li>
	 * <li>When {@link SecondaryTable#pkJoinColumns()} is non-empty, one condition
	 * is created per entry using
	 * {@link JoinCondition#JoinCondition(String, jakarta.persistence.PrimaryKeyJoinColumn, EntityMetaData)}.</li>
	 * </ul>
	 *
	 * @param entity owning entity metadata used to resolve primary-key columns
	 */
	void initializeJoinConditions(EntityMetaData entity) {
		if (primary)
			return;

		if (secondaryTable.pkJoinColumns().length == 0)
			joinConditions = IuIterable.map(entity.idColumns, column -> new JoinCondition(alias, column));
		else
			joinConditions = IuIterable.map(IuIterable.iter(secondaryTable.pkJoinColumns()),
					pkJoinColumn -> new JoinCondition(alias, pkJoinColumn, entity));
	}

}
