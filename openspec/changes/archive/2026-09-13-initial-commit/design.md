# Design: Initial Commit

                        DataLifecyclePolicyEnforcer
                                  |
          +-----------------------+----------------------+
          |                       |                      |
    Metadata Reader          Policy Service       Enforcement Engine
          |                       |                      |
          |                       |                Scheduler
          |                       |                      |
    Target PostgreSQL        Control DB              Executor
          |                                              |
          +----------------------------------------------+
                                  |
                           S3 / MinIO
                            future

## Approach

The application will be implemented as a standalone Spring Boot service.

The first implementation stage will focus on PostgreSQL metadata discovery and
lifecycle policy definition. It must not perform destructive data operations by
default.

The system will distinguish between:

1. Target PostgreSQL databases
    - databases whose structure and data lifecycle are managed;
    - accessed using configured JDBC connections;
    - treated as external systems;
    - no internal application tables should be created inside them.

2. Application metadata database
    - stores configured database connections;
    - stores lifecycle policies;
    - stores policy-to-table associations;
    - stores execution history in future stages.

PostgreSQL metadata should be discovered primarily through PostgreSQL system
catalogs such as:

- pg_catalog.pg_class
- pg_catalog.pg_namespace
- pg_catalog.pg_attribute
- pg_catalog.pg_index
- pg_catalog.pg_partitioned_table
- pg_catalog.pg_inherits

information_schema may be used where appropriate, but PostgreSQL-specific
catalogs are preferred when they provide richer metadata.

The metadata model should expose at least:

- database
- schema
- table
- columns
- primary keys
- indexes
- table size
- partitioning status
- partition key
- child partitions

The application should expose discovered metadata through REST APIs and provide
a simple web UI for browsing database structure.

Lifecycle policies will initially be declarative only.

A policy may contain:

- retention period
- partitioning strategy
- partition interval
- archive policy
- delete policy
- target object storage
- execution schedule
- dry-run flag

The enforcement engine should be designed behind an interface so that metadata
discovery and policy management are independent from execution logic.

Example abstraction:

LifecyclePolicyExecutor
|
+-- DryRunPolicyExecutor
+-- PostgreSqlPolicyExecutor
+-- ArchivePolicyExecutor (future)

The initial implementation should provide a DryRunPolicyExecutor only.

The application should be designed so multiple PostgreSQL databases can be
managed from one DataLifecyclePolicyEnforcer instance.

## Components Affected

### DatabaseConnection

Represents an external PostgreSQL database managed by the service.

Responsibilities:

- connection configuration
- connection validation
- connection status

Sensitive credentials must not be returned by REST APIs.

### Metadata Reader

Responsible for PostgreSQL structure discovery.

Responsibilities:

- discover schemas
- discover tables
- discover columns
- discover indexes
- detect partitioned tables
- discover partitions
- retrieve table and partition sizes

Metadata discovery must be read-only.

### Policy Service

Responsible for lifecycle policy management.

Responsibilities:

- create lifecycle policies
- update lifecycle policies
- assign policies to tables
- validate policies
- expose policies through REST APIs

### Enforcement Engine

Responsible for evaluating and executing lifecycle policies.

Initial implementation:

- dry-run only
- determine which action would be executed
- no DELETE, DROP or data movement

Future implementation:

- create partitions
- detach partitions
- archive partitions
- drop expired partitions

### Scheduler

Triggers lifecycle policy evaluation.

The scheduler must invoke the enforcement abstraction rather than execute SQL
directly.

### REST API

Initial endpoints may include:

GET  /api/databases
POST /api/databases
POST /api/databases/{id}/test

GET  /api/databases/{id}/schemas
GET  /api/databases/{id}/tables
GET  /api/databases/{id}/tables/{schema}/{table}

GET  /api/policies
POST /api/policies
PUT  /api/policies/{id}
DELETE /api/policies/{id}

POST /api/policies/{id}/assignments
POST /api/policies/{id}/dry-run

### Web UI

Initial UI should provide:

- configured databases
- schemas
- tables
- table metadata
- partition structure
- lifecycle policy configuration
- policy assignment
- dry-run result

## Trade-offs

### Standalone service vs PostgreSQL extension

The initial implementation will use a standalone Spring Boot service rather than
a PostgreSQL extension.

Advantages:

- easier deployment and development
- one service can manage multiple databases
- no PostgreSQL server extension installation required
- easier integration with S3-compatible storage
- easier REST API and UI implementation
- lifecycle logic remains outside the managed database

Disadvantages:

- requires external credentials
- depends on network connectivity
- some operations may require elevated PostgreSQL permissions

A PostgreSQL extension may be considered later for specialized functionality,
but it is not required for the initial architecture.

### PostgreSQL-specific catalogs vs generic JDBC metadata

The implementation will prefer PostgreSQL system catalogs.

Advantages:

- complete partition information
- PostgreSQL-specific storage metadata
- better access to table and index sizes

Disadvantage:

- initial implementation is PostgreSQL-specific

This is acceptable because PostgreSQL is the initial target platform.

### Separate metadata storage

Policies and configuration should be stored separately from the target database.

Advantages:

- target databases remain untouched
- multiple databases can be managed centrally
- lifecycle configuration is isolated from application data

Disadvantage:

- requires a metadata persistence layer.

### Safety

Destructive operations must never be enabled implicitly.

The initial version should operate in read-only metadata discovery and dry-run
mode.

Future destructive executors must require explicit policy configuration and
should maintain an audit history.