# Special integration-test Mongo fixtures

Isolated Docker Compose project for backend ITs that need a replica set or
restricted Mongo privileges. **Does not touch** the developer Mongo on
`127.0.0.1:27017` or any existing Compose volumes.

| Service | Host port | Purpose | Env flag |
|---|---|---|---|
| `mongo-rs` | `127.0.0.1:27018` | Single-node replica set `rs0` | `CODERZCLUB_REPLICA_SET=1`, `CODERZCLUB_LOCAL_FLOW=1` |
| `mongo-auth` | `127.0.0.1:27019` | Root + `noindex` with custom `readWriteNoIndex` (no `createIndex`) | `CODERZCLUB_INDEX_AUTH=1` |

Note: built-in Mongo `readWrite` includes `createIndex`, so the init script creates a custom role.

## Start

```powershell
docker compose -p coderzclub-special-its -f docker-compose.special-its.yml up -d --wait
```

Wait until `mongo-rs-init` exits 0 and both healthchecks are healthy.

## Run ITs (sequential — never parallel against the same Maven `target/`)

```powershell
cd backend
$env:CODERZCLUB_REPLICA_SET = "1"
mvn -Dtest=ReplicaSetIdempotencyIT test
Remove-Item Env:CODERZCLUB_REPLICA_SET

$env:CODERZCLUB_INDEX_AUTH = "1"
mvn -Dtest=IndexPrivilegeIT test
Remove-Item Env:CODERZCLUB_INDEX_AUTH

$env:CODERZCLUB_LOCAL_FLOW = "1"
mvn -Dtest=LocalSubmissionFlowIT test
Remove-Item Env:CODERZCLUB_LOCAL_FLOW
```

`LocalSubmissionFlowIT` also needs local Redis (`6379`) and RabbitMQ (`5672`).
It uses the isolated replica on **27018** (`rs0`), not the standalone 27017 database.

## Tear down

```powershell
docker compose -p coderzclub-special-its -f docker-compose.special-its.yml down -v
```

`-v` removes only `coderzclub_special_rs_data` and `coderzclub_special_auth_data`.
