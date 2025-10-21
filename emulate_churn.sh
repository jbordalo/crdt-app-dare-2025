#!/bin/bash

N=$1
WAIT=${2:-10}

SERVICE="crdt-app_node"

if [ -z "$N" ]; then
  echo "Usage: $0 N [WAIT]"
  exit 1
fi

# current replicas
CURRENT=$(docker service inspect --format='{{.Spec.Mode.Replicated.Replicas}}' $SERVICE)

if (( N >= CURRENT )); then
  echo "[ERROR]: N ($N) must be smaller than current replicas ($CURRENT)"
  exit 1
fi

TARGET=$((CURRENT - N))

echo "[INFO] Scaling $SERVICE down from $CURRENT to $TARGET replicas."
docker service scale $SERVICE=$TARGET

echo "[INFO] Waiting for $WAIT seconds."
sleep $WAIT

echo "[INFO] Scaling $SERVICE back up to $CURRENT replicas."
docker service scale $SERVICE=$CURRENT

echo "Done."
