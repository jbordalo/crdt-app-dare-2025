# Comparative study of alternative delta CRDT models (DARE 2025) 

This is the implementation for the comparative study of alternative delta CRDT models developed as the final project of DARE 2025.

## Authors

- João Bordalo (j.bordalo@campus.fct.unl.pt)

## How to run

These instructions assume that nodes of the swarm will be executed in a single machine using the loopback interface (configured to have several virtual IP addresses, see instructions below). Similar instructions can be used in other environments. These instructions assume that IP multicast is available among the swarm elements (for automatically discovering a point of contact, if not a pair IP:PORT of the contact node must be provided as the value of the Contact parameter in the command line).

Start by building the executable jar using (Java JDK 21 or higher is required):

```console
mvn clean package -U
```

the first node can be executed (assuming a loopback interface with IP 127.0.0.1, or a local interface eth0):

```console
java -jar target/tardis-simple-usecase-emu-gym-ml-0.0.1.jar babel.address=127.0.0.1 GlobalMembership.Contact=none
```

Other nodes can be launched as such, assuming the virtual loopback IP address 127.0.0.2 exists. For other nodes the same command can be used, ensuring that each one uses a different virtual IP address that has been previously setup by the user.

```console
java -jar target/tardis-simple-usecase-emu-gym-ml-0.0.1.jar babel.address=127.0.0.2
```

Notice that if running on a cluster environemnt, where each swarm nodes executes on a single machine, the ``babel.address`` option in the command line can be replaced by ``babel.interface`` with other options remaining the same. For instance the commands above would translate to (assuming a network interface named ``eth0``):

```console
java -jar target/tardis-simple-usecase-emu-gym-ml-0.0.1.jar babel.interface=eth0 GlobalMembership.Contact=none

java -jar target/tardis-simple-usecase-emu-gym-ml-0.0.1.jar babel.interface=eth0
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
