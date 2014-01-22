(ns pallet.crate.mongodb.mms
  "Install MongoDB MMS
See: http://mms.mongodb.com/help/monitoring/"
  (:require
   [clojure.string :as string]
   [clojure.tools.logging :as log :refer [debugf]]
   [pallet.actions :as actions :refer [directory exec-checked-script]]
   [pallet.api :as api :refer [plan-fn]]
   [pallet.crate
    :refer [defplan assoc-settings defmethod-plan get-settings service-phases]]
   [pallet.crate-install :as crate-install :refer [install-from]]
   [pallet.crate.nohup]
   [pallet.crate.service
    :refer [supervisor-config supervisor-config-map] :as service]
   [pallet.utils :refer [apply-map deep-merge]]
   [pallet.version-dispatch
    :refer [defmethod-version-plan defmulti-version-plan]]))

(def facility ::mms)
(def mongomms-config-changed-flag "MONGOMMS-CONFIG-CHANGED")

(def default-mms-conf
  {:supervisor :nohup
   :service-name "mongomms"
   :user "mongomms"
   :owner "mongomms"
   :group "mongomms"
   ;; hmac only required for python 2.4.  Install via easy_install recommended.
   :install-strategy :archive
   :install-source {:local-file "mms-monitoring-agent.tar.gz"
                    :owner "mongomms"
                    :group "mongomms"}
   :install-dir "/opt/mongomms"
   :log-dir "/var/log/mongomms"
   })

(defmulti-version-plan default-settings [version])

(defmethod-version-plan default-settings {:os :debian-base}
  [os os-version version]
  (merge default-mms-conf
         {:dependencies {:pymongo {:install-strategy :packages
                                   :packages ["python-pymongo"]}}}))

(defmethod-version-plan default-settings {:os :ubuntu}
  [os os-version version]
  (merge default-mms-conf
         {:dependencies {:pymongo {:install-strategy :packages
                                   :packages ["python-pymongo"]}}}))

(defmethod-version-plan default-settings {:os :rh-base}
  [os os-version version]
  (merge default-mms-conf
         {:dependencies {:python-hashlib {:install-strategy :package-source
                                          :repository {:id :epel
                                                       :version "6.8"}
                                          :packages ["python-hashlib"]}
                         :python-pymongo {:install-strategy :package-source
                                          :repository {:id :epel
                                                       :version "6.8"}
                                          :packages ["python-pymongo"]}}}))

(defmethod-version-plan default-settings {:os :amzn-linux}
  [os os-version version]
  (merge default-mms-conf
         {:dependencies {:python-setuptools {:install-strategy :packages
                                             :packages ["python-setuptools"
                                                        "gcc"
                                                        "python-devel"]}
                         :python-pymongo {:install-strategy ::easy-install
                                          :packages ["pymongo"]}}}))

(defmethod-plan crate-install/install-from ::easy-install
  [{:keys [packages] :as settings}]
  (exec-checked-script
   (str "Install via easy-install " (string/join ", " packages))
   ("easy_install" ~@packages)))


(defmethod supervisor-config-map [facility :runit]
  [_ {:keys [log-dir run-command service-name user] :as settings} options]
  {:service-name service-name
   :run-file
   {:content (str "#!/bin/sh\nexec 2>&1\nexec chpst -u " user " " run-command)
    :literal true}
   :log-run-file
   {:content (str "#!/bin/sh\nexec chpst -u " user " svlogd -tt " log-dir)
    :literal true}})

(defmethod supervisor-config-map [facility :nohup]
  [_ {:keys [run-command service-name user] :as settings} options]
  {:service-name service-name
   :run-file {:content run-command}
   :user user})

(defn computed-defaults
  [{:keys [install-dir run-command] :as settings}]
  (let [settings (if run-command
                   settings
                   (assoc settings :run-command
                          (str "python " install-dir "/agent.py")))]
    settings))

(defplan settings
  "Build the configuration settings by merging the user supplied ones
  with the OS-related install settings and the default config settings
  for MongoDB"
  [{:keys [version config] :as settings}
   {:keys [instance-id] :as options}]
  (let [settings (deep-merge
                  (default-settings :latest)
                  settings)
        settings (computed-defaults settings)]
    (debugf "mongomms settings %s" settings)
    (assoc-settings facility settings {:instance-id instance-id})
    (supervisor-config facility settings (or options {}))))

;;; # User
(defplan user
  "Create the mongomms user"
  [{:keys [instance-id] :as options}]
  (let [{:keys [user owner group home]} (get-settings facility options)]
    (assert (string? user) "user must be a username string")
    (assert (string? owner) "owner must be a username string")
    (actions/group group :system true)
    (debugf "mongomms create owner %s" owner)
    (when (not= owner user)
      (actions/user owner :group group :system true))
    (debugf "mongomms create user %s" user)
    (actions/user
     user :group group :system true :create-home true :shell :bash)))

;;; # Install
(defplan install
  [{:keys [instance-id]}]
  (let [{:keys [dependencies log-dir owner group] :as settings}
        (get-settings facility {:instance-id instance-id})]
    (directory log-dir :owner owner :group group)
    (doseq [[dependency dep-settings] dependencies]
      (install-from dep-settings)))
  (crate-install/install facility instance-id))


(defplan configure
  [{:keys [instance-id] :as options}]
  (let [{:keys [conf-file config user]}
        (get-settings facility {:instance-id instance-id})]
    ))

(defplan service
  [& {:keys [instance-id] :as options}]
  (let [{:keys [supervision-options] :as settings}
        (get-settings facility {:instance-id instance-id})]
    (service/service settings (merge supervision-options
                                     (dissoc options :instance-id)))))

(defplan ensure-service
  [& {:keys [instance-id] :as options}]
  (service :instance-id instance-id :if-stopped true)
  (service :instance-id instance-id
           :action :reload
           :if-flag mongomms-config-changed-flag))


(defn server-spec
  "Return a server spec for MongoDB.  Keys under the :config settings
will be written to the mongo .conf file.  To use a replica set,
specify a replica set name in the :replSet config key. Specify
`:arbiter true` for an arbiter node (which is distinguished by the
arbiter role.)"
  [{:keys [instance-id] :as settings}]
  (let [settings (dissoc settings :instance-id)]
    (api/server-spec
     :phases
     (merge
      {:settings (plan-fn
                   (pallet.crate.mongodb.mms/settings
                    settings {:instance-id instance-id}))
       :install (plan-fn
                 (user {:instance-id instance-id})
                 (install {:instance-id instance-id}))
       :configure (plan-fn
                   (configure {:instance-id instance-id})
                   (service :action :enable :instance-id instance-id))
       :restart-if-changed (plan-fn
                             (ensure-service :instance-id instance-id))}
      (service-phases facility {:instance-id instance-id} service))
     :default-phases [:install :configure :restart-if-changed]
     :roles #{:mms})))
