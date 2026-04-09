#!/bin/bash
#
# Docker commands to create order-events topic and set ACLs
# Run after Kafka has started: docker-compose up -d
#
# Usage: ./setup-kafka-topic-and-acls.sh

set -e  # Exit on error

# Prevent Git Bash (MSYS) on Windows from converting /opt/... to C:/Program Files/Git/opt/...
# Otherwise "docker exec kafka /opt/kafka/bin/..." would be passed as a Windows path to the container
export MSYS_NO_PATHCONV=1

KAFKA_CONTAINER="kafka"
KAFKA_BIN="/opt/kafka/bin"
echo "=========================================="
echo "Setting up Kafka topic and ACLs"
echo "=========================================="
echo ""

# Check if Kafka container is running
if ! docker ps | grep -q "$KAFKA_CONTAINER"; then
    echo "ERROR: Kafka container '$KAFKA_CONTAINER' is not running!"
    echo "Please start Kafka first: docker-compose up -d"
    exit 1
fi

echo "Kafka container found. Proceeding..."
echo ""

# Create admin client properties file inside container
echo "Creating admin client config..."
docker exec $KAFKA_CONTAINER bash -c 'cat > /tmp/admin-client.properties << EOF
security.protocol=SASL_PLAINTEXT
sasl.mechanism=PLAIN
sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="admin" password="admin-secret";
EOF'

if [ $? -ne 0 ]; then
    echo "ERROR: Failed to create admin client config"
    exit 1
fi

echo "Admin client config created successfully."
echo ""

# Create order-events topic
echo "Creating order-events topic..."
docker exec $KAFKA_CONTAINER $KAFKA_BIN/kafka-topics.sh \
    --create \
    --bootstrap-server localhost:9092 \
    --command-config /tmp/admin-client.properties \
    --topic order-events \
    --partitions 1 \
    --replication-factor 1 \
    --if-not-exists

if [ $? -ne 0 ]; then
    echo "ERROR: Failed to create order-events topic"
    exit 1
fi

echo "Topic 'order-events' created successfully."
echo ""

# Create order-db-sync and transaction-db-sync topics (for db-sync service)
echo "Creating order-db-sync topic..."
docker exec $KAFKA_CONTAINER $KAFKA_BIN/kafka-topics.sh \
    --create \
    --bootstrap-server localhost:9092 \
    --command-config /tmp/admin-client.properties \
    --topic order-db-sync \
    --partitions 1 \
    --replication-factor 1 \
    --if-not-exists

echo "Creating transaction-db-sync topic..."
docker exec $KAFKA_CONTAINER $KAFKA_BIN/kafka-topics.sh \
    --create \
    --bootstrap-server localhost:9092 \
    --command-config /tmp/admin-client.properties \
    --topic transaction-db-sync \
    --partitions 1 \
    --replication-factor 1 \
    --if-not-exists

echo "db-sync topics created successfully."
echo ""

# Set ACLs: main can produce, engine can consume (order-events); engine produce + dbsync consume (order-db-sync, transaction-db-sync)
echo "Setting up ACLs..."
echo ""

# Main: produce only
echo "Granting produce permission to main..."
docker exec $KAFKA_CONTAINER $KAFKA_BIN/kafka-acls.sh \
    --bootstrap-server localhost:9092 \
    --command-config /tmp/admin-client.properties \
    --add \
    --allow-principal User:main \
    --operation Write \
    --topic order-events

if [ $? -ne 0 ]; then
    echo "WARNING: Failed to grant produce permission to main"
fi

# Engine: consume only
echo "Granting consume permission to engine..."
docker exec $KAFKA_CONTAINER $KAFKA_BIN/kafka-acls.sh \
    --bootstrap-server localhost:9092 \
    --command-config /tmp/admin-client.properties \
    --add \
    --allow-principal User:engine \
    --operation Read \
    --topic order-events \
    --group engine-order-consumer-group

if [ $? -ne 0 ]; then
    echo "WARNING: Failed to grant consume permission to engine"
fi

# Engine: deny produce
echo "Denying produce permission to engine..."
docker exec $KAFKA_CONTAINER $KAFKA_BIN/kafka-acls.sh \
    --bootstrap-server localhost:9092 \
    --command-config /tmp/admin-client.properties \
    --add \
    --deny-principal User:engine \
    --operation Write \
    --topic order-events

if [ $? -ne 0 ]; then
    echo "WARNING: Failed to deny produce permission to engine"
fi

# Tradingbot: deny produce
echo "Denying produce permission to tradingbot..."
docker exec $KAFKA_CONTAINER $KAFKA_BIN/kafka-acls.sh \
    --bootstrap-server localhost:9092 \
    --command-config /tmp/admin-client.properties \
    --add \
    --deny-principal User:tradingbot \
    --operation Write \
    --topic order-events

if [ $? -ne 0 ]; then
    echo "WARNING: Failed to deny produce permission to tradingbot"
fi

# Tradingbot: deny consume
echo "Denying consume permission to tradingbot..."
docker exec $KAFKA_CONTAINER $KAFKA_BIN/kafka-acls.sh \
    --bootstrap-server localhost:9092 \
    --command-config /tmp/admin-client.properties \
    --add \
    --deny-principal User:tradingbot \
    --operation Read \
    --topic order-events \
    --group "*"

if [ $? -ne 0 ]; then
    echo "WARNING: Failed to deny consume permission to tradingbot"
fi

# --- order-db-sync, transaction-db-sync: engine can produce, dbsync can consume ---
echo "Granting engine produce permission to order-db-sync..."
docker exec $KAFKA_CONTAINER $KAFKA_BIN/kafka-acls.sh \
    --bootstrap-server localhost:9092 \
    --command-config /tmp/admin-client.properties \
    --add \
    --allow-principal User:engine \
    --operation Write \
    --topic order-db-sync

echo "Granting engine produce permission to transaction-db-sync..."
docker exec $KAFKA_CONTAINER $KAFKA_BIN/kafka-acls.sh \
    --bootstrap-server localhost:9092 \
    --command-config /tmp/admin-client.properties \
    --add \
    --allow-principal User:engine \
    --operation Write \
    --topic transaction-db-sync

echo "Granting dbsync consume permission to order-db-sync and transaction-db-sync..."
docker exec $KAFKA_CONTAINER $KAFKA_BIN/kafka-acls.sh \
    --bootstrap-server localhost:9092 \
    --command-config /tmp/admin-client.properties \
    --add \
    --allow-principal User:dbsync \
    --operation Read \
    --topic order-db-sync \
    --group db-sync-consumer-group

docker exec $KAFKA_CONTAINER $KAFKA_BIN/kafka-acls.sh \
    --bootstrap-server localhost:9092 \
    --command-config /tmp/admin-client.properties \
    --add \
    --allow-principal User:dbsync \
    --operation Read \
    --topic transaction-db-sync \
    --group db-sync-consumer-group

echo ""
echo "=========================================="
echo "Setup completed!"
echo "=========================================="
echo ""
echo "Summary:"
echo "  - order-events:        Main ✅ produce | Engine ✅ consume"
echo "  - order-db-sync:       Engine ✅ produce | dbsync ✅ consume"
echo "  - transaction-db-sync: Engine ✅ produce | dbsync ✅ consume"
echo ""
echo "Verify ACLs:"
echo "  docker exec $KAFKA_CONTAINER $KAFKA_BIN/kafka-acls.sh --bootstrap-server localhost:9092 --command-config /tmp/admin-client.properties --list --topic order-events"
echo ""
echo "Verify topic:"
echo "  docker exec $KAFKA_CONTAINER $KAFKA_BIN/kafka-topics.sh --bootstrap-server localhost:9092 --command-config /tmp/admin-client.properties --list"
