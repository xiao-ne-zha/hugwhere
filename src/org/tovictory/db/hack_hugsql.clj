(ns org.tovictory.db.hack-hugsql
  (:require [robert.hooke :refer [add-hook]]
            [clojure.string :as str]
            [hugsql.parser :as hp]
            [org.tovictory.db.hug-params]))

(defn- hack-pdef [{sql :sql {req :require nm :name} :hdr :as pdef}]
  (let [dynamic-where (= ":D" (last nm))
        req (if dynamic-where (conj req "[org.tovictory.db.hugwhere :refer [where]]") req)
        sql (if dynamic-where
              (mapv #(if (vector? %)
                       (let [[s e] %]
                         (if (and (= :end e) (or (str/starts-with? s "[") (str/starts-with? s "where ")))
                           [(format "(where params \"%s\")" s) :end]
                           %))
                       %)
                    sql)
              sql)]
    (assoc (assoc-in pdef [:hdr :require] req) :sql sql)))

(defn- parse-hook
  ([f sql] (parse-hook f sql {}))
  ([f sql opts]
   (let [pdefs (f sql opts)]
     (mapv hack-pdef pdefs))))

(defn hack-hugsql []
  (add-hook #'hp/parse #'parse-hook))
