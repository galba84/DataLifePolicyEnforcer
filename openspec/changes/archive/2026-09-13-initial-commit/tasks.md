# Tasks: Initial Commit

## 1. Project foundation

- [x] Add Spring Boot Web
- [x] Add Spring Data JPA
- [x] Add PostgreSQL driver
- [x] Add Flyway
- [x] Add validation
- [x] Add OpenAPI/Swagger
- [x] Configure application metadata database

## 2. Database connection management

- [x] Create DatabaseConnection entity
- [x] Create repository
- [x] Create DTOs
- [x] Implement connection CRUD service
- [x] Implement PostgreSQL connection test
- [x] Add REST endpoints
- [x] Ensure passwords are never returned by API

## 3. PostgreSQL metadata discovery

- [x] Create MetadataReader interface
- [x] Implement PostgreSqlMetadataReader
- [x] Discover schemas
- [x] Discover tables
- [x] Discover columns
- [x] Discover primary keys
- [x] Discover indexes
- [x] Detect partitioned tables
- [x] Discover child partitions
- [x] Read partition bounds
- [x] Read table/partition sizes
- [x] Add metadata REST endpoints

## 4. Lifecycle policy management

- [x] Create LifecyclePolicy entity
- [x] Define retention settings
- [x] Define archive settings
- [x] Define delete/drop settings
- [x] Define partition interval
- [x] Define dry-run flag
- [x] Implement policy validation
- [x] Implement policy CRUD
- [x] Implement policy assignment to database/schema/table
- [x] Add policy REST endpoints

## 5. Dry-run engine

- [x] Define LifecyclePolicyEvaluator interface
- [x] Define LifecycleExecutionPlan
- [x] Define LifecycleAction
- [x] Define ActionType enum:
  KEEP
  CREATE_PARTITION
  ARCHIVE
  DROP
  ARCHIVE_AND_DROP

- [x] Implement policy evaluation for partitions
- [x] Implement KEEP decision
- [x] Implement ARCHIVE decision
- [x] Implement DROP decision
- [x] Implement ARCHIVE_AND_DROP decision
- [x] Add explanation/reason for every action
- [x] Add dry-run REST endpoint
- [x] Guarantee no DDL/DML execution during dry-run

## 6. Integration tests

- [x] Add Testcontainers PostgreSQL
- [x] Create partitioned test table
- [x] Test schema discovery
- [x] Test table discovery
- [x] Test partition discovery
- [x] Test partition bounds
- [x] Test table sizes
- [x] Test policy validation
- [x] Test lifecycle evaluator
- [x] Verify dry-run does not modify target database

## 7. Initial UI

- [x] Show configured databases
- [x] Show schemas
- [x] Show tables
- [x] Show table details
- [x] Show partitions
- [x] Create/edit lifecycle policy
- [x] Assign policy to table
- [x] Show dry-run execution plan