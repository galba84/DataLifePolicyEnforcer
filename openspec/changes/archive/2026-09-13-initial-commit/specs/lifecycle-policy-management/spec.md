# Capability: Lifecycle Policy Management

## ADDED Requirements

### Requirement: Create policy

The system SHALL allow lifecycle policies to be created.

A policy may define:

- retention period
- partition interval
- archive enabled
- delete enabled
- execution schedule
- dry-run mode

#### Scenario: Create policy
- **WHEN** a user submits a valid lifecycle policy
- **THEN** the system creates the policy with the supplied settings

### Requirement: Assign policy

A lifecycle policy SHALL be assignable to a specific database, schema and table.

Example:

database:
production

table:
telemetry.events

policy:
retention: 90 days
partitionInterval: 1 day
archiveAfter: 30 days
deleteAfter: 90 days

#### Scenario: Assign policy
- **WHEN** a user assigns a policy to a database, schema and table
- **THEN** the system stores that policy assignment

### Requirement: Validate policy

The system SHALL reject invalid policies.

Examples:

- negative retention
- delete period shorter than archive period
- unsupported partitioning strategy

#### Scenario: Validate policy
- **WHEN** a user submits an invalid policy
- **THEN** the system rejects the policy

### Requirement: Persist policy

Policies SHALL be stored in the application's control database and not in the target database.

#### Scenario: Persist policy
- **WHEN** a lifecycle policy is saved
- **THEN** the system stores it in the control database without writing it to the target database

