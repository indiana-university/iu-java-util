package iu.dao;

import java.beans.PropertyDescriptor;

import edu.iu.dao.SpaceForNull;
import edu.iu.dao.SqlColumn;
import jakarta.persistence.Column;
import jakarta.persistence.Id;

class ColumnMetaData {

	final PropertyDescriptor property;
	final String propertyName;
	final String columnName;
	final String sql;
	final String selectAlias;
	final Id id;
	final Column column;
	final SqlColumn sqlColumn;
	final boolean spaceForNull;
	final TableMetaData table;

	ColumnMetaData(PropertyDescriptor property, EntityMetaData entity) {
		this.property = property;
		this.propertyName = property.getName();
		final var readMethod = property.getReadMethod();
		this.id = readMethod.getAnnotation(Id.class);
		this.column = readMethod.getAnnotation(Column.class);
		this.sqlColumn = readMethod.getAnnotation(SqlColumn.class);
		this.spaceForNull = entity.spaceForNull || readMethod.isAnnotationPresent(SpaceForNull.class);
		if (column == null) {
			this.table = null;
			this.columnName = null;
			this.sql = sqlColumn.value();
			this.selectAlias = DaoUtils.camelToSnakeUpper(propertyName);
		} else {
			this.table = entity.resolveTable(column.table());
			this.columnName = hasValue(column.name()) ? column.name() : DaoUtils.camelToSnakeUpper(propertyName);
			this.sql = this.columnName;
			this.selectAlias = null;
		}
	}

	String reference() {
		return reference(null);
	}

	String reference(String aliasOverride) {
		final var sb = new StringBuilder();
		if (table != null)
			sb.append(aliasOverride == null ? table.alias : aliasOverride).append('.');
		sb.append(sql);
		return sb.toString();
	}

	boolean isMappedColumn() {
		return column != null;
	}

	boolean isPrimaryColumn() {
		return isMappedColumn() && table.primary;
	}
}
