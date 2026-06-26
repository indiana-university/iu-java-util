package iu.dao;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.SecondaryTable;

private static final class TableMetaData {
	
	private final String name;
	private final String fullName;
	private final String alias;
	private final boolean primary;
	private final SecondaryTable secondaryTable;
	private List<JoinCondition> joinConditions = List.of();

	private TableMetaData(String name, String schema, String alias, boolean primary, SecondaryTable secondaryTable) {
		this.name = name;
		this.fullName = qualifyName(schema, name);
		this.alias = alias;
		this.primary = primary;
		this.secondaryTable = secondaryTable;
	}

	private void initializeJoinConditions(EntityMetaData entity) {
		if (primary) {
			joinConditions = List.of();
			return;
		}

		final var conditions = new ArrayList<JoinCondition>();
		if (secondaryTable.pkJoinColumns().length == 0) {
			for (final var column : entity.idColumns)
				conditions.add(new JoinCondition(alias, column));
		} else {
			for (final var pkJoinColumn : secondaryTable.pkJoinColumns())
				conditions.add(new JoinCondition(alias, pkJoinColumn, entity));
		}
		joinConditions = List.copyOf(conditions);
	}
}
