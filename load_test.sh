#!/bin/bash

# Load Testing Script for SMS Edge Node
# Simulates high-volume SMS traffic for testing

set -e

PACKAGE="com.sms.edgenode"
DEVICE_SERIAL="${1:-}"
MESSAGE_COUNT="${2:-1000}"

if [ -n "$DEVICE_SERIAL" ]; then
    ADB="adb -s $DEVICE_SERIAL"
else
    ADB="adb"
fi

echo "=== SMS Edge Node Load Test ==="
echo "Target: $MESSAGE_COUNT messages"
echo "Device: $(${ADB} shell getprop ro.product.model)"
echo ""

# Check if app is installed
if ! ${ADB} shell pm list packages | grep -q "$PACKAGE"; then
    echo "Error: App not installed"
    exit 1
fi

# Check if service is running
echo "Checking service status..."
SERVICE_RUNNING=$(${ADB} shell dumpsys activity services | grep -c "RoutingService" || true)
if [ "$SERVICE_RUNNING" -eq 0 ]; then
    echo "Warning: RoutingService not running. Starting..."
    ${ADB} shell am start-foreground-service -n "$PACKAGE/.service.RoutingService"
    sleep 2
fi

# Reset stats
echo "Resetting battery stats..."
${ADB} shell dumpsys batterystats --reset > /dev/null

# Generate test messages
echo "Generating $MESSAGE_COUNT test messages..."
START_TIME=$(date +%s)

for i in $(seq 1 $MESSAGE_COUNT); do
    SENDER="+1555000$(printf "%04d" $((i % 10000)))"
    BODY="Load test message $i at $(date +%s)"
    
    # Simulate SMS via broadcast (requires root or test build)
    ${ADB} shell am broadcast \
        -a android.provider.Telephony.SMS_RECEIVED \
        --es pdus "test_pdu_$i" \
        --es sender "$SENDER" \
        --es body "$BODY" \
        > /dev/null 2>&1 || true
    
    # Progress indicator
    if [ $((i % 100)) -eq 0 ]; then
        ELAPSED=$(($(date +%s) - START_TIME))
        RATE=$((i / (ELAPSED + 1)))
        echo "Progress: $i/$MESSAGE_COUNT ($RATE msg/s)"
    fi
    
    # Rate limit to avoid overwhelming device
    if [ $((i % 50)) -eq 0 ]; then
        sleep 1
    fi
done

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo ""
echo "=== Load Test Complete ==="
echo "Messages sent: $MESSAGE_COUNT"
echo "Duration: ${DURATION}s"
echo "Rate: $((MESSAGE_COUNT / (DURATION + 1))) msg/s"
echo ""

# Wait for processing
echo "Waiting 30s for queue processing..."
sleep 30

# Collect metrics
echo "=== Metrics ==="

# Queue size
QUEUE_SIZE=$(${ADB} shell "run-as $PACKAGE sqlite3 /data/data/$PACKAGE/databases/edge_node_db \"SELECT COUNT(*) FROM sms_queue WHERE status='PENDING';\"" 2>/dev/null || echo "N/A")
echo "Queue size: $QUEUE_SIZE"

# Delivered count
DELIVERED=$(${ADB} shell "run-as $PACKAGE sqlite3 /data/data/$PACKAGE/databases/edge_node_db \"SELECT COUNT(*) FROM sms_queue WHERE status='DELIVERED';\"" 2>/dev/null || echo "N/A")
echo "Delivered: $DELIVERED"

# Failed count
FAILED=$(${ADB} shell "run-as $PACKAGE sqlite3 /data/data/$PACKAGE/databases/edge_node_db \"SELECT COUNT(*) FROM sms_queue WHERE status='FAILED';\"" 2>/dev/null || echo "N/A")
echo "Failed: $FAILED"

# Memory usage
MEM=$(${ADB} shell dumpsys meminfo "$PACKAGE" | grep "TOTAL PSS" | awk '{print $3}')
echo "Memory: ${MEM}KB"

# Battery usage
echo ""
echo "Battery stats (run 'adb shell dumpsys batterystats' for full report)"

echo ""
echo "=== Test Complete ==="
echo "Monitor the app for the next 24h to measure battery impact"
