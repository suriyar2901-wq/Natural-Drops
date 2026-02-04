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

# Load environment variables from .env file if it exists (improved method)
if [ -f ".env" ]; then
    echo "📋 Loading environment variables from .env file..."
    # Use set -a to automatically export all variables
    set -a
    source .env
    set +a
    echo "✅ Environment variables loaded from .env"
    echo ""
fi

# Check if application-local.properties exists (preferred method for local development)
LOCAL_PROPS_FILE="src/main/resources/application-local.properties"
if [ -f "$LOCAL_PROPS_FILE" ]; then
    echo "✅ Found application-local.properties - using 'local' profile"
    USE_LOCAL_PROFILE=true
else
    echo "ℹ️  application-local.properties not found - will use environment variables"
    USE_LOCAL_PROFILE=false
    
    # If no .env and no local props, check for required environment variables
    if [ ! -f ".env" ]; then
        if [ -z "$DATABASE_USERNAME" ] || [ -z "$DATABASE_PASSWORD" ]; then
            echo "⚠️  Warning: No configuration found!"
            echo "   Please either:"
            echo "   1. Create application-local.properties with your database credentials"
            echo "   2. Create .env file: ./setup-env.sh"
            echo "   3. Export variables: export DATABASE_USERNAME=... DATABASE_PASSWORD=..."
            echo ""
            echo "   For local development, we recommend using application-local.properties"
            echo "   (it's already gitignored for security)"
            exit 1
        fi
    fi
fi

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
if [ "$USE_LOCAL_PROFILE" = true ]; then
    echo "   Using profile: local (application-local.properties)"
else
    echo "   Using environment variables"
fi
echo "   Press Ctrl+C to stop the server"
echo ""

# Run the Spring Boot application
# Use local profile if application-local.properties exists, otherwise use environment variables
if [ "$USE_LOCAL_PROFILE" = true ]; then
    # Ensure the local profile is active for both Spring Boot and Maven plugin
    export SPRING_PROFILES_ACTIVE=local
    mvn spring-boot:run -Dspring-boot.run.profiles=local
else
    mvn spring-boot:run
fi
