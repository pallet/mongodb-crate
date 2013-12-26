(ns pallet.crate.mongodb-test
  (:require
   [pallet.crate.mongodb :as mongodb]
   [pallet.actions :refer [package-manager]]
   [pallet.algo.fsmop :refer [complete?]]
   [pallet.api :refer [plan-fn server-spec]]
   [pallet.crate.network-service :refer [wait-for-port-listen]]
   [clojure.test :refer [deftest] :as test]))

(def live-test-spec
  (server-spec
   :extends [(mongodb/server-spec {:config {:verbose "true"
                                             :vvvv "true"}} )]
   :phases {:install (plan-fn (package-manager :update))
            :test (plan-fn (wait-for-port-listen 28017))}))



(deftest config-file

  (test/are [mapconfig expected-text]

    (=  (mongodb/to-config-file mapconfig) expected-text)

    {:name "value"}
    "name=value\n"

    (sorted-map :name "value" :setParameter "logLevel=4")
    (str "name=value\n"
         "setParameter=logLevel=4\n")


    ;; Vector values expand to multiple lines of the same configuration key
    (sorted-map :name "value" :setParameter ["logLevel=4" "textSearchEnabled=true"])
    (str "name=value\n"
         "setParameter=logLevel=4\n"
         "setParameter=textSearchEnabled=true\n")) )
