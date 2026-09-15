package com.example.datalifepolicyenforcer.metadata;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.example.datalifepolicyenforcer.metadata.Metadata.*;

@RestController
@RequestMapping("/api/databases/{id}")
@RequiredArgsConstructor
public class MetadataController {
    private final MetadataReader reader;
    @GetMapping("/schemas") public List<String> schemas(@PathVariable long id) { return reader.schemas(id); }
    @GetMapping("/tables") public List<TableSummary> tables(@PathVariable long id, @RequestParam(required = false) String schema) {
        return reader.tables(id, schema);
    }
    @GetMapping("/tables/{schema}/{table}")
    public TableDetails table(@PathVariable long id, @PathVariable String schema, @PathVariable String table) {
        return reader.table(id, schema, table);
    }
}

