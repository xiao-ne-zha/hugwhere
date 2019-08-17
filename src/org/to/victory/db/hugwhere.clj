(ns org.to.victory.db.hugwhere
  (:require [org.to.victory.db.hug-impl :refer [where-parser to-sql]]
            [clojure.string :as str]))

(defn where [params where-clause]
  (to-sql params nil (where-parser where-clause)))
