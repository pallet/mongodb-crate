{:dev
 {:dependencies [[com.palletops/pallet "0.8.0-RC.7" :classifier "tests"]
                 [com.palletops/crates "0.1.1"]
                 [com.palletops/pallet-vmfest "0.3.0-beta.2"]
                 [ch.qos.logback/logback-classic "1.0.9"]]
  :plugins [[com.palletops/pallet-lein "0.8.0-alpha.1"]
            [com.palletops/lein-pallet-crate "0.1.0"]
            [lein-pallet-release "RELEASE"]
            [lein-resource "0.3.2"]]
  :aliases {"live-test-up"
            ["pallet" "up" "--phases" "install,configure,test"]
            "live-test-down" ["pallet" "down"]
            "live-test" ["do" "live-test-up," "live-test-down"]
            "replica-test-up"
            ["pallet" "up"
             "--phases" "install,configure,restart,init-replica-set,test"
             "--roles" "replica-test"]
            "replica-test-down"
            ["pallet" "down" "--roles" "replica-test"]}
  :test-selectors {:default (complement :live-test)
                   :live-test :live-test
                   :all (constantly true)}}}
