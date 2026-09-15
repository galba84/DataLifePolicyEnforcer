package com.example.datalifepolicyenforcer.metadata;

import java.util.List;
import static com.example.datalifepolicyenforcer.metadata.Metadata.*;

public interface MetadataReader {
    List<String> schemas(long databaseId);
    List<TableSummary> tables(long databaseId, String schema);
    TableDetails table(long databaseId, String schema, String table);
}

