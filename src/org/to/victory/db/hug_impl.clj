(ns org.to.victory.db.hug-impl
  (:require  [clojure.string :as str]
             [instaparse.core :refer [defparser]]
             [clojure.java.io :as io]))

(defparser ^:private hwp (slurp (io/resource "hugwhere.bnf")) :auto-whitespace :standard)
(def where-parser #_(memoize hwp) hwp)

;; 返回结果 [exps acts sql]
;; 在参数敏感时，exps必须返回期望的参数，acts返回实际有值的参数，sql转换的结果
;; 在参数不敏感时，exps 和 acts 为空，sql取实际值
(defmulti to-sql (fn [params sensitive ast] (first ast)) :default nil)

(defn- keep? [exps acts]
  (if-not (empty? exps)
    (some identity acts)
    true))

(defn- concat-with-space [msql & others]
  (apply str msql (when (and msql (not= (last msql) \space)) \space) others))

(defn- concat-sql [params sensitive ocs]
  (let [[es as sql]
        (reduce (fn [[mes mas msql] c]
                  (let [[e a s] (to-sql params sensitive c)
                        s (if (and (keep? e a) (not-empty s))
                            (concat-with-space msql s)
                            msql)]
                    (if sensitive
                      [(concat mes e) (concat mas a) s]
                      [mes mas s]))) [] ocs)]
    (if sensitive [es as (when (keep? es as) sql)] [nil nil sql])))

(defmethod to-sql nil [params sensitive ast]
  (if (string? ast) [nil nil ast]
      (let [[_ & ocs] ast]
        (concat-sql params sensitive ocs))))

(defmethod to-sql :sc [params sensitive [_ l c r]]
  (if r
    (let [sensitive (if (= "]" r) (not sensitive) sensitive)
          [exp act sql] (to-sql params sensitive c)
          sql (when-not (empty? sql) (if (= "]" r) sql (str l sql r)))]
      (if sensitive
        [exp act (when-not (empty? sql) (if (= "]" r) sql (str l sql r)))]
        [nil nil sql]))
    (to-sql params sensitive l)))

(defmethod to-sql :cc [params sensitive [_ & ctt]]
  (let [[exp act sql]
        (reduce (fn [[es as msql] e]
                  (let [[exp act sql] (to-sql params sensitive e)]
                    [(concat es exp) (concat as act)
                     (if sql (concat-with-space msql sql) msql)])) [] ctt)]
    (if (= :func (first (nth ctt 2)))
      [exp act (if (and (not-empty exp) (not-every? true? act)) nil sql)]
      [exp act (when (keep? exp act) sql)])))

(defmethod to-sql :KID [params sensitive [_ kid]]
  (let [k (keyword (str/replace-first kid #"(:[-\w]+\*?)?:(\S+)" "$2"))
        hk (not (nil? (get params k)))]
    [[k] [hk] (when hk kid)]))

(defn- to-lo-sql [lo params sensitive ocs]
  (let [[es as sql] (->> (cons lo ocs)
                         (partition 2)
                         (reduce (fn [[mes mas msql] [o c]]
                                   (let [[e a s] (to-sql params sensitive c)
                                         s (if (and (keep? e a) (not-empty s))
                                             (concat-with-space msql o \space s)
                                             msql)]
                                     (if (or (keep? e a) sensitive)
                                       [(concat mes e) (concat mas a) s]
                                       [mes mas s]))) []))
        sql (when sql (str/trim (str/replace sql #"^(and|or)\s" "")))]
    [es as sql]))

(defmethod to-sql :conds [params sensitive [_ & ocs]]
  (to-lo-sql "or" params sensitive ocs))

(defmethod to-sql :ac [params sensitive [_ & ocs]]
  (to-lo-sql "and" params sensitive ocs))

(defmethod to-sql :where-clause [params sensitive [_ w cs & clauses]]
  (let [[exps acts sql] (to-sql params sensitive cs)
        sql (when-not (empty? sql) (str w " " sql))
        sqls (map #(->> (to-sql params sensitive %) rest second) clauses)
        sql (str/join (cons sql sqls))
        sql (if (empty? sql) nil sql)]
    (if sensitive [exps acts (when (keep? exps acts) sql)] [nil nil sql])))

(defmethod to-sql :func [params sensitive ast]
  (if (= 4 (count ast))
    [nil nil (str/join (rest ast))]
    (let [[_ fname lp cols rp] ast
          [es as sql] (to-sql params true cols)]
      [es as (when sql (str fname lp sql rp))])))

(defmethod to-sql :cols [params sensitive [_ & args]]
  (let [sqls (map (partial to-sql params sensitive) args)]
    (if sensitive
      (reduce (fn [[es as sql] [e a s]]
                [(concat es e) (concat as a)
                 (if (str/blank? s) sql (if sql (str sql "," s) s))])
              []
              sqls)
      [nil nil
       (reduce (fn [r [_ _ v]] (if (str/blank? v) r (if r (str r "," v) v))) nil sqls)])))
