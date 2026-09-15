## Why

Build the initial version of DataLifecyclePolicyEnforcer — a service for managing
the lifecycle of data stored in PostgreSQL.

The first stage should be able to connect to an existing PostgreSQL database,
inspect its schema, identify tables and partitions, and provide a foundation for
applying lifecycle policies to them.

## What Changes

Create a Spring Boot application that:

- connects to a configured PostgreSQL database;
- discovers schemas, tables, columns, indexes and partitions;
- exposes discovered database structure through a REST API;
- provides a simple UI for browsing the PostgreSQL structure;
- introduces a lifecycle policy model that can be associated with a table;
- stores policy configuration separately from the target application database.

A lifecycle policy should describe future actions such as:

- retention period;
- partition creation;
- partition deletion;
- partition archival;
- exporting old partitions to S3-compatible object storage.

Actual destructive lifecycle operations may be implemented in later stages.

## Capabilities

### New Capabilities

- PostgreSQL database introspection
- Database structure REST API
- Database structure UI
- Lifecycle policy definition
- Policy-to-table association
- Foundation for scheduled lifecycle enforcement

### Modified Capabilities

None. This is the initial implementation.

## Impact

New standalone Spring Boot application.

Main components:

- PostgreSQL metadata reader
- lifecycle policy domain model
- REST API
- persistence layer for lifecycle policies
- scheduler/enforcement abstraction
- web UI

Initial implementation should avoid modifying user data.

The architecture should allow future support for:

- automatic PostgreSQL partition creation;
- retention-based partition deletion;
- archival of partitions to S3-compatible storage;
- restoring archived data;
- dry-run execution;
- audit/history of lifecycle operations;
- support for multiple PostgreSQL databases.