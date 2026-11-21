// Placeholder so the mongo image mounts an init directory.
// Replica set initiation is performed by the mongo-rs-init one-shot service
// after mongod is healthy (see docker-compose.special-its.yml).
print("mongo-rs volume ready; waiting for rs.initiate from companion service");
