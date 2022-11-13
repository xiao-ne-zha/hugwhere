(ns org.to.victory.db.hug-impl
  (:require  [clojure.string :as str]
             [instaparse.core :refer [defparser] :as insta]
             [clojure.core.cache :as cache]
             [clojure.java.io :as io]
             [hugsql.parameters :as hp]))

(def REGEX-KID #"(:[-\w]+\*?)?:[-\w]+(\.[-\w]+)*")
(defparser ^:private hwp (slurp (io/resource "hugwhere.bnf")) :auto-whitespace :standard)
(def where-parser (memoize hwp))
(defn- to-param-id [kid]
  (keyword (str/replace-first kid #"(:[-\w]+\*?)?:(\S+)" "$2")))
(defn- get-all-key-from-sql [sql]
  (when (and sql (string? sql))
    (->> (re-seq REGEX-KID sql)
         (map first)
         (map to-param-id))))
;; 返回结果 sql
;; 每层中括号仅仅管理本层的exps和acts关系
(defmulti to-hugsql (fn [param-keyset ast] (first ast)) :default nil)

(defn lcache4fn
  "本地缓存函数,仅对参数和值进行kv缓存，副作用将失效"
  {:added "zszhang, 2016-11-02"
   :static true}
  [ttl-seconds f]
  (let [mem (atom (cache/ttl-cache-factory {} :ttl (* 1000 ttl-seconds)))]
    (fn [& args]
      (if (cache/has? @mem args)
        (get (cache/hit @mem args) args)
        (let [ret (apply f args)]
          (swap! mem assoc args ret)
          ret)))))

;; 定义该函数，是为了不缓存多态方法
(defn get-sql [pks ast]
  (to-hugsql pks ast))
(def get-sql-through-cache (lcache4fn 300 get-sql))


(defn to-sql [params where-clause]
  (let [ast (where-parser where-clause)
        ;;hav-v-params (remove (fn [[k v]] (nil? v)) params)
        ;;pks (->> hav-v-params (into {}) keys set)
        pks (get-all-key-from-sql where-clause)
        pks (remove #(nil? (get-in params (hp/deep-get-vec %))) pks)
        sql (get-sql-through-cache (set pks) ast)]
    (when-not (empty? sql)
      sql)))

(defmethod to-hugsql nil [pks ast]
  (when (string? ast) ast))

(defmethod to-hugsql :KID [pks [_ kid]]
  kid)

(defn- kid->sql-sensitive [pks [_ kid]]
  (let [exp-k (to-param-id kid)
        act-k (get pks exp-k)]
    (when act-k kid)))

#_(defmethod to-hugsql :KID [pks [_ kid]]
    (let [exp-k (to-param-id kid)
          act-k (get pks exp-k)]
      kid))

(defmethod to-hugsql :where-clause [pks [_ where conds]]
  (if (nil? conds)
    (to-hugsql pks where)
    (when-let [sql (to-hugsql pks conds)]
      (when-not (str/blank? sql)
        (str where \space sql)))))

(defmethod to-hugsql :conds [pks [_ & conds]]
  (->> conds
       (map #(to-hugsql pks %))
       #_(map #(->> % (to-hugsql pks) (remove nil?) (str/join \space)))
       (remove empty?)
       (str/join \space)))

(defmethod to-hugsql :cond [pks [_ & elements]]
  (let [idx-types (map-indexed (fn [idx e] [idx (first e)]) elements)
        idx-direct-deps (remove nil?
                                (map (fn [[idx tp]] (when (= :KID tp) idx)) idx-types))
        idx-indirect-deps (remove nil?
                                  (map (fn [[idx tp]] (when (= :cond tp) idx)) idx-types))
        ;;idx-deps (remove nil?
        ;;                         (map (fn [[idx tp]] (when (#{:cond :KID} tp) idx)) idx-types))
        sql-elements (mapv (fn [e]
                             (if (string? e) e
                                 (if (= :KID (first e))
                                   (kid->sql-sensitive pks e)
                                   (to-hugsql pks e))))
                           elements)
        direct-deps-result (when-not (empty? idx-direct-deps) (map #(get sql-elements %) idx-direct-deps))
        indirect-deps-reuslt (when-not (empty? idx-indirect-deps) (map #(get sql-elements %) idx-indirect-deps))
        sql-elements (remove nil? sql-elements)
        ;;deps-result (map #(get sql-elements %) idx-deps)
        ]
    (if direct-deps-result
      (when-not (every? nil? direct-deps-result)
        (str/join \space sql-elements))
      (when-not (every? nil? indirect-deps-reuslt)
        (str/join \space sql-elements)))))
