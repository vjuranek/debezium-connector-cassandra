/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.connector.cassandra;

import static io.debezium.connector.cassandra.TestUtils.TEST_KEYSPACE_NAME;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.File;

import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.kafka.connect.data.Schema;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.debezium.config.Configuration;
import io.debezium.connector.base.ChangeEventQueue;
import io.debezium.time.Conversions;

public class QueueProcessorTest extends EmbeddedCassandra3ConnectorTestBase {
    private CassandraConnectorContext context;
    private QueueProcessor queueProcessor;
    private KafkaRecordEmitter emitter;

    @Before
    public void setUp() throws Exception {
        context = generateTaskContext();
        emitter = mock(KafkaRecordEmitter.class);
        queueProcessor = new QueueProcessor(context, 0, emitter);
    }

    @After
    public void tearDown() {
        context.cleanUp();
    }

    @Test
    public void testProcessChangeRecords() throws Exception {
        doNothing().when(emitter).emit(any());

        int recordSize = 5;
        ChangeEventQueue<Event> queue = context.getQueues().get(0);
        for (int i = 0; i < recordSize; i++) {
            Configuration configuration = Configuration.empty()
                    .edit()
                    .with(CassandraConnectorConfig.CONNECTOR_NAME, "someconnector")
                    .build();

            CassandraConnectorConfig config = new CassandraConnectorConfig(configuration);

            SourceInfo sourceInfo = new SourceInfo(config, DatabaseDescriptor.getClusterName(),
                    new OffsetPosition("CommitLog-6-123.log", i),
                    new KeyspaceTable(TEST_KEYSPACE_NAME, "cdc_table"), false,
                    Conversions.toInstantFromMicros(System.currentTimeMillis() * 1000));
            Record record = new ChangeRecord(sourceInfo, new RowData(), Schema.INT32_SCHEMA, Schema.INT32_SCHEMA, Record.Operation.INSERT, false);
            queue.enqueue(record);
        }

        assertEquals(recordSize, queue.totalCapacity() - queue.remainingCapacity());
        queueProcessor.process();
        verify(emitter, times(recordSize)).emit(any());
        assertEquals(queue.totalCapacity(), queue.remainingCapacity());
    }

    @Test
    public void testProcessTombstoneRecords() throws Exception {
        doNothing().when(emitter).emit(any());

        int recordSize = 5;
        ChangeEventQueue<Event> queue = context.getQueues().get(0);
        for (int i = 0; i < recordSize; i++) {
            Configuration configuration = Configuration.empty()
                    .edit()
                    .with(CassandraConnectorConfig.CONNECTOR_NAME, "someconnector")
                    .build();

            CassandraConnectorConfig config = new CassandraConnectorConfig(configuration);

            SourceInfo sourceInfo = new SourceInfo(config, DatabaseDescriptor.getClusterName(),
                    new OffsetPosition("CommitLog-6-123.log", i),
                    new KeyspaceTable(TEST_KEYSPACE_NAME, "cdc_table"), false,
                    Conversions.toInstantFromMicros(System.currentTimeMillis() * 1000));
            Record record = new TombstoneRecord(sourceInfo, new RowData(), Schema.INT32_SCHEMA);
            queue.enqueue(record);
        }

        assertEquals(recordSize, queue.totalCapacity() - queue.remainingCapacity());
        queueProcessor.process();
        verify(emitter, times(recordSize)).emit(any());
        assertEquals(queue.totalCapacity(), queue.remainingCapacity());
    }

    @Test
    public void testProcessEofEvent() throws Exception {
        doNothing().when(emitter).emit(any());

        ChangeEventQueue<Event> queue = context.getQueues().get(0);
        File commitLogFile = TestUtils.generateCommitLogFile();
        queue.enqueue(new EOFEvent(commitLogFile));

        assertEquals(1, queue.totalCapacity() - queue.remainingCapacity());
        queueProcessor.process();
        verify(emitter, times(0)).emit(any());
        assertEquals(queue.totalCapacity(), queue.remainingCapacity());
    }
}
