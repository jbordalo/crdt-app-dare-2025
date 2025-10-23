#!/bin/bash

SCALE_INC=${1:-2}
STACK="crdt-app"
COMPOSES=("docker-compose-state.yml" "docker-compose-small-delta.yml" "docker-compose-big-delta.yml")

run_ramp_up() {
  local COMPOSE=$1
  local SERVICE="${STACK}_node"

  echo "=============================="
  echo "[RUN] Deploying $COMPOSE"
  echo "=============================="
  docker stack deploy -c "$COMPOSE" $STACK

  echo "[INFO] Waiting 3 minutes for startup..."
  sleep 180

  local CURRENT
  CURRENT=$(docker service inspect --format='{{.Spec.Mode.Replicated.Replicas}}' $SERVICE)
  local TARGET=$((CURRENT + SCALE_INC))

  echo "[INFO] Scaling $SERVICE up from $CURRENT to $TARGET replicas..."
  docker service scale $SERVICE=$TARGET

  echo "[INFO] Wait 3 minutes to observe scale..."
  sleep 180

  echo "[STOP] Removing stack $STACK"
  docker stack rm $STACK
  sleep 15
}

for COMPOSE in "${COMPOSES[@]}"; do
  run_ramp_up "$COMPOSE"
done

echo "[ALL DONE]"

