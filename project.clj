(defproject com.palletops/mongodb-crate "0.8.0-alpha.5"
  :description "Pallet crate to install, configure and use mongodb"
  :url "http://palletops.com/pallet"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [com.palletops/pallet "0.8.0-RC.7"]
                 [com.palletops/upstart-crate "0.8.0-alpha.2"]]
  :resource {:resource-paths ["doc-src"]
             :target-path "target/classes/pallet_crate/mongodb_crate/"
             :includes [#"doc-src/USAGE.*"]}
  :prep-tasks ["resource" "crate-doc"])
