#!/bin/bash

# Spring Boot Backend Startup Script (Background Mode)
# This script runs the backend server in the background

cd "$(dirname "$0")"

echo "=========================================="
echo "Starting Natural Drops Backend Server"
echo "Running in background mode"
echo "=========================================="
echo ""

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "❌ Error: Java is not installed or not in PATH"
    exit 1
fi

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo "❌ Error: Maven is not installed or not in PATH"
    exit 1
fi

# Check if port 8080 is already in use
if lsof -Pi :8080 -sTCP:LISTEN -t >/dev/null 2>&1 ; then
    echo "⚠️  Warning: Port 8080 is already in use"
    echo "   Stopping existing process..."
    pkill -f "spring-boot.*water-supply-backend" || true
    sleep 2
fi

# Create logs directory if it doesn't exist
mkdir -p logs

# Log file with timestamp
LOG_FILE="logs/backend-$(date +%Y%m%d-%H%M%S).log"

echo "📝 Logs will be written to: $LOG_FILE"
echo ""

# Build the project first
echo "📦 Building project..."
mvn clean package -DskipTests > "$LOG_FILE" 2>&1

if [ $? -ne 0 ]; then
    echo "❌ Build failed. Check log file: $LOG_FILE"
    exit 1
fi

echo "🚀 Starting Spring Boot application in background..."
echo "   Log file: $LOG_FILE"
echo "   To view logs: tail -f $LOG_FILE"
echo "   To stop: ./stop-backend.sh"
echo ""

# Run in background with nohup
nohup mvn spring-boot:run >> "$LOG_FILE" 2>&1 &

# Save PID
echo $! > backend.pid

echo "✅ Backend server started!"
echo "   PID: $(cat backend.pid)"
echo "   Check status: ./status-backend.sh"
echo ""

