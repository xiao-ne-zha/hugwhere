(ns org.tovictory.db.listsql
  (:require  [clojure.string :as str]))

(defn xf-line [line]
  (let [line (str/triml line)]
    (cond
      ;; 默认:name 相当于:list
      (re-matches #"--\s+:(name|list)\s+\S+\s*" line)
      (let [sqlid (re-find #"(?<=\s:name\s+)\S+" line)
            sqlid (if sqlid sqlid
                      (re-find #"(?<=\s:list\s+)\S+" line))]
        (str "-- :name " sqlid " :? :* :D"))
      (re-matches #"--\s+:count\s+\S+\s*" line)
      (let [sqlid (re-find #"(?<=--\s+:count\s+)\S+" line)]
        (str "-- :name " sqlid " :? :1 :D"))
      (str/starts-with? line "{")
      (str "--~ " line)
      :else
      line)))

(defn listsql->hugsql [ls]
  (->> ls
       str/split-lines
       (map xf-line)
       (str/join \newline)))

(defn xf-line-cud [table-name line]
  (let [line (str/trim line)]
    (cond
      (re-matches #"--\s+:(insert|update|delete)\b" line)
      (let [action (re-find #"(?<=--\s+:)insert|update|delete(?=\s*)" line)]
        (str "-- :name " action "-" table-name " :! :n :D"))
      (str/starts-with? line "{")
      (str "--~ " line)
      :else
      line)))

(defn cudsql->hugsql [table-name lines]
  (let [xf (partial xf-line-cud table-name)]
    (->> lines
         str/split-lines
         (map xf)
         (str/join \newline))))

(comment
  (listsql->hugsql "-- :name list-by-map \n-- comment content\nselect * from table-a\n {where 1 = 1 and {a = :a}}")
  (listsql->hugsql "-- :name list-by-map \n-- comment content\nselect * from table-a \n{where 1 = 1\n and {a = :a}}")
  (listsql->hugsql "-- :name list-by-map \n-- comment content\nselect * from table-a \n{where 1 = 1\n--comment\n and {a = :a}}")
  ;; 复杂的where条件，可以写成clojure语言来组织字符串
  ;; 考虑到复杂性需要对齐等因素，建议采用/*~ clojure form ~*/ 形式
  (listsql->hugsql "-- :name test-expr\n--comment\n--~(let [s1 \"select * from table-a\"] (str s1 (when (:name params) \" where ddd\")))")
  (listsql->hugsql "-- :name test-expr\n--comment\n/*~(when (:name params) \"and name = :name\")~*/")
  (listsql->hugsql "-- :name test-expr\n--comment\n/*~\n--~(when (:name params) \"and name = :name\")\n~*/"))
