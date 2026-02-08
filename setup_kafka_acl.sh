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
echo "Setting up ACLs"
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

# Set ACLs:
#Topic: order-events
#Producer: producer-client (Allow) main
#Consumer: consumer-client (Allow) engine
#Rest not allowed
echo "Setting up ACLs for order-events topic"
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

#Topic: order-db-sync
#Producer: producer-client (Allow) engine
#Consumer: consumer-client (Allow) dbsync
#Rest not allowed
echo "Setting up ACLs for order-db-sync topic"
echo ""

# Engine: produce only
echo "Granting produce permission to main..."
docker exec $KAFKA_CONTAINER $KAFKA_BIN/kafka-acls.sh \
    --bootstrap-server localhost:9092 \
    --command-config /tmp/admin-client.properties \
    --add \
    --allow-principal User:engine \
    --operation Write \
    --topic order-db-sync

if [ $? -ne 0 ]; then
    echo "WARNING: Failed to grant produce permission to engine"
fi

# db sync application: consume only
echo "Granting consume permission to db sync..."
docker exec $KAFKA_CONTAINER $KAFKA_BIN/kafka-acls.sh \
    --bootstrap-server localhost:9092 \
    --command-config /tmp/admin-client.properties \
    --add \
    --allow-principal User:dbsync \
    --operation Read \
    --topic order-db-sync \
    --group db-sync-consumer-group


#Topic: transaction-db-sync
#Producer: producer-client (Allow) engine
#Consumer: consumer-client (Allow) dbsync
#Rest not allowed
echo "Setting up ACLs for transaction-db-sync topic"
echo ""

# Engine: produce only
echo "Granting produce permission to main..."
docker exec $KAFKA_CONTAINER $KAFKA_BIN/kafka-acls.sh \
    --bootstrap-server localhost:9092 \
    --command-config /tmp/admin-client.properties \
    --add \
    --allow-principal User:engine \
    --operation Write \
    --topic transaction-db-sync

if [ $? -ne 0 ]; then
    echo "WARNING: Failed to grant produce permission to engine"
fi

# db sync application: consume only
echo "Granting consume permission to db sync..."
docker exec $KAFKA_CONTAINER $KAFKA_BIN/kafka-acls.sh \
    --bootstrap-server localhost:9092 \
    --command-config /tmp/admin-client.properties \
    --add \
    --allow-principal User:dbsync \
    --operation Read \
    --topic transaction-db-sync \
    --group db-sync-consumer-group

if [ $? -ne 0 ]; then
    echo "WARNING: Failed to grant consume permission to engine"
fi


