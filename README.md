# Data Lifecycle Policy Enforcer

Standalone Java 21 / Spring Boot service for discovering external PostgreSQL databases and managing declarative lifecycle policies. The initial version only reads metadata and produces dry-run plans.

## Run locally

Requirements: Java 21, Docker (for the control database and integration tests).

```powershell
docker compose up -d
$env:APP_USERNAME = 'admin'
$env:APP_PASSWORD = 'choose-a-local-password'
.\gradlew.bat bootRun
```

Open http://localhost:8080 and sign in using HTTP Basic authentication.
Swagger UI is at http://localhost:8080/swagger-ui/index.html and OpenAPI JSON at /v3/api-docs.
Set APP_PASSWORD explicitly; otherwise the service generates a new random password on startup.

The Compose service contains only the **control database**. Configure managed PostgreSQL connections through the UI or REST API. Target databases must be separate from the control database. Flyway and JPA operate exclusively on the control datasource.

| Environment variable | Default |
| --- | --- |
| CONTROL_DB_URL | jdbc:postgresql://localhost:5432/lifecycle_control |
| CONTROL_DB_USERNAME | lifecycle |
| CONTROL_DB_PASSWORD | lifecycle (local Compose development) |
| APP_USERNAME | admin |
| APP_PASSWORD | Random per startup; set explicitly to log in |
| SCHEDULER_ENABLED | false |

The control database stores target credentials, which must remain retrievable for JDBC connections. Its storage is not application-encrypted in this initial version; restrict access to the control database and its backups. REST responses omit both target username and password. Use HTTPS for deployments beyond localhost.

## Connections and metadata

Use a dedicated PostgreSQL account with CONNECT, schema USAGE, and read-only permissions on the managed tables. Configure SSL mode explicitly; the UI defaults to verify-full.

Target JDBC connections set read-only mode, disable autocommit, enforce default_transaction_read_only, and apply connection and query timeouts. Metadata uses parameterized catalog queries; target names are never interpolated into SQL. No internal application tables are installed in targets.

Table metadata includes columns, ordered primary keys, index definitions and sizes, estimated row counts, partition keys, direct child partitions and bounds. Open a child partition in the UI to browse deeper partition levels. Sizes represent each relation's own storage, not a recursive sum; partitioned parent relations can report zero. A row estimate of -1 means PostgreSQL has not collected an estimate.

## Policies and dry runs

Example policy request:

```json
{
  "name": "Telemetry retention",
  "retentionDays": 90,
  "partitionStrategy": "RANGE",
  "partitionIntervalDays": 1,
  "archiveEnabled": true,
  "archiveAfterDays": 30,
  "deleteEnabled": true,
  "deleteAfterDays": 90,
  "targetObjectStorage": "s3://archive/telemetry",
  "executionSchedule": "0 0 2 * * *",
  "dryRun": true
}
```

Periods must be positive whole days. NONE and RANGE are the supported policy partition strategies. An archive plan requires an archive period and storage target. A delete period defaults to retentionDays if omitted and cannot be shorter than the archive period. The initial API rejects dryRun=false.

Assignments identify databaseId, schemaName, and tableName. Assignment creation checks that the target table exists. One policy may cover many tables across databases, but a table may have only one policy. Delete assignments before deleting a referenced connection; deleting a policy also removes its assignments.

The evaluator measures whole elapsed UTC days from each partition's **exclusive upper bound**. Date and timestamp-without-time-zone bounds use UTC; timestamp-with-time-zone offsets are respected. Only single-column date/timestamp RANGE keys are eligible for age-based decisions. Default, unbounded, composite, expression, non-temporal and unsupported bounds produce KEEP with a reason. Ordinary tables also produce KEEP.

- KEEP: no enabled threshold reached, or bounds cannot be evaluated safely.
- ARCHIVE: archive threshold reached.
- DROP: delete threshold reached with archiving disabled.
- ARCHIVE_AND_DROP: both thresholds reached; any future implementation must archive successfully before dropping.

No DDL, DML, archive upload, or data movement is executed. Archive state is not tracked, so repeated previews can repeat archive suggestions. Partition strategy and interval are stored declaratively; CREATE_PARTITION is reserved in the action model for future planning. Nested partition children are evaluated as the direct units of their assigned parent.

The optional scheduler uses six-field Spring cron expressions in UTC and invokes LifecyclePolicyExecutor. The sole executor is DryRunPolicyExecutor. Schedules begin after startup, do not replay missed executions, and produce summary logs; execution history persistence is future work.

## REST API

All endpoints require authentication. Mutating requests also require a session CSRF token: GET /api/csrf, retain the session cookie, and send the returned token using its headerName. The web UI handles this automatically.

| Method | Path | Purpose |
| --- | --- | --- |
| GET, POST | /api/databases | List or configure connections |
| GET, PUT, DELETE | /api/databases/{id} | Read, replace or remove a connection |
| POST | /api/databases/{id}/test | Test and persist connection status |
| GET | /api/databases/{id}/schemas | Discover user schemas |
| GET | /api/databases/{id}/tables?schema=telemetry | Discover tables; schema is optional |
| GET | /api/databases/{id}/tables/{schema}/{table} | Detailed metadata |
| GET, POST | /api/policies | List or create policies |
| GET, PUT, DELETE | /api/policies/{id} | Read, replace or remove a policy |
| GET, POST | /api/policies/{id}/assignments | List or create assignments |
| DELETE | /api/policies/{id}/assignments/{assignmentId} | Remove an assignment |
| POST | /api/policies/{id}/dry-run | Preview every assigned table |

PUT replaces all editable fields; connection updates must supply credentials again.

## Verification

```powershell
.\gradlew.bat build
```

The integration suite requires a running Docker engine and starts separate disposable PostgreSQL 18.6 control and target containers. It verifies migrations, metadata, partition bounds and sizes, policy CRUD/validation, API authentication/CSRF, credential omission, and unchanged target rows/catalog after dry-run. A privileged-account test verifies that target connections still reject writes. Unit tests cover action decisions, exact age thresholds, unsupported bounds and scheduler delegation.

To run the unit tests without Docker:

```powershell
.\gradlew.bat test --tests '*PolicyValidatorTest' --tests '*PartitionPolicyEvaluatorTest' --tests '*PolicySchedulerTest'
```

Implementation references: [PostgreSQL relation catalog](https://www.postgresql.org/docs/current/catalog-pg-class.html), [partition catalog](https://www.postgresql.org/docs/current/catalog-pg-partitioned-table.html), [pgJDBC connection options](https://jdbc.postgresql.org/documentation/use/), [Testcontainers PostgreSQL module](https://java.testcontainers.org/modules/databases/postgres/).

