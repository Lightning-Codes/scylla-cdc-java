package com.scylladb.cdc.model.worker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class OperationTypeParseTest {
    @Test
    public void testClientOperationsParseByIndex() {
        RawChange.OperationType[] values = RawChange.OperationType.values();
        for (int i = 0; i < values.length; i++) {
            assertEquals(values[i], RawChange.OperationType.parse((byte) i));
        }
    }

    @Test
    public void testServiceDeletesMapToClientDeletes() {
        // Alternator TTL expiry and per-row TTL expiry are logged as -3 / -4.
        assertEquals(RawChange.OperationType.ROW_DELETE, RawChange.OperationType.parse((byte) -3));
        assertEquals(RawChange.OperationType.PARTITION_DELETE, RawChange.OperationType.parse((byte) -4));
    }

    @Test
    public void testUnknownOperationsStillThrow() {
        assertThrows(IllegalArgumentException.class, () -> RawChange.OperationType.parse((byte) -1));
        assertThrows(IllegalArgumentException.class, () -> RawChange.OperationType.parse((byte) -2));
        assertThrows(IllegalArgumentException.class, () -> RawChange.OperationType.parse((byte) -5));
        assertThrows(IllegalArgumentException.class, () -> RawChange.OperationType.parse((byte) 10));
    }
}
