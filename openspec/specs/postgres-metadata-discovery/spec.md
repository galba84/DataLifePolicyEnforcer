# postgres-metadata-discovery Specification

## Purpose
TBD - created by archiving change initial-commit. Update Purpose after archive.
## Requirements
### Requirement: Discover schemas

The system SHALL retrieve non-system PostgreSQL schemas.

#### Scenario: Discover schemas
- **WHEN** a user requests schemas
- **THEN** the system returns non-system PostgreSQL schemas

### Requirement: Discover tables

The system SHALL retrieve tables for each schema.

For each table the system SHALL expose:

- schema name
- table name
- estimated row count
- total size
- table size
- index size

#### Scenario: Discover tables
- **WHEN** a user requests tables in a schema
- **THEN** the system returns table names, estimated row counts and total, table and index sizes

### Requirement: Discover columns

The system SHALL expose:

- column name
- data type
- nullable flag
- default value

#### Scenario: Discover columns
- **WHEN** a user requests columns for a table
- **THEN** the system returns column names, data types, nullable flags and default values

### Requirement: Discover indexes

The system SHALL expose table indexes and primary keys.

#### Scenario: Discover indexes
- **WHEN** a user requests index metadata for a table
- **THEN** the system returns its indexes and primary keys

### Requirement: Discover partitioning

For partitioned tables the system SHALL expose:

- partition strategy
- partition key
- child partitions
- partition bounds
- partition size

#### Scenario: Discover partitioning
- **WHEN** a user inspects a partitioned table
- **THEN** the system returns its partition strategy, key, child partitions, bounds and sizes

### Requirement: Safety

Metadata discovery SHALL not modify the target PostgreSQL database.

#### Scenario: Safety
- **WHEN** metadata discovery runs against a target database
- **THEN** the target database remains unchanged

