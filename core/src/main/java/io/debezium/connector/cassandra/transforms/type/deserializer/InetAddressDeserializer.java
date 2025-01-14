/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.connector.cassandra.transforms.type.deserializer;

import java.net.InetAddress;
import java.nio.ByteBuffer;

import org.apache.cassandra.db.marshal.AbstractType;
import org.apache.kafka.connect.data.SchemaBuilder;

import io.debezium.connector.cassandra.transforms.CassandraTypeKafkaSchemaBuilders;
import io.debezium.connector.cassandra.transforms.DebeziumTypeDeserializer;

public class InetAddressDeserializer extends LogicalTypeDeserializer {

    private final DebeziumTypeDeserializer deserializer;

    public InetAddressDeserializer(DebeziumTypeDeserializer deserializer) {
        this.deserializer = deserializer;
    }

    @Override
    public Object deserialize(AbstractType<?> abstractType, ByteBuffer bb) {
        Object value = deserializer.deserialize(abstractType, bb);
        return formatDeserializedValue(abstractType, value);
    }

    @Override
    public SchemaBuilder getSchemaBuilder(AbstractType<?> abstractType) {
        return CassandraTypeKafkaSchemaBuilders.STRING_TYPE;
    }

    @Override
    public Object formatDeserializedValue(AbstractType<?> abstractType, Object value) {
        InetAddress inetAddress = (InetAddress) value;
        return inetAddress.toString();
    }
}
