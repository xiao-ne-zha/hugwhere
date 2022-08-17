(ns org.to.victory.db.hugwhere
  (:require [org.to.victory.db.hug-impl :refer [to-sql]]
            [clojure.string :as str]))

(defn where [params where-clause]
  (to-sql params where-clause))
