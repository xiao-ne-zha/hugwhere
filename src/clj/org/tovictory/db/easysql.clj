(ns org.tovictory.db.easysql
  (:require  [clojure.string :as str]))

(defn xf-header [prefix fname]
  (let [sqlid (if (empty? prefix) fname (str prefix "-" fname))]
    (cond
      (str/starts-with? fname "insert")
      (str "-- :name " sqlid " :! :n :D")
      (str/starts-with? fname "save")
      (str "-- :name " sqlid " :! :n :D")
      (str/starts-with? fname "update")
      (str "-- :name " sqlid " :! :n :D")
      (str/starts-with? fname "delete")
      (str "-- :name " sqlid " :! :n :D")
      (str/starts-with? fname "detail")
      (str "-- :name " sqlid " :? :1 :D")
      (str/starts-with? fname "count")
      (str "-- :name " sqlid " :? :1 :D")
      (str/starts-with? fname "total")
      (str "-- :name " sqlid " :? :1 :D")
      :else
      (str "-- :name " sqlid " :? :* :D"))))

(defn xf-line
  ([line] (xf-line nil line))
  ([prefix oline]
   (let [line (str/triml oline)]
     (cond
       (str/starts-with? line "{")
       (str "--~ " line)
       (re-matches #"^--\s+:name\s+\S+\s*$" line)
       (let [sqlid (re-find #"(?<=\s:name\s+)\S+" line)]
         (xf-header prefix sqlid))
       (re-matches #"^order\s+by\s+:\w+(\.\w+)?\s*$" line)
       (let [order-by-key (second (re-matches #"^order\s+by\s+(:\w+(\.\w+)?)\s*$" line))]
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


(comment
  (easysql->hugsql "-- :name list-by-map \n-- comment content\nselect * from table-a\n {where 1 = 1 and {a = :a}}")
  (easysql->hugsql "-- :name list-by-map \n-- comment content\nselect * from table-a \n{where 1 = 1\n and {a = :a}}")
  (easysql->hugsql "-- :name list-by-map \n-- comment content\nselect * from table-a \n{where 1 = 1\n--comment\n and {a = :a}}")
  ;; 复杂的where条件，可以写成clojure语言来组织字符串
  ;; 考虑到复杂性需要对齐等因素，建议采用/*~ clojure form ~*/ 形式
  (easysql->hugsql "-- :name test-expr\n--comment\n--~(let [s1 \"select * from table-a\"] (str s1 (when (:name params) \" where ddd\")))")
  (easysql->hugsql "-- :name test-expr\n--comment\n/*~(when (:name params) \"and name = :name\")~*/")
  (easysql->hugsql "-- :name test-expr\n--comment\n/*~\n--~(when (:name params) \"and name = :name\")\n~*/"))
