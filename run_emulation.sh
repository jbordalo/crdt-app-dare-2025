#!/bin/bash

STACK="crdt-app"
DURATION=$((15 * 60))  # 15 minutes

run_stack() {
  FILE=$1
  echo "=== Running $FILE for 15 minutes ==="
  docker stack deploy -c "$FILE.yml" $STACK

  echo "Sleeping for $DURATION minutes"
  sleep $DURATION

  echo "=== Stopping $FILE ==="
  docker stack rm $STACK

  # Wait for cleanup
  sleep 30
}

run_stack "docker-compose-state"
run_stack "docker-compose-small-delta"
run_stack "docker-compose-big-delta"

echo "All runs complete."

