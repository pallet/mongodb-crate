## Usage

Supports standalone or replica set modes.

The mongodb crate provides a `server-spec` function that returns a
server-spec. This server spec will install and run the mongodb server.
You pass a map of options to configure mongodb.  The options are as
for the `settings` function.

The `server-spec` provides an easy way of using the crate functions, and you can
use the following crate functions directly if you need to.

The `settings` function provides a plan function that should be called in the
`:settings` phase.  The function puts the configuration options into the pallet
session, where they can be found by the other crate functions, or by other
crates wanting to interact with mongodb.

The `install` function is responsible for actually installing mongodb.

## Settings

`:install-strategy`
: used to control how mongodb will be installed.  Defaults to using
  the 10gen package sources.  See `pallet.crate-install` for other
  install strategies.

`:config`
: a map of options that will be used to generate the mongodb conf
  file.  The keys in the map are keywords named after the relevant
  mongodb conf file entry.  If `:replSet` is specified, then the
  replica set will be initialised. See
  http://docs.mongodb.org/manual/reference/configuration-options/.

`:supervisor`
: used to set the supervisor mongodb is run under.  Defaults to
  `:upstart` on ubuntu, and `:initd` otherwise.

## Live test on vmfest

For example, to run the live test on VMFest, using Ubuntu 13:

```sh
lein with-profile +vmfest pallet up --selectors ubuntu-13
lein with-profile +vmfest pallet down --selectors ubuntu-13
```
