(use 'pallet.crate.mongodb-test 'pallet.repl 'pallet.crate.automated-admin-user) 
(use-pallet)

(def vmfest (compute-service :vmfest))

(def mongodb-ubuntu (group-spec "mongodb-ubuntu-test"
                                :node-spec {:image {:image-id :ubuntu-12.04}}
                                :extends [with-automated-admin-user
                                          live-test-spec]))

(converge {mongodb-ubuntu 1}
          :compute vmfest
          :phase [:install :configure :restart :test ] )

;; debian
(def mongodb-debian (group-spec "mongodb-debian-test"
                                :node-spec {:image
                                            {:image-id :debian-6.0.2.1-64bit-v0.3}}
                                :extends [with-automated-admin-user
                                          live-test-spec]))

(converge {mongodb-debian 1}
          :compute vmfest
          :phase [:install :configure :restart :test ] )

(converge {mongodb-ubuntu 0 mongodb-debian 0} :compute vmfest)