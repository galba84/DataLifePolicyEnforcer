package com.example.datalifepolicyenforcer.metadata;

import com.example.datalifepolicyenforcer.connection.*;
import com.example.datalifepolicyenforcer.common.TargetUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.sql.*;
import java.util.*;
import static com.example.datalifepolicyenforcer.metadata.Metadata.*;

@Component
@RequiredArgsConstructor
public class PostgreSqlMetadataReader implements MetadataReader {
    private final DatabaseConnectionService databases;
    private final TargetConnectionFactory connections;
    private static final String USER_SCHEMA = "n.nspname <> 'information_schema' AND left(n.nspname, 3) <> 'pg_'";
    // Sizes describe each relation's own storage; descendants are reported individually.
    private static final String TABLES = """
            SELECT c.oid, current_database() AS database_name, n.nspname, c.relname,
                   c.reltuples::bigint AS estimated_rows, c.relkind = 'p' AS partitioned, c.relispartition,
                   pg_catalog.pg_total_relation_size(c.oid) AS total_size,
                   pg_catalog.pg_table_size(c.oid) AS table_size,
                   pg_catalog.pg_indexes_size(c.oid) AS index_size
            FROM pg_catalog.pg_class c
            JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
            WHERE c.relkind IN ('r', 'p') AND
            """ + USER_SCHEMA;
    @Override
    public List<String> schemas(long id) {
        return read(id, c -> query(c, "SELECT n.nspname FROM pg_catalog.pg_namespace n WHERE "
                + USER_SCHEMA + " ORDER BY n.nspname", rs -> rs.getString(1)));
    }
    @Override
    public List<TableSummary> tables(long id, String schema) {
        return read(id, c -> schema == null
                ? query(c, TABLES + " ORDER BY n.nspname, c.relname", this::summary)
                : query(c, TABLES + " AND n.nspname = ? ORDER BY c.relname", this::summary, schema));
    }
    @Override
    public TableDetails table(long id, String schema, String table) {
        return read(id, c -> {
            List<Long> ids = query(c, TABLES + " AND n.nspname = ? AND c.relname = ?", rs -> rs.getLong("oid"), schema, table);
            if (ids.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Table not found");
            long oid = ids.getFirst();
            TableSummary summary = query(c, TABLES + " AND c.oid = ?", this::summary, oid).getFirst();
            List<Column> columns = query(c, """
                    SELECT a.attname, pg_catalog.format_type(a.atttypid, a.atttypmod) AS data_type,
                           NOT a.attnotnull AS nullable, pg_catalog.pg_get_expr(d.adbin, d.adrelid) AS default_value
                    FROM pg_catalog.pg_attribute a
                    LEFT JOIN pg_catalog.pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum
                    WHERE a.attrelid = ? AND a.attnum > 0 AND NOT a.attisdropped ORDER BY a.attnum
                    """, rs -> new Column(rs.getString("attname"), rs.getString("data_type"),
                    rs.getBoolean("nullable"), rs.getString("default_value")), oid);
            List<String> primaryKeys = query(c, """
                    SELECT a.attname FROM pg_catalog.pg_index i
                    CROSS JOIN LATERAL unnest(i.indkey) WITH ORDINALITY AS k(attnum, position)
                    JOIN pg_catalog.pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = k.attnum
                    WHERE i.indrelid = ? AND i.indisprimary AND k.position <= i.indnkeyatts ORDER BY k.position
                    """, rs -> rs.getString(1), oid);
            List<Index> indexes = query(c, """
                    SELECT ic.relname, i.indisprimary, i.indisunique,
                           pg_catalog.pg_get_indexdef(i.indexrelid) AS definition,
                           pg_catalog.pg_relation_size(i.indexrelid) AS size
                    FROM pg_catalog.pg_index i JOIN pg_catalog.pg_class ic ON ic.oid = i.indexrelid
                    WHERE i.indrelid = ? ORDER BY ic.relname
                    """, rs -> new Index(rs.getString(1), rs.getBoolean(2), rs.getBoolean(3), rs.getString(4), rs.getLong(5)), oid);
            List<PartitionKey> keys = query(c, """
                    SELECT p.partstrat, pg_catalog.pg_get_partkeydef(p.partrelid),
                           p.partstrat = 'r' AND p.partnatts = 1
                           AND a.atttypid IN ('pg_catalog.date'::regtype, 'pg_catalog.timestamp'::regtype,
                                             'pg_catalog.timestamptz'::regtype) AS temporal
                    FROM pg_catalog.pg_partitioned_table p
                    LEFT JOIN pg_catalog.pg_attribute a ON a.attrelid = p.partrelid AND a.attnum = p.partattrs[0]
                    WHERE p.partrelid = ?
                    """, rs -> new PartitionKey(switch (rs.getString(1)) {
                        case "r" -> "RANGE"; case "l" -> "LIST"; case "h" -> "HASH"; default -> "UNKNOWN";
                    }, rs.getString(2), rs.getBoolean(3)), oid);
            List<Partition> partitions = query(c, """
                    SELECT n.nspname, child.relname, pg_catalog.pg_get_expr(child.relpartbound, child.oid),
                           pg_catalog.pg_total_relation_size(child.oid), pg_catalog.pg_table_size(child.oid),
                           pg_catalog.pg_indexes_size(child.oid), child.relkind = 'p'
                    FROM pg_catalog.pg_inherits i
                    JOIN pg_catalog.pg_class child ON child.oid = i.inhrelid
                    JOIN pg_catalog.pg_namespace n ON n.oid = child.relnamespace
                    WHERE i.inhparent = ? AND child.relispartition ORDER BY n.nspname, child.relname
                    """, rs -> new Partition(rs.getString(1), rs.getString(2), rs.getString(3),
                    rs.getLong(4), rs.getLong(5), rs.getLong(6), rs.getBoolean(7)), oid);
            PartitionKey key = keys.isEmpty() ? new PartitionKey(null, null, false) : keys.getFirst();
            return new TableDetails(summary, columns, primaryKeys, indexes, key.strategy(), key.definition(), key.temporal(), partitions);
        });
    }
    private record PartitionKey(String strategy, String definition, boolean temporal) {}
    private TableSummary summary(ResultSet rs) throws SQLException {
        return new TableSummary(rs.getString("database_name"), rs.getString("nspname"), rs.getString("relname"),
                rs.getLong("estimated_rows"), rs.getLong("total_size"), rs.getLong("table_size"),
                rs.getLong("index_size"), rs.getBoolean("partitioned"), rs.getBoolean("relispartition"));
    }
    private <T> T read(long id, SqlWork<T> work) {
        DatabaseConnection config = databases.require(id);
        try (Connection connection = connections.open(config)) {
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            return work.apply(connection);
        } catch (SQLException ex) {
            throw new TargetUnavailableException();
        }
    }
    private <T> List<T> query(Connection c, String sql, RowMapper<T> mapper, Object... args) throws SQLException {
        try (PreparedStatement statement = c.prepareStatement(sql)) {
            statement.setQueryTimeout(15);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            try (ResultSet rs = statement.executeQuery()) {
                List<T> rows = new ArrayList<>();
                while (rs.next()) rows.add(mapper.map(rs));
                return rows;
            }
        }
    }
    @FunctionalInterface private interface SqlWork<T> { T apply(Connection c) throws SQLException; }
    @FunctionalInterface private interface RowMapper<T> { T map(ResultSet rs) throws SQLException; }
}

