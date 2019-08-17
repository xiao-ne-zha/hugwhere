(ns org.to.vitory.db.hugwhere
  (:require [org.to.vitory.db.hug-impl :refer [where-parser to-sql]]
            [clojure.string :as str]))

(defn where [params where-clause]
  (to-sql params nil (where-parser where-clause)))
