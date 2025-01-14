/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.connector.cassandra;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;

import com.datastax.oss.driver.api.core.metadata.schema.ColumnMetadata;
import com.datastax.oss.driver.api.core.metadata.schema.TableMetadata;

/**
 * Row-level data about the source event. Contains a map where the key is the table column
 * name and the value is the {@link CellData}.
 */
public class RowData implements KafkaRecord {
    private final Map<String, CellData> cellMap = new LinkedHashMap<>();

    public void addCell(CellData cellData) {
        this.cellMap.put(cellData.name, cellData);
    }

    public void removeCell(String columnName) {
        if (hasCell(columnName)) {
            cellMap.remove(columnName);
        }
    }

    public boolean hasCell(String columnName) {
        return cellMap.containsKey(columnName);
    }

    @Override
    public Struct record(Schema schema) {
        Struct struct = new Struct(schema);
        for (Field field : schema.fields()) {
            Schema cellSchema = KeyValueSchema.getFieldSchema(field.name(), schema);
            CellData cellData = cellMap.get(field.name());
            // only add the cell if it is not null
            if (cellData != null) {
                struct.put(field.name(), cellData.record(cellSchema));
            }
        }
        return struct;
    }

    public RowData copy() {
        RowData copy = new RowData();
        for (CellData cellData : cellMap.values()) {
            copy.addCell(cellData);
        }
        return copy;
    }

    /**
     * Assemble the Kafka connect {@link Schema} for the "after" field of the change event
     * based on the Cassandra table schema.
     * @param tm metadata of a table that contains the Cassandra table schema
     * @return a schema for the "after" field of a change event
     */
    static Schema rowSchema(TableMetadata tm) {
        SchemaBuilder schemaBuilder = SchemaBuilder.struct().name(Record.AFTER);
        for (ColumnMetadata cm : tm.getColumns().values()) {
            Schema optionalCellSchema = CellData.cellSchema(cm, true);
            if (optionalCellSchema != null) {
                schemaBuilder.field(cm.getName().toString(), optionalCellSchema);
            }
        }
        return schemaBuilder.build();
    }

    List<CellData> getPrimary() {
        return this.cellMap.values().stream().filter(CellData::isPrimary).collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return this.cellMap.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RowData rowData = (RowData) o;
        return Objects.equals(cellMap, rowData.cellMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cellMap);
    }
}
