(defproject com.palletops/mongodb-crate "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [com.palletops/pallet "0.8.0-beta.7"]
                 ;[com.palletops/java-crate "0.8.0-beta.2"]
                 ]
  :profiles {:dev {:dependencies
                   [[ch.qos.logback/logback-classic "1.0.9"]
                    [org.cloudhoist/pallet-vmfest "0.3.0-alpha.3"]
                    [org.clojars.tbatchelli/vboxjxpcom "4.2.4"]]}}
  :repositories {"sonatype"
                 {:url "https://oss.sonatype.org/content/repositories/releases/"
                  :snapshots false}})
