(ns pallet.crate.mongodb
  (:require [clojure.tools.logging :as log]
            [pallet.api :refer [plan-fn] :as api])
  (:use [pallet.crate :only [defplan assoc-settings defmulti-plan defmethod-plan
                             os-family get-settings]]
        [pallet.crate-install :only [install]]
        [pallet.actions :only [package-source package remote-file service]]
        [pallet.compute :only [os-hierarchy]]
        [clojure.pprint :only [pprint]]
        [pallet.utils :only [deep-merge]]))

(def default-version "2.2.3")
(def facility :mongodb)

(def default-mongodb-conf
  ;; from http://docs.mongodb.org/manual/reference/configuration-options/
  {:dbpath "/var/lib/mongodb"
   :logpath "/var/log/mongodb/mongodb.log"
   :logappend true
   :rest true})

(defn to-config-file
  "Generate the .config file contents out of a map of config option, value"
  [config]
  (apply str
         (map (fn [[k v]]
                (format "%s=%s\n" (name k) v))
              config)))

(def packages-version
  {:latest ["mongodb-10gen"]
   :1.8 ["mongodb18-10gen"]
   :2.0 ["mongodb20-10gen"]})

(defn packages-from-version
  "What package name to use given a version of MongoDB. Different
  versions use different package names"
  [version]
  (get packages-version version))

(defmulti-plan install-settings
  (f [version]
     (os-family))
  :hierarchy #'os-hierarchy)

(defmethod-plan install-settings :ubuntu [version]
  ;; use package-source
  {:install-strategy :package-source
   :packages (packages-from-version version)
   :package-source {:name "10gen"
                    :aptitude
                    {:url "http://downloads-distro.mongodb.org/repo/ubuntu-upstart/"
                     :release "dist"
                     :scopes ["10gen"]
                     :key-id "7F0CEB10"
                     :key-server "keyserver.ubuntu.com"}}})

(defmethod-plan install-settings :debian-base [version]
  ;; use package-source
  {:install-strategy :package-source
   :packages (packages-from-version version)
   :package-source {:name "10gen"
                    :aptitude
                    {:url "http://downloads-distro.mongodb.org/repo/debian-sysvinit"
                     :release "dist"
                     :scopes ["10gen"]
                     :key-id "7F0CEB10"
                     :key-server "keyserver.ubuntu.com"}}})

(defplan build-settings
  "Build the configuration settings by merging the user supplied ones
  with the OS-related install settings and the default config settings
  for MongoDB"
  [{:keys [version config] :as settings}
   {:keys [instance-id] :as options}]
  (let [install-settings (install-settings :latest)
        config (merge default-mongodb-conf config)
        settings (deep-merge {:version (or version :latest)}
                        install-settings
                        settings
                        {:config config})]
    (assoc-settings :mongodb settings {:intance-id instance-id})))

(defplan install-mongodb
  [& {:keys [instance-id]}]
  (let [settings (get-settings :mongodb {:instance-id instance-id})]
    (install :mongodb instance-id)))

(defplan configure [& opts]
  (let [config (:config (get-settings :mongodb))]
    (remote-file "/etc/mongodb.conf"
                 :content (to-config-file config))))

(defn server-spec
  [settings & {:keys [instance-id] :as options}]
  (api/server-spec
   :phases
   {:settings (plan-fn
               (build-settings settings (or options {})))
    :install (plan-fn
              (install-mongodb :instance-id instance-id))
    :configure (plan-fn
                (configure options))
    :start (plan-fn (service "mongodb" :action :start))
    :stop (plan-fn (service "mongodb" :action :stop))
    :restart (plan-fn (service "mongodb" :action :restart)) }

   :roles #{ :mongodb }))
