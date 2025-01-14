/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.connector.cassandra.transforms;

import java.nio.ByteBuffer;

import org.apache.cassandra.db.marshal.AbstractType;

public interface DebeziumTypeDeserializer {
    Object deserialize(AbstractType abstractType, ByteBuffer bb);
}
