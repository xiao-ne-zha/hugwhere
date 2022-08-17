(ns org.to.victory.db.hug-impl
  (:require  [clojure.string :as str]
             [instaparse.core :refer [defparser] :as insta]
             [clojure.core.cache :as cache]
             [clojure.java.io :as io]))

(defparser ^:private hwp (slurp (io/resource "hugwhere.bnf")) :auto-whitespace :standard)
(def where-parser (memoize hwp))

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

(def get-sql-through-cache (lcache4fn 300 to-hugsql))

(defn to-sql [params where-clause]
  (let [ast (where-parser where-clause)
        hav-v-params (remove (fn [[k v]] (nil? v)) params)
        pks (->> hav-v-params (into {}) keys set)]
    (get-sql-through-cache pks ast)))

(defmethod to-hugsql nil [pks ast]
  (when (string? ast) ast))

(defn- to-param-id [kid]
  (keyword (str/replace-first kid #"(:[-\w]+\*?)?:(\S+)" "$2")))

(defmethod to-hugsql :KID [pks [_ kid]]
  (let [exp-k (to-param-id kid)
        act-k (get pks exp-k)]
    kid))

(defmethod to-hugsql :where-clause [pks [_ where conds]]
  (when-let [sql (to-hugsql pks conds)]
    (when-not (str/blank? sql)
      (str where \space sql))))

(defmethod to-hugsql :conds [pks [_ & conds]]
  (->> conds
       (map #(to-hugsql pks %))
       #_(map #(->> % (to-hugsql pks) (remove nil?) (str/join \space)))
       (remove empty?)
       (str/join \space)))

(defmethod to-hugsql :cond [pks [_ & ast]]
  (if (= "[" (first ast))
    (let [cds (-> ast rest drop-last)
          deps (map #(let [tp (first %)]
                       (cond
                         (= :KID tp) (to-param-id (second %))
                         (= :cond tp) :indirect-dep
                         :else nil))
                    cds)
          ddeps (remove #(or (nil? %) (= :indirect-dep %)) deps)
          sqls (map #(to-hugsql pks %) cds)
          ideps (filter #(= :indirect-dep %) deps)
          real-ideps (filter identity (map #(and (= :indirect-dep %1) %2) deps sqls))]
      (if (empty? ddeps) ;; 没有需要的直接依赖，检查是否有间接依赖，如果没有间接依赖，直接保留所有sql。如果有间接依赖，当间接依赖都没有值时，舍弃，只要有一个间接依赖有值，就应该保留
        (cond
          (empty? ideps) (str/join \space (remove nil? sqls))
          (empty? real-ideps) nil
          :else (str/join \space (remove nil? sqls)))
        (when (some pks ddeps) ;; 需要直接依赖，直接依赖又有值时
          (str/join \space (remove nil? sqls)))))
    (->> ast
         (map #(to-hugsql pks %))
         (remove empty?)
         (str/join \space))))
