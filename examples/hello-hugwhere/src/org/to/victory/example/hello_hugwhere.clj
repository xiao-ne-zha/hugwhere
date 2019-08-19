(ns org.to.victory.example.hello-hugwhere
  (:require [org.to.victory.db.hack-hugsql :as hh]
            [conman.core :as conman]
            [mount.core :refer [defstate] :as m]
            [clojure.pprint :refer [print-table]])
  (:gen-class))

(hh/hack-hugsql)

(def pool-spec {:jdbc-url "jdbc:h2:./db/dev_user.db"})

(defstate ^:dynamic *db*
  :start (conman/connect! pool-spec)
  :stop (conman/disconnect! *db*))

(conman/bind-connection *db* "sql/queries.sql")

(defn -main
  [& args]
  (m/start)
  (println "(list-users {:id 4}):")
  (print-table (list-users {:id 4}))
  (println "(list-users {:name \"a\"}):")
  (print-table (list-users {:name "a"}))
  (println "(list-users2 {:id 4}):")
  (print-table (list-users2 {:id 4}))
  (println "(list-users2 {:name \"a\"}):")
  (print-table (list-users2 {:name "a"})))
