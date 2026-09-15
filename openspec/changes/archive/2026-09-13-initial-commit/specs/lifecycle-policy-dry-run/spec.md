# Capability: Lifecycle Policy Dry Run

## ADDED Requirements

### Requirement: Evaluate policy

The system SHALL evaluate a lifecycle policy against the current state of a PostgreSQL table.

#### Scenario: Evaluate policy
- **WHEN** a user requests a dry run for a table and lifecycle policy
- **THEN** the system evaluates the policy against the current table state

### Requirement: Produce execution plan

The dry-run result SHALL describe actions that would be performed.

Example:

Table:
telemetry.events

Partitions:

events_2026_01
age: 250 days
action: ARCHIVE_AND_DROP

events_2026_05
age: 120 days
action: DROP

events_2026_08
age: 25 days
action: KEEP

#### Scenario: Produce execution plan
- **WHEN** a dry-run evaluation completes
- **THEN** the result describes the proposed actions without executing them

