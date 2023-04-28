(ns org.tovictory.db.hugwhere
  (:require [org.tovictory.db.hug-impl :refer [to-sql]]))

(defn where [params where-clause]
  (to-sql params where-clause))
