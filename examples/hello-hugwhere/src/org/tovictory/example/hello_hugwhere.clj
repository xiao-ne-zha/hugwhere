(ns org.tovictory.example.hello-hugwhere
  (:require [org.tovictory.db.easysql :refer [easysql->hugsql]]
            [org.tovictory.db.hug-params] ;; 引入此命名空间主要是为了使like参数生效
            [conman.core :as conman]
            [hugsql.core :as h :refer [def-sqlvec-fns]]
            [mount.core :refer [defstate] :as m]
            [clojure.pprint :refer [print-table]]
            [clojure.java.io :as io])
  (:gen-class))

(def pool-spec {:jdbc-url "jdbc:h2:./db/dev_user.db"})

(defstate ^:dynamic *db*
  :start (conman/connect! pool-spec)
  :stop (conman/disconnect! *db*))

;; Read queries.sql, convert using easysql->hugsql, and write to queries_gen.sql in resources directory
(let [queries-content (slurp (io/resource "sql/queries.sql"))
      converted-content (easysql->hugsql queries-content)]
  (spit "resources/sql/queries_gen.sql" converted-content)
  (conman/bind-connection 
   *db* 
   {:require-str "[org.tovictory.db.hugwhere :refer [smart-block order-by not-nil? contain-para-name?]]"} 
   "sql/queries_gen.sql")
  (def-sqlvec-fns "sql/queries_gen.sql"
    {:require-str "[org.tovictory.db.hugwhere :refer [smart-block order-by not-nil? contain-para-name?]]"}))

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
