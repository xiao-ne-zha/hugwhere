(ns org.to.victory.db.hug-impl
  (:require  [clojure.string :as str]
             [instaparse.core :refer [defparser]]
             [clojure.java.io :as io]))

(defparser ^:private hwp (slurp (io/resource "hugwhere.bnf")) :auto-whitespace :standard)
(def where-parser (memoize hwp))

(defmulti to-sql (fn [params opts ast] (first ast)) :default nil)

(defmethod to-sql nil [params opts ast]
  (if (vector? ast) (to-sql params opts (second ast)) [nil ast]))

(defmethod to-sql :where-clause [params opts [_ w c]]
  (let [[k sql] (to-sql params opts c)]
    (when-not (str/blank? sql)
      (str "where " sql))))

(defmethod to-sql :conds [params {:keys [sensitive] :as opts} [_ & cs]]
  (let [opts (dissoc opts :sensitive)
        sqls (map (partial to-sql params opts) cs)
        sqls (partition 2 (cons [nil "and"] sqls))
        need-const-cond (if sensitive
                          (not-empty (filter (fn [[_ [k v]]] (and (not (nil? k)) v)) sqls))
                          true)
        sql (reduce
             (fn [r [[_ o] [k c]]]
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
  (let [[k sql] (to-sql params opts re)
        [lk lsql] (to-sql params opts le)]
    [(or lk k) (when-not (or (str/blank? lsql) (str/blank? sql)) (str lsql " " co " " sql))]))

(defmethod to-sql :sensitive-conds [params opts [_ ob cs cb]]
  (when-let [cs (to-sql params (assoc opts :sensitive true) cs)]
    cs))

(defmethod to-sql :value-list [params opts [_ op kid cp]]
  (let [[k sql] (to-sql params opts kid)]
    [k (when sql (str op sql cp))]))

(defmethod to-sql :func [params opts ast]
  (case (count ast)
    4 [nil (str/join (rest ast))]
    5 (let [[_ fname op cols cp] ast
            [k sql] (to-sql params (assoc opts :sensitive false) cols)]
        [k (when sql (str fname op sql cp))])
    7 (let [[_ fname op _ cols _ cp] ast
            [k sql] (to-sql params opts cols)]
        [k (when sql (str fname op sql cp))])))

(defmethod to-sql :cols [params {:keys [sensitive] :as opts} [_ & args]]
  (let [sqls (map (partial to-sql params opts) args)]
    (if sensitive
      (let [has-key (not-empty (filter (fn [[k v]] k) sqls))
            clear (not-empty (filter (fn [[k v]] (nil? v)) sqls))]
        (if clear
          [true nil]
          [has-key (reduce (fn [r [k v]] (if (str/blank? v) r (if r (str r "," v) v))) nil sqls)]))
      [nil (reduce (fn [r [k v]] (if (str/blank? v) r (if r (str r "," v) v))) nil sqls)]))
  )

(defmethod to-sql :KID [params {sen :sensitive :or {sen true}} [_ kid]]
  (if sen
    (let [k (keyword (str/replace-first kid #"(:[-\w]+\*?)?:(\S+)" "$2"))]
      [k (when-not (nil? (get params k)) kid)])
    [nil kid]))
