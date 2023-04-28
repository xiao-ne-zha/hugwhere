(ns org.tovictory.example.hello-hugwhere
  (:require [org.tovictory.db.hack-hugsql :as hh]
            [conman.core :as conman]
            [hugsql.core :as h :refer [def-sqlvec-fns]]
            [mount.core :refer [defstate] :as m]
            [clojure.pprint :refer [print-table]]
            [clojure.java.io :as io])
  (:gen-class))

(hh/hack-hugsql)

(def pool-spec {:jdbc-url "jdbc:h2:./db/dev_user.db"})

(defstate ^:dynamic *db*
  :start (conman/connect! pool-spec)
  :stop (conman/disconnect! *db*))

(conman/bind-connection *db* "sql/queries.sql")
(def-sqlvec-fns (io/resource "sql/queries.sql"))

(defn -main
  [& args]
  (m/start)
  (println "(list-users):" (list-users-sqlvec))
  (print-table (list-users))
  (println "(list-users {:id 4}):" (list-users-sqlvec {:id 4}))
  (print-table (list-users {:id 4}))
  (println "(list-users {:name \"a\"}):" (list-users-sqlvec {:name "a"}))
  (print-table (list-users {:name "a"}))

  (println "(list-users2):" (list-users2-sqlvec))
  (print-table (list-users2))
  (println "(list-users2 {:id 4}):" (list-users2-sqlvec {:id 4}))
  (print-table (list-users2 {:id 4}))
  (println "(list-users2 {:name \"a\"}):" (list-users2-sqlvec {:name "a"}))
  (print-table (list-users2 {:name "a"})))
