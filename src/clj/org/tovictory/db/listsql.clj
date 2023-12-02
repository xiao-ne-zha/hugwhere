(ns org.tovictory.db.listsql
  (:require  [clojure.string :as str]
             [clojure.core.cache :as cache]
             [clojure.java.io :as io]
             [clojure.tools.logging :as log]))

(defn join-line [& s] (str/join "\n" s))

(defn make-header [fn-name]
  (str "-- :name " fn-name " :? :* :D"))

(defn make-sqls [& sqls]
  (let [ctt (str/join \space sqls)]
    (if (str/starts-with? ctt "/*~")
      ctt
      (str "--~ " ctt))))

(def trfm-map
  {:prog join-line
   :fn join-line
   :header make-header
   :sqls make-sqls})

(defn xf-line [line]
  (let [line (str/triml line)]
    (cond
      (re-matches #"--\s+:list\s+\S+\s*" line)
      (let [sqlid (re-find #"(?<=--\s+:list\s+)\S+")]
        (str "-- :name " sqlid " :? :* :D"))
      (re-matches #"--\s+:count\s+\S+\s*" line)
      (let [sqlid (re-find #"(?<=--\s+:count\s+)\S+")]
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

(comment
  (listsql->hugsql "-- :name list-by-map \n-- comment content\nselect * from table-a\n {where 1 = 1 and {a = :a}}")
  (listsql->hugsql "-- :name list-by-map \n-- comment content\nselect * from table-a \n{where 1 = 1\n and {a = :a}}")
  (listsql->hugsql "-- :name list-by-map \n-- comment content\nselect * from table-a \n{where 1 = 1\n--comment\n and {a = :a}}")
  ;; 复杂的where条件，可以写成clojure语言来组织字符串
  ;; 考虑到复杂性需要对齐等因素，建议采用/*~ clojure form ~*/ 形式
  (listsql->hugsql "-- :name test-expr\n--comment\n--~(let [s1 \"select * from table-a\"] (str s1 (when (:name params) \" where ddd\")))")
  (listsql->hugsql "-- :name test-expr\n--comment\n/*~(when (:name params) \"and name = :name\")~*/")
  (listsql->hugsql "-- :name test-expr\n--comment\n/*~\n--~(when (:name params) \"and name = :name\")\n~*/"))
