#!/usr/bin/env bash
set -a
source .env 2>/dev/null
set +a
./gradlew :app:jvmRun --args="$*"
