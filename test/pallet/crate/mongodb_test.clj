(ns pallet.crate.mongodb-test
  (:require
   [clojure.test :refer :all]
   [pallet.crate.mongodb :as mongodb]
   [pallet.actions :refer [exec-checked-script package-manager plan-when]]
   [pallet.algo.fsmop :refer [complete?]]
   [pallet.build-actions :refer [build-actions]]
   [pallet.api :refer [execute-and-flag-metadata plan-fn server-spec]]
   [pallet.crate.network-service :refer [wait-for-port-listen]]
   [pallet.crate.etc-hosts :refer [set-hostname]]
   [pallet.crate.upstart :as upstart]
   [pallet.stevedore :refer [fragment]]))

(deftest minimal-test
  (is (build-actions {}
        (mongodb/settings {} {})
        (mongodb/install {})
        (mongodb/configure {})
        (mongodb/service :action :restart))))

(def live-test-spec
  (server-spec
   :extends [(mongodb/server-spec
              {:config {:verbose "true"
                        :vvvv "true"
                        :nohttpinterface false}} )]
   :phases {:install (plan-fn (package-manager :update))
            :test (plan-fn (wait-for-port-listen 28017))}))

(def replica-test-spec
  (server-spec
   :extends [(upstart/server-spec {})
             (mongodb/server-spec
              {:config {:replSet "rs1"}} )]
   :phases {:install (plan-fn
                       (package-manager :update)
                       (set-hostname :update-etc-hosts false)
                       (plan-when
                           (fragment
                            (pipe ("ps ax")("grep" dhclient)("grep" -v grep)))
                         (exec-checked-script
                          "Propagate hostname to DHCP"
                          ("service" networking restart))))
            :test (plan-fn (wait-for-port-listen 27017))}
   :phases-meta {:install (execute-and-flag-metadata :install)}))

(def arbiter-test-spec
  (server-spec
   :extends [(upstart/server-spec {})
             (mongodb/server-spec
              {:config {:replSet "rs1" :port 30000}
               :arbiter true})]
   :phases {:install (plan-fn
                       (package-manager :update)
                       (set-hostname :update-etc-hosts false)
                       (plan-when
                           (fragment
                            (pipe ("ps ax")("grep" dhclient)
                                  ("grep" -v grep)))
                         (exec-checked-script
                          "Propogate hostname to dhcp"
                          ("service" networking restart))))
            :test (plan-fn (wait-for-port-listen 30000))}
   :phases-meta {:install (execute-and-flag-metadata :install)}))


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

