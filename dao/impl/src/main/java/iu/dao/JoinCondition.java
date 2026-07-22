package iu.dao;

import jakarta.persistence.PrimaryKeyJoinColumn;

/**
 * Represents a single SQL equality condition used to join a secondary table to
 * the primary table in a JPA entity mapping.
 *
 * <p>
 * Instances are created during {@link EntityMetaData} initialization and
 * consumed by the SQL builder when constructing {@code JOIN ... ON} clauses.
 * The condition always takes the form:
 * </p>
 *
 * <pre>
 * primaryAlias.primaryColumn = secondaryAlias.secondaryColumn
 * </pre>
 */
class JoinCondition {
	private final String primaryAlias;
	private final String primaryColumn;
	private final String secondaryAlias;
	private final String secondaryColumn;

	/**
	 * Creates a join condition where the primary and secondary tables share the
	 * same column name.
	 *
	 * <p>
	 * Used when a {@link jakarta.persistence.SecondaryTable} declares no explicit
	 * {@link PrimaryKeyJoinColumn} entries; each {@code @Id}-mapped column of the
	 * entity becomes an implicit join column.
	 * </p>
	 *
	 * @param secondaryAlias alias assigned to the secondary table
	 * @param column         {@code @Id}-mapped column from the primary table whose
	 *                       name is shared with the secondary table
	 */
	JoinCondition(String secondaryAlias, ColumnMetaData column) {
		this.primaryAlias = column.table.alias;
		this.primaryColumn = column.columnName;
		this.secondaryAlias = secondaryAlias;
		this.secondaryColumn = column.columnName;
	}

	/**
	 * Creates a join condition from an explicit {@link PrimaryKeyJoinColumn}
	 * annotation.
	 *
	 * <p>
	 * The primary-side column is resolved in the following order:
	 * </p>
	 * <ol>
	 * <li>If {@link PrimaryKeyJoinColumn#referencedColumnName()} is non-blank, it
	 * is used as the primary column lookup key.</li>
	 * <li>Otherwise, if the entity has exactly one {@code @Id} column, that
	 * column's name is used.</li>
	 * <li>Otherwise, {@link PrimaryKeyJoinColumn#name()} is used as the fallback
	 * lookup key.</li>
	 * </ol>
	 *
	 * <p>
	 * If the resolved key matches a known column in the entity, that column's alias
	 * and name are used; otherwise the entity's primary-table alias and the raw
	 * lookup key are used directly.
	 * </p>
	 *
	 * @param secondaryAlias alias assigned to the secondary table
	 * @param pkJoinColumn   annotation that describes the join column mapping
	 * @param entity         metadata for the owning entity, used to resolve column
	 *                       aliases and names
	 */
	JoinCondition(String secondaryAlias, PrimaryKeyJoinColumn pkJoinColumn, EntityMetaData entity) {
		final String primaryLookup;
		if (DaoUtils.hasValue(pkJoinColumn.referencedColumnName()))
			primaryLookup = pkJoinColumn.referencedColumnName();
		else {
			final var i = entity.idColumns.iterator();
			String singlePkColumnName;
			if (i.hasNext() //
					&& DaoUtils.hasValue(singlePkColumnName = i.next().columnName) //
					&& !i.hasNext())
				primaryLookup = singlePkColumnName;
			else
				primaryLookup = pkJoinColumn.name();
		}

		final var column = entity.columnsByNormalizedColumn.get(DaoUtils.normalizeName(primaryLookup));
		if (column == null) {
			this.primaryAlias = entity.primaryTable.alias;
			this.primaryColumn = primaryLookup;
		} else {
			this.primaryAlias = column.table.alias;
			this.primaryColumn = column.columnName;
		}
		this.secondaryAlias = secondaryAlias;
		this.secondaryColumn = pkJoinColumn.name();
	}

	/**
	 * Appends the SQL equality condition to the given {@link StringBuilder}.
	 *
	 * <p>
	 * Produces output of the form:
	 * {@code primaryAlias.primaryColumn = secondaryAlias.secondaryColumn}.
	 * </p>
	 *
	 * @param sb target {@link StringBuilder} to append to
	 */
	void appendTo(StringBuilder sb) {
		sb.append(primaryAlias).append('.').append(primaryColumn).append(" = ").append(secondaryAlias).append('.')
				.append(secondaryColumn);
	}

}
