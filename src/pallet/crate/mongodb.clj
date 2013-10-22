(ns pallet.crate.mongodb
  (:require
   [clojure.pprint :refer [pprint]]
   [clojure.set :refer [intersection]]
   [clojure.string :as string]
   [clojure.tools.logging :as log :refer [debugf]]
   [pallet.actions :refer [exec exec-checked-script on-one-node
                           package-source package remote-file]]
   [pallet.api :as api :refer [execute-and-flag-metadata plan-fn]]
   [pallet.compute :refer [os-hierarchy]]
   [pallet.crate :refer [defplan assoc-settings defmulti-plan defmethod-plan
                         get-node-settings get-settings nodes-with-role
                         os-family service-phases target]]
   [pallet.crate-install :as crate-install]
   [pallet.crate.service :as service]
   [pallet.crate.upstart]
   [pallet.node :refer [primary-ip private-ip]]
   [pallet.utils :refer [apply-map deep-merge]]
   [pallet.version-dispatch
    :refer [defmethod-version-plan defmulti-version-plan]]))

(def default-version "2.4.6")
(def facility :mongodb)

(def mongo-config-changed-flag "MONGO-CONFIG-CHANGED")

(def default-mongodb-conf
  ;; from http://docs.mongodb.org/manual/reference/configuration-options/
  {:dbpath "/var/lib/mongodb"
   :logpath "/var/log/mongodb/mongodb.log"
   :logappend true
   :rest true
   :port 27017
   ;; :bind-ip will be bound to local ip if possible
   :nohttpinterface true})

(defmulti-version-plan default-settings [version])

(defmethod-version-plan default-settings {:os :debian-base}
  [os os-version version]
  {:config default-mongodb-conf
   :user "mongodb"
   :service-name "mongodb"
   :supervisor :initd
   :install-strategy :package-source
   :packages ["mongodb-10gen"]
   :package-options {:disable-service-start true}
   :package-source
   {:name "10gen"
    :aptitude
    {:url "http://downloads-distro.mongodb.org/repo/debian-sysvinit/"
     :release "dist"
     :scopes ["10gen"]
     :key-id "7F0CEB10"
     :key-server "keyserver.ubuntu.com"}}
   :conf-file "/etc/mongodb.conf"})

(defmethod-version-plan default-settings {:os :ubuntu}
  [os os-version version]
  {:config default-mongodb-conf
   :user "mongodb"
   :service-name "mongodb"
   :supervisor :upstart
   :install-strategy :package-source
   :packages ["mongodb-10gen"]
   :package-options {:disable-service-start true}
   :package-source
   {:name "10gen"
    :aptitude
    {:url "http://downloads-distro.mongodb.org/repo/ubuntu-upstart/"
     :release "dist"
     :scopes ["10gen"]
     :key-id "7F0CEB10"
     :key-server "keyserver.ubuntu.com"}}
   :conf-file "/etc/mongodb.conf"})

(defmethod-version-plan default-settings {:os :rh-base}
  [os os-version version]
  {:config (merge default-mongodb-conf
                  {:dbpath "/var/lib/mongo"
                   :logpath "/var/log/mongo/mongodb.log"})
   :user "mongod"
   :service-name "mongod"
   :supervisor :initd
   :install-strategy :package-source
   :packages ["mongo-10gen" "mongo-10gen-server"]
   :package-source
   {:name "mongodb"
    :yum
    {:url "http://downloads-distro.mongodb.org/repo/redhat/os/x86_64"
     :gpg-check 0
     :name "MongoDB Repository"}}
   :conf-file "/etc/mongod.conf"})


(defn to-config-file
  "Generate the .config file contents out of a map of config option, value"
  [config]
  (apply str
         (map (fn [[k v]]
                (format "%s=%s\n" (name k) v))
              config)))

(defplan settings
  "Build the configuration settings by merging the user supplied ones
  with the OS-related install settings and the default config settings
  for MongoDB"
  [{:keys [version config] :as settings}
   {:keys [instance-id] :as options}]
  (let [settings (deep-merge
                  {:version (or version :latest)}
                  (default-settings :latest)
                  settings)]
    (debugf "mongodb settings %s" settings)
    (assoc-settings facility settings {:instance-id instance-id})))

(defplan install
  [{:keys [instance-id]}]
  (let [settings (get-settings facility {:instance-id instance-id})]
    (crate-install/install facility instance-id)))

(defplan configure
  [{:keys [instance-id] :as options}]
  (let [{:keys [conf-file config user]}
        (get-settings facility {:instance-id instance-id})]
    (remote-file conf-file
                 :content (to-config-file config)
                 :owner user
                 :flag-on-changed mongo-config-changed-flag)))

(defmacro file-ns [] (name (ns-name *ns*)))

(defn role-for-replica-set
  "Return a role keyword for the specified replica-set name"
  [replica-set]
  (keyword (file-ns) (str "mongo-rs-" replica-set)))

(defn config-for-node
  [node instance-id]
  (get-node-settings node facility {:instance-id instance-id}))

(defn ip-port
  [target instance-id]
  (debugf "ip-port %s" target)
  (let [port (-> (config-for-node (:node target) instance-id) :config :port)]
    (str (or (private-ip (:node target))
             (primary-ip (:node target)))
         ":"
         port)))

(def report-fn
  "report = function(v, s) {
    print(s)
    printjson(v)
    if (v.ok == 0) { throw new Error(s + ': ' + v.errmsg) }
  }
")

(defplan init-replica-set
  [{:keys [instance-id] :as opts}]
  (let [{:keys [config]}
        (get-settings facility {:instance-id instance-id})
        replica-set (:replSet config)
        role-kw (role-for-replica-set replica-set)]
    (debugf "replica-set %s" replica-set)
    (when replica-set
      (assert
       replica-set
       "init-replica-set called on settings with no :replica-set defined.")
      (let [replica-nodes (nodes-with-role role-kw)
            arbiter-nodes (nodes-with-role ::arbiter)
            arbiter-node (first (intersection (set replica-nodes)
                                              (set arbiter-nodes)))
            data-nodes (disj (set replica-nodes) arbiter-node)]
        (debugf "init-replica-set arbiter-node %s" (boolean arbiter-node))
        (on-one-node [role-kw ::data]
          (remote-file
           "init-replica-set.js"
           :content (str report-fn "
  print('Current replica set status')
  printjson(rs.status())
  if (rs.status().ok != 1) { report(rs.initiate(), 'initiate') }
  n=20
  while (n > 0 &&
         (rs.status().ok == 0 ||
          rs.status().myState != 1)) {
    print('.')
    sleep(2000)
    n=n-1
  }
  print('Final replica set status')
  report(rs.status(), 'status')" \newline))
          (exec-checked-script
           (str "Initialise replica set " replica-set)
           ("mongo" "--port" ~(:port config) "init-replica-set.js"))

          (doseq [target (remove #(= (target) %) data-nodes)]
            (exec
             {:language "js" :interpreter "/usr/bin/mongo"
              :interpreter-args ["--port" (:port config)]}
             (format
              "%s
               printjson(rs.status())
               report(rs.add(\"%s\"), 'add')"
              report-fn
              (ip-port target instance-id))))
          (when arbiter-node
            (exec
             {:language "js" :interpreter "/usr/bin/mongo"
              :interpreter-args ["--port" (:port config)]}
             (format
              "%s
               printjson(rs.status())
               report(rs.add(\"%s\", true), 'addArb') "
              report-fn
              (ip-port arbiter-node instance-id)))))))))

(defplan service
  [& {:keys [instance-id] :as options}]
  (let [{:keys [supervision-options] :as settings}
        (get-settings facility {:instance-id instance-id})]
    (service/service settings (merge supervision-options
                                     (dissoc options :instance-id)))))

(defplan restart-if-changed
  [& {:keys [instance-id] :as options}]
  (service :instance-id instance-id :if-flag mongo-config-changed-flag))

(defn server-spec
  "Return a server spec for MongoDB.  Keys under the :config settings
will be written to the mongo .conf file.  To use a replica set,
specify a replica set name in the :replSet config key. Specify
`:arbiter true` for an arbiter node (which is distinguished by the
arbiter role.)"
  [{:keys [arbiter instance-id] :as settings}]
  (let [settings (dissoc settings :instance-id)]
    (api/server-spec
     ;; TODO - needs an adjust-replica-set to run on resize of
     ;; replica set
     :phases
     (merge
      {:settings (plan-fn
                   (pallet.crate.mongodb/settings
                    settings {:instance-id instance-id}))
       :install (plan-fn
                  (install {:instance-id instance-id}))
       :configure (plan-fn
                    (configure {:instance-id instance-id}))
       :restart-if-chnaged (plan-fn
                             (restart-if-changed :instance-id instance-id))
       :init-replica-set (vary-meta
                          (plan-fn
                            (init-replica-set {:instance-id instance-id}))
                          merge
                          (execute-and-flag-metadata ::replica-set))}
      (service-phases facility {:instance-id instance-id} service))
     :default-phases [:install :configure :restart-if-changed :init-replica-set]
     :roles (set
             (filter identity
                     [(when-let [replica-set (-> settings :config :replSet)]
                        (role-for-replica-set replica-set))
                      (if arbiter
                        ::arbiter
                        ::data)])))))
