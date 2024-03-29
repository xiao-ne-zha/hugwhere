(ns org.tovictory.db.hugwhere
  (:require [org.tovictory.db.parablock :refer [xf-statement] :as pb]
            [clojure.string :as str]
            [camel-snake-kebab.core :as csk]
            [hugsql.parameters :refer [identifier-param-quote] :as hp]))

(def not-nil? pb/not-nil?)
(def contain-para-name? pb/contain-para-name?)

(defn smart-block
  ([params sql] (smart-block params nil sql))
  ([params options sql]
   (let [result (xf-statement params options sql)]
     (if (empty? result)
       nil
       result))))

(defn order-by-fn
  ([order-by-key params] (order-by-fn order-by-key params {:quoting :ansi}))
  ([order-by-key params options]
   (when-let [obs (get params order-by-key)]
     (let [obs (if (string? obs) [obs] obs)
           obs (map #(if (or (string? %) (keyword? %)) [%] %) obs)
           obs (map (fn [[col asc]]
                      (let [asc (when (string? asc) (str/lower-case asc))
                            asc (#{"asc" "desc"} asc)
                            col (-> col name csk/->snake_case)]
                        (str (identifier-param-quote col options) \space asc)))
                    obs)]
       (str "order by " (str/join "," obs))))))

(defmacro order-by
  ([order-by-key]
   `(order-by ~order-by-key {:quoting :ansi}))
  ([order-by-key options]
   (list order-by-fn order-by-key 'params options)))
