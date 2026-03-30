#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

mkdir -p logs data/dn1 data/dn2 data/dn3
mkdir -p data/namenode

mvn -q -DskipTests compile

pkill -f 'edu.course.myhdfs.NameNodeServer' || true
pkill -f 'edu.course.myhdfs.DataNodeServer' || true

REPLICAS='dn1=http://127.0.0.1:9001,dn2=http://127.0.0.1:9002,dn3=http://127.0.0.1:9003'

nohup mvn -q -DskipTests -Dexec.mainClass=edu.course.myhdfs.NameNodeServer -Dexec.args="--port=9000 --replicas=${REPLICAS}" exec:java > logs/namenode.log 2>&1 &

nohup mvn -q -DskipTests -Dexec.mainClass=edu.course.myhdfs.DataNodeServer -Dexec.args='--id=dn1 --port=9001 --storageDir=data/dn1 --forwardDelayMs=1500 --throttleBytesPerSec=1024' exec:java > logs/dn1.log 2>&1 &
nohup mvn -q -DskipTests -Dexec.mainClass=edu.course.myhdfs.DataNodeServer -Dexec.args='--id=dn2 --port=9002 --storageDir=data/dn2 --forwardDelayMs=1500 --throttleBytesPerSec=1024' exec:java > logs/dn2.log 2>&1 &
nohup mvn -q -DskipTests -Dexec.mainClass=edu.course.myhdfs.DataNodeServer -Dexec.args='--id=dn3 --port=9003 --storageDir=data/dn3 --forwardDelayMs=1500 --throttleBytesPerSec=1024' exec:java > logs/dn3.log 2>&1 &

wait_http() {
  local url="$1"
  local attempts="${2:-20}"
  local i
  for ((i=1; i<=attempts; i++)); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      return 0
    fi
    sleep 0.5
  done
  return 1
}

wait_http 'http://127.0.0.1:9000/api/v1/health' 30 || true
wait_http 'http://127.0.0.1:9001/api/v1/health' 30 || true
wait_http 'http://127.0.0.1:9002/api/v1/health' 30 || true
wait_http 'http://127.0.0.1:9003/api/v1/health' 30 || true

echo 'Cluster started. Health checks:'
curl -sS http://127.0.0.1:9000/api/v1/health || true
echo
echo 'DataNodes:'
for p in 9001 9002 9003; do
  printf '  :%s -> ' "$p"
  curl -sS "http://127.0.0.1:${p}/api/v1/state" || true
  echo
done

echo 'Logs in logs/*.log'
