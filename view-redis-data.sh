#!/bin/bash

# Script to view Redis idempotency data
# Usage: ./view-redis-data.sh

echo "=== Redis Idempotency Data Viewer ==="
echo ""

# Total number of idempotency keys
TOTAL_KEYS=$(docker exec redis redis-cli --scan --pattern "idempotency:*" | wc -l)
echo "Total Idempotency Keys: $TOTAL_KEYS"
echo ""

# Show sample keys
echo "=== Sample Keys (first 10) ==="
docker exec redis redis-cli --scan --pattern "idempotency:*" | head -10
echo ""

# Count by status
echo "=== Status Distribution ==="
echo "DONE status:"
docker exec redis redis-cli --scan --pattern "idempotency:*" | while read key; do
    value=$(docker exec redis redis-cli GET "$key")
    if [ "$value" = "DONE" ]; then
        echo "$key"
    fi
done | wc -l

echo "IN_PROGRESS status:"
docker exec redis redis-cli --scan --pattern "idempotency:*" | while read key; do
    value=$(docker exec redis redis-cli GET "$key")
    if [ "$value" = "IN_PROGRESS" ]; then
        echo "$key"
    fi
done | wc -l
echo ""

# Show details of a few keys
echo "=== Sample Key Details ==="
SAMPLE_KEY=$(docker exec redis redis-cli --scan --pattern "idempotency:*" | head -1)
if [ ! -z "$SAMPLE_KEY" ]; then
    echo "Key: $SAMPLE_KEY"
    echo "Value: $(docker exec redis redis-cli GET "$SAMPLE_KEY")"
    echo "TTL: $(docker exec redis redis-cli TTL "$SAMPLE_KEY") seconds ($(($(docker exec redis redis-cli TTL "$SAMPLE_KEY") / 86400)) days remaining)"
fi
