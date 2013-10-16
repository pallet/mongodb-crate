(require
 '[pallet.crate.mongodb-test
   :refer [live-test-spec replica-test-spec arbiter-test-spec]]
 '[pallet.crates.test-nodes :refer [node-specs]])

(defproject mongodb-crate
  :provider node-specs                  ; supported pallet nodes
  :groups [(group-spec "mongodb-live-test"
                       :extends [with-automated-admin-user
                                 live-test-spec]
                       :roles #{:simple-test})
           (group-spec "mongo-replica-live-test"
                       :extends [with-automated-admin-user
                                 replica-test-spec]
                       :roles #{:replica-test}
                       :count 2)
           (group-spec "mongo-arbiter-live-test"
                       :extends [with-automated-admin-user
                                 arbiter-test-spec]
                       :roles #{:replica-test})])
