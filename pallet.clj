(require
 '[pallet.crate.mongodb-test :refer [live-test-spec]]
 '[pallet.crates.test-nodes :refer [node-specs]])

(defproject mongodb-crate
  :provider node-specs                  ; supported pallet nodes
  :groups [(group-spec "mongodb-live-test"
                       :extends [with-automated-admin-user
                                 live-test-spec])])