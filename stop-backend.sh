#!/bin/bash

# Stop Spring Boot Backend Server

cd "$(dirname "$0")"

echo "Stopping Natural Drops Backend Server..."

# Check if PID file exists
if [ -f backend.pid ]; then
    PID=$(cat backend.pid)
    
    if ps -p $PID > /dev/null 2>&1; then
        echo "Stopping process $PID..."
        kill $PID
        
        # Wait for process to stop
        sleep 3
        
        # Force kill if still running
        if ps -p $PID > /dev/null 2>&1; then
            echo "Force killing process $PID..."
            kill -9 $PID
        fi
        
        echo "✅ Backend server stopped"
    else
        echo "⚠️  Process $PID not found"
    fi
    
    rm -f backend.pid
else
    # Try to find and kill by process name
    echo "PID file not found. Searching for Spring Boot process..."
    pkill -f "spring-boot.*water-supply-backend"
    
    if [ $? -eq 0 ]; then
        echo "✅ Backend server stopped"
    else
        echo "⚠️  No running backend server found"
    fi
fi

# Also kill any Java processes on port 8080
lsof -ti:8080 | xargs kill -9 2>/dev/null || true

echo "Done."

