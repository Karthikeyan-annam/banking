#!/usr/bin/env bash
set -e

echo "====================================================================="
echo " Starting Secure Core Banking Full-Stack Application"
echo "====================================================================="

# 1. Start Backend in Background
echo "[1/3] Launching Spring Boot Backend..."
mvn spring-boot:run &
BACKEND_PID=$!

cleanup() {
    echo "Stopping background processes..."
    kill $BACKEND_PID 2>/dev/null || true
}
trap cleanup EXIT INT TERM

# 2. Wait for Backend port 8080 to become ready
echo -n "[2/3] Waiting for Backend to be ready at http://localhost:8080"
MAX_ATTEMPTS=60
ATTEMPTS=0

while [ $ATTEMPTS -lt $MAX_ATTEMPTS ]; do
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8080/actuator/health || true)
    if [ "$STATUS" = "200" ] || [ "$STATUS" = "401" ]; then
        echo ""
        echo "[SUCCESS] Backend is up and healthy!"
        break
    fi
    echo -n "."
    sleep 2
    ATTEMPTS=$((ATTEMPTS+1))
done

if [ $ATTEMPTS -ge $MAX_ATTEMPTS ]; then
    echo ""
    echo "[ERROR] Timed out waiting for backend to start."
    exit 1
fi

# 3. Start Frontend
echo "[3/3] Starting Vite Frontend on http://localhost:3000..."
cd frontend
npm run dev
