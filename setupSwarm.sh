#!/bin/bash

hosts=(moltres-05 moltres-06 moltres-07 moltres-08 moltres-09)

# Read the arguments
#IFS=',' read -r -a hosts <<< $hosts

# Leave a potentially existing swarm
docker swarm leave --force

# Run docker swarm init on the local machine and capture the output
echo "Initializing swarm on the local machine..."
init_output=$(docker swarm init 2>&1)

# Check if the swarm init command was successful
if [ $? -ne 0 ]; then
    echo "Failed to initialize swarm."
    echo "$init_output"
    exit 1
fi

# Extract the join token and manager IP from the output
echo "$init_output"
SWARM_TOKEN=$(echo "$init_output" | grep 'docker swarm join --token' | awk '{print $5}')
MANAGER_IP=$(echo "$init_output" | grep 'docker swarm join --token' | awk '{print $6}')

# Check if we successfully extracted the token and IP
if [ -z "$SWARM_TOKEN" ] || [ -z "$MANAGER_IP" ]; then
    echo "Failed to extract the swarm join token or manager IP."
    exit 1
fi

echo "Swarm initialized. Join token: $SWARM_TOKEN"
echo "Manager IP: $MANAGER_IP"

# Loop through each host and join the swarm
for host in "${hosts[@]:1}"; do
    echo "Processing $host."
    
    ssh "$host" "docker swarm leave --force"
    ssh "$host" "docker swarm join --token $SWARM_TOKEN $MANAGER_IP"
    
    echo "Processed $host."
done

echo "All hosts have been processed."
