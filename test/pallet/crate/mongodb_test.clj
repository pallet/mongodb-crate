(ns pallet.crate.mongodb-test
  (:require
   [pallet.crate.mongodb :as mongodb]
   [pallet.actions :refer [package-manager]]
   [pallet.algo.fsmop :refer [complete?]]
   [pallet.api :refer [plan-fn server-spec]]
   [pallet.crate.network-service :refer [wait-for-port-listen]]
   [clojure.test :as test]))

(def live-test-spec
  (server-spec
   :extends [(mongodb/server-spec {:config {:verbose "true"
                                             :vvvv "true"}} )]
   :phases {:install (plan-fn (package-manager :update))
            :test (plan-fn (wait-for-port-listen 28017))}))