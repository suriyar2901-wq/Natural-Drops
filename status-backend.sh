#!/bin/bash

# Check Spring Boot Backend Server Status

cd "$(dirname "$0")"

echo "=========================================="
echo "Backend Server Status"
echo "=========================================="
echo ""

# Check if PID file exists
if [ -f backend.pid ]; then
    PID=$(cat backend.pid)
    
    if ps -p $PID > /dev/null 2>&1; then
        echo "✅ Server is RUNNING"
        echo "   PID: $PID"
        echo ""
        
        # Check port 8080
        if lsof -Pi :8080 -sTCP:LISTEN -t >/dev/null 2>&1 ; then
            echo "✅ Port 8080 is LISTENING"
        else
            echo "⚠️  Port 8080 is NOT listening"
        fi
        
        # Show process info
        echo ""
        echo "Process Info:"
        ps -p $PID -o pid,ppid,%cpu,%mem,etime,cmd
        
        # Show recent logs
        if [ -d logs ]; then
            LATEST_LOG=$(ls -t logs/backend-*.log 2>/dev/null | head -1)
            if [ -n "$LATEST_LOG" ]; then
                echo ""
                echo "Recent logs (last 10 lines):"
                echo "----------------------------------------"
                tail -10 "$LATEST_LOG"
            fi
        fi
    else
        echo "❌ Server is NOT RUNNING"
        echo "   PID file exists but process not found"
        rm -f backend.pid
    fi
else
    # Check if any Spring Boot process is running
    if pgrep -f "spring-boot.*water-supply-backend" > /dev/null; then
        echo "⚠️  Server may be running (PID file not found)"
        echo "   Process found:"
        pgrep -f "spring-boot.*water-supply-backend"
    else
        echo "❌ Server is NOT RUNNING"
    fi
fi

# Check port 8080
echo ""
echo "Port 8080 Status:"
if lsof -Pi :8080 -sTCP:LISTEN -t >/dev/null 2>&1 ; then
    echo "✅ Port 8080 is in use"
    lsof -Pi :8080 -sTCP:LISTEN
else
    echo "❌ Port 8080 is not in use"
fi

echo ""

