(ns org.tovictory.db.hugwhere
  (:require [org.tovictory.db.parablock :refer [xf-statement]]))

(defn where [params sql]
  (let [result (xf-statement params sql)]
    (if (empty? result)
      nil
      result)))
