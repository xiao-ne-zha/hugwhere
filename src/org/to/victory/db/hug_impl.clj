(ns org.to.victory.db.hug-impl
  (:require  [clojure.string :as str]
             [instaparse.core :refer [defparser]]
             [clojure.java.io :as io]))

(defparser ^:private hwp (slurp (io/resource "hugwhere.bnf")) :auto-whitespace :standard)
(def where-parser (memoize hwp))

(defmulti to-sql (fn [params opts ast] (first ast)) :default nil)

(defmethod to-sql nil [params opts ast] (when (string? ast) ast))

(defmethod to-sql :where-clause [params opts [_ w c]]
  (let [[k sql] (to-sql params opts c)]
    (when-not (str/blank? sql)
      (str "where " sql))))

(defmethod to-sql :conds [params {:keys [_param-sens] :as opts} [_ & cs]]
  (let [sqls (map (partial to-sql params opts) cs)
        sqls (partition 2 (cons "and" sqls))
        need-const-cond (if _param-sens
                          (not-empty (filter (fn [[o [k v]]] (and k v)) sqls))
                          true)
        sql (reduce
             (fn [r [o [k c]]]
               (if (or k need-const-cond)
                 (if (str/blank? c) r (str r " " o " " c))
                 r))
             ""
             sqls)]
    [need-const-cond (str/replace-first sql #"\s*(and|or)\s*" "")]))

(defmethod to-sql :cond [params opts [_ co c cp]]
  (if c
    (let [[k sql] (to-sql params opts c)]
      (when-not (str/blank? sql) (str co sql cp)))
    (to-sql params opts co)))

(defmethod to-sql :atom-cond [params opts [_ le co re]]
  (let [[k sql] (to-sql params opts re)]
    [k (when-not (str/blank? sql) (str le " " co " " sql))]))

(defmethod to-sql :sensitive-conds [params opts [_ ob cs cb]]
  (when-let [cs (to-sql params (assoc opts :_param-sens true) cs)]
    cs))

(defmethod to-sql :re [params opts [_ re]]
  (if (vector? re) (to-sql params nil re)
      [nil re]))

(defmethod to-sql :KID [params opts [_ kid]]
  (let [k (keyword (str/replace-first kid #"(:[-\w]+\*?)?:(\S+)" "$2"))]
    [k (when (get params k) kid)]))
