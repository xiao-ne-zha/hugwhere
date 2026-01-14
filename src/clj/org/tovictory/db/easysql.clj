(ns org.tovictory.db.easysql
  (:require  [clojure.string :as str]
             [hugsql.core :as hs]
             [org.tovictory.db.hug-params]
             [hugsql.parser :as hp]
             [clojure.edn :as edn]))

(defn xf-header [prefix fname]
  (let [sqlid (if (empty? prefix) fname (str prefix "-" fname))]
    (cond
      (str/starts-with? fname "insert")
      (str "-- :name " sqlid " :! :n")
      (str/starts-with? fname "save")
      (str "-- :name " sqlid " :! :n")
      (str/starts-with? fname "update")
      (str "-- :name " sqlid " :! :n")
      (str/starts-with? fname "delete")
      (str "-- :name " sqlid " :! :n")
      (str/starts-with? fname "detail")
      (str "-- :name " sqlid " :? :1")
      (str/starts-with? fname "count")
      (str "-- :name " sqlid " :? :1")
      (str/starts-with? fname "total")
      (str "-- :name " sqlid " :? :1")
      :else
      (str "-- :name " sqlid " :? :*"))))

(defn xf-line
  ([line] (xf-line nil line))
  ([prefix oline]
   (let [line (str/triml oline)]
     (cond
       (or (str/starts-with? line "{{") (str/starts-with? line "[["))
       ;; 保留非空值的sql部分
       (format "--~ (smart-block params \"%s\")" line)

       (str/starts-with? line "--$")
       (format "--~ (smart-block params \"%s\")" (subs line 3))

       (str/starts-with? line "--@")
       ;; 只有有键信息就保留的sql部分
       (format "--~ (smart-block params {:pred-keep-fn contain-para-name?} \"%s\")" (subs line 3))

       (re-matches #"^--\s+:name\s+\S+\s*$" line)
       (let [sqlid (re-find #"(?<=\s:name\s{1,10})\S+" line)]
         (xf-header prefix sqlid))

       (re-matches #"(?i)^order\s+by\s+:\w+(\.\w+)?\s*$" line)
       (let [order-by-key (second (re-matches #"(?i)^order\s+by\s+(:\w+(\.\w+)?)\s*$" line))]
         (format "--~ (order-by %s)" order-by-key))

       :else
       oline))))

(defn easysql->hugsql
  ([ls] (easysql->hugsql nil ls))
  ([prefix ls]
   (->> ls
        str/split-lines
        (map (partial xf-line prefix))
        (str/join \newline))))

(defn sqlvec-fn [easysql]
  (let [hugsql (easysql->hugsql easysql)
        parsed-defs (hp/parse hugsql
                              {:no-header true
                               :require-str "[org.tovictory.db.hugwhere :refer [smart-block order-by not-nil? contain-para-name?]]"})
        pdef (first parsed-defs)]
    ;; 编译表达式以处理 require-str
    (hs/compile-exprs pdef)
    (hs/sqlvec-fn* (:sql pdef))))

(comment
  (easysql->hugsql "-- :name list-by-map \n-- comment content\nselect * from table-a\n {{where 1 = 1 and {{a = :a}}}}")
  (easysql->hugsql "-- :name list-by-map \n-- comment content\nselect * from table-a \n{{where 1 = 1\n and {{a = :a}}}}")
  (easysql->hugsql "-- :name list-by-map \n-- comment content\nselect * from table-a \n{{where 1 = 1\n--comment\n and {{a = :a}}
}}")
  ;; 复杂的where条件，可以写成clojure语言来组织字符串
  ;; 考虑到复杂性需要对齐等因素，建议采用/*~ clojure form ~*/ 形式
  (easysql->hugsql "-- :name test-expr\n--comment\n--~(let [s1 \"select * from table-a\"] (str s1 (when (:name params) \" where ddd\")))")
  (easysql->hugsql "-- :name test-expr\n--comment\n/*~(when (:name params) \"and name = :name\")~*/")
  (easysql->hugsql "-- :name test-expr\n--comment\n/*~\n--~(when (:name params) \"and name = :name\")\n~*/")
  (easysql->hugsql "SELECT
   [[ID, CODE, NAME, TML_ID, MODEL_ID, LIFE_STATE_ID, CREATE_DATE, count(1) AS cnt]]
FROM OBD
WHERE model_id = :model_id AND life_state_id = :life_state_id
{{ AND code like :l:code }}
{{ AND name like :l:name }} {{ AND create_date >= :create_date_start }} {{ AND create_date <= :create_date_end }}
order by :_order_by
{{ LIMIT :_limit OFFSET :_offset }}"))
