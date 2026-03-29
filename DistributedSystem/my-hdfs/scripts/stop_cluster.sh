#!/usr/bin/env bash
set -euo pipefail

pkill -f 'edu.course.myhdfs.NameNodeServer' || true
pkill -f 'edu.course.myhdfs.DataNodeServer' || true

echo 'Cluster stopped.'
