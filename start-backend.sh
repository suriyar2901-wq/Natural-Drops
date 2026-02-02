#!/bin/bash

# Spring Boot Backend Startup Script
# This script runs the backend server continuously

cd "$(dirname "$0")"

echo "=========================================="
echo "Starting Natural Drops Backend Server"
echo "=========================================="
echo "Port: 8080"
echo "Address: 0.0.0.0 (accessible from network)"
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

# Build the project first
echo "📦 Building project..."
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "❌ Build failed. Please check the errors above."
    exit 1
fi

echo ""
echo "🚀 Starting Spring Boot application..."
echo "   Press Ctrl+C to stop the server"
echo ""

# Run the Spring Boot application
mvn spring-boot:run
