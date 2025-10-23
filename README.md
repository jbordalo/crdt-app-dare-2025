# Comparative study of alternative delta CRDT models (DARE 2025) 

This is the implementation for the comparative study of alternative delta CRDT models developed as the final project of DARE 2025.

## Authors

- João Bordalo (j.bordalo@campus.fct.unl.pt)

## How to run

Start by building the executable jar using (Java JDK 21 or higher is required):

```console
mvn clean package -U
```

the first node can be executed (assuming a loopback interface with IP 127.0.0.1, or a local interface eth0):

```console
java -jar target/crdt-dare-project-0.0.1.jar babel.address=127.0.0.1 HyParView.contact=none
```

Other nodes can be launched as such, assuming the virtual loopback IP address 127.0.0.2 exists. For other nodes the same command can be used, ensuring that each one uses a different virtual IP address that has been previously setup by the user.

```console
java -jar target/crdt-dare-project-0.0.1.jar babel.address=127.0.0.2 HyParView.contact=127.0.0.1:5555
```

Notice that if running on a cluster environemnt, where each swarm nodes executes on a single machine, the ``babel.address`` option in the command line can be replaced by ``babel.interface`` with other options remaining the same. For instance the commands above would translate to (assuming a network interface named ``eth0``):

```console
java -jar target/crdt-dare-project-0.0.1.jar babel.interface=eth0 HyParView.contact=none

java -jar target/crdt-dare-project-0.0.1.jar babel.interface=eth0 HyParView.contact=127.0.0.1:5555
```

To create an additional virtual loopback address, one can use the command:

### Mac OS
```console
sudo ifconfig lo0 alias 127.0.0.2
```
### Linux
```console
sudo ip addr add 127.0.0.2/8 dev lo
```

### Others
You can look at the execution scripts, `run_contact.sh` and `run_node.sh` for more information.
