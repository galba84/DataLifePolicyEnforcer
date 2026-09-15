package com.example.datalifepolicyenforcer.metadata;

import java.util.List;

public final class Metadata {
    private Metadata() {}
    public record TableSummary(String database, String schema, String table, long estimatedRows,
                               long totalSize, long tableSize, long indexSize, boolean partitioned, boolean partition) {}
    public record Column(String name, String dataType, boolean nullable, String defaultValue) {}
    public record Index(String name, boolean primary, boolean unique, String definition, long size) {}
    public record Partition(String schema, String table, String bounds, long totalSize,
                            long tableSize, long indexSize, boolean partitioned) {}
    public record TableDetails(TableSummary table, List<Column> columns, List<String> primaryKeys,
                               List<Index> indexes, String partitionStrategy, String partitionKey,
                               boolean temporalRangeKey, List<Partition> partitions) {}
}

