#!/bin/bash

N=${1:-2}
STACK="crdt-app"
STABLE=120
CHURN=120

run_single_churn() {
  local SERVICE="${STACK}_node"
  local CURRENT
  CURRENT=$(docker service inspect --format='{{.Spec.Mode.Replicated.Replicas}}' $SERVICE)

  echo "[CHURN] Scaling down $N replicas..."
  docker service scale $SERVICE=$((CURRENT - N)) >/dev/null
  sleep $((CHURN / 2))

  echo "[CHURN] Scaling back up..."
  docker service scale $SERVICE=$CURRENT >/dev/null
  sleep $((CHURN / 2))

  echo "[CHURN] Completed down-up cycle."
}

run_experiment() {
  local COMPOSE=$1
  echo "=============================="
  echo "[RUN] Deploying $COMPOSE"
  echo "=============================="
  docker stack deploy -c "$COMPOSE" $STACK
  echo "[INFO] Waiting 30s for startup..."
  sleep 30

  echo "[INFO] Stable phase 1 ($STABLE s)"
  sleep $STABLE

  run_single_churn

  echo "[INFO] Stable phase 2 ($STABLE s)"
  sleep $STABLE

  echo "[STOP] Removing stack $STACK"
  docker stack rm $STACK
  sleep 30
}

run_experiment docker-compose-state.yml
run_experiment docker-compose-small-delta.yml
run_experiment docker-compose-big-delta.yml

echo "[ALL DONE]"

