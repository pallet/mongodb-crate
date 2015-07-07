## 0.8.0-alpha.7

- Update to pallet 0.8.2

- Ensure initialisation doesn't run when adding node
  When adding a mongodnb node to an existing cluster, ensure that the 
  initialisation doesn't run by checking for replica-set node metadata.

## 0.8.0-alpha.6

- Fix python-devel dependency on amazon linux

## 0.8.0-alpha.5

- Ensure mongo is up before initialising replica set
  When initialising the replica set, ensure mongo is up first.

## 0.8.0-alpha.4

- Fix package names for redhat based distros

## 0.8.0-alpha.3

- Add support for runit

# 0.8.0-alpha.2

- Add support for mongo MMS agent install

- Improve maintenance of replica set members
  When nodes are added or removed, this should be reflected in the replica
  set configuration.

- Update to pallet 0.8.0-RC.7

# 0.8.0-alpha.1

- Initial release
