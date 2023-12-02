(ns org.tovictory.db.listsql
  (:require  [clojure.string :as str]
             [instaparse.core :refer [defparser] :as insta]
             [clojure.core.cache :as cache]
             [clojure.java.io :as io]
             [clojure.tools.logging :as log]
             [hugsql.parameters :as hp]))

(defparser ^:private parser (slurp (io/resource "listsql.bnf")))

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

(defn listsql->hugsql [ls]
  (let [result (parser ls)]
    (if (insta/failure? result)
      (log/error "parse listsql error, listsql is " ls "\nfailure info:\n" result)
      (insta/transform trfm-map result))))

(comment
  (parser "-- :name list-by-map \n-- comment content\nselect * from table-a {where 1 = 1 and {a = :a}}")
  (parser "-- :name list-by-map \n-- comment content\nselect * from table-a {where 1 = 1\n and {a = :a}}")
  (parser "-- :name list-by-map \n-- comment content\nselect * from table-a {where 1 = 1\n--comment\n and {a = :a}}")
  (parser "-- :name test-expr\n--comment\n(let [s1 \"select * from table-a\"] (str s1 (when (:name params) \" where ddd\")))")
  (listsql->hugsql "-- :name list-by-map \n-- comment content\nselect * from table-a {where 1 = 1 and {a = :a}}")
  (listsql->hugsql "-- :name list-by-map \n-- comment content\nselect * from table-a {where 1 = 1\n and {a = :a}}")
  (listsql->hugsql "-- :name list-by-map \n-- comment content\nselect * from table-a {where 1 = 1\n--comment\n and {a = :a}}")
  ;; 复杂的where条件，可以写成clojure语言来组织字符串
  ;; 考虑到复杂性需要对齐等因素，建议采用/*~ clojure form ~*/ 形式
  (listsql->hugsql "-- :name test-expr\n--comment\n(let [s1 \"select * from table-a\"] (str s1 (when (:name params) \" where ddd\")))")
  (listsql->hugsql "-- :name test-expr\n--comment\n/*~(when (:name params) \"and name = :name\")~*/")
  (listsql->hugsql "-- :name test-expr\n--comment\n/*~\n(when (:name params) \"and name = :name\")\n~*/"))
