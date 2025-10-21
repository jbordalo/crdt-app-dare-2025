#!/bin/bash   

hosts=(moltres-05 moltres-06 moltres-07 moltres-08 moltres-09)

nNodes=${#hosts[@]}

workingDir=/home/jbordalo/crdt-app-dare-2025

for i in $(seq 0 $((nNodes-1))) 
do
    server=${hosts[$i]}
    echo "updating $server..."
    ssh $server "cd $workingDir && docker build -q -t crdt-app ."
done
