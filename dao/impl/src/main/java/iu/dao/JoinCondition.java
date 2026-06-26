package iu.dao;

import jakarta.persistence.PrimaryKeyJoinColumn;

/**
 * 
 */
class JoinCondition {
	private final String primaryAlias;
	private final String primaryColumn;
	private final String secondaryAlias;
	private final String secondaryColumn;

	JoinCondition(String secondaryAlias, ColumnMetaData column) {
		this.primaryAlias = column.table.alias;
		this.primaryColumn = column.columnName;
		this.secondaryAlias = secondaryAlias;
		this.secondaryColumn = column.columnName;
	}

	JoinCondition(String secondaryAlias, PrimaryKeyJoinColumn pkJoinColumn, EntityMetaData entity) {
		final var primaryLookup = DaoUtils.hasValue(pkJoinColumn.referencedColumnName()) //
				? pkJoinColumn.referencedColumnName() //
				: entity.idColumns.size() == 1 //
						? entity.idColumns.get(0).columnName
						: pkJoinColumn.name();
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

	void appendTo(StringBuilder sb) {
		sb.append(primaryAlias).append('.').append(primaryColumn).append(" = ").append(secondaryAlias).append('.')
				.append(secondaryColumn);
	}

}
