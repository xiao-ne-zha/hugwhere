(ns org.tovictory.db.listsql
  (:require  [clojure.string :as str]
             [instaparse.core :refer [defparser] :as insta]
             [clojure.core.cache :as cache]
             [clojure.java.io :as io]
             [hugsql.parameters :as hp]))

(defparser ^:private parser (slurp (io/resource "listsql.bnf")))

(defn join-line [& s] (str/join "\n" s))

(defn make-header [fn-name]
  (str "-- :name " fn-name " :? :* :D"))

(defn make-sqls [& sqls]
  (str "--~ "
       (str/join \space sqls)))

(def trfm-map
  {:prog join-line
   :fn join-line
   :header make-header
   :sqls make-sqls})

(defn listsql->hugsql [ls]
  (->> ls
       parser
       (insta/transform trfm-map)))

(comment
  (parser "-- :name list-by-map \n-- comment content\nselect * from table-a {where 1 = 1 and {a = :a}}")
  (parser "-- :name list-by-map \n-- comment content\nselect * from table-a {where 1 = 1\n and {a = :a}}")
  (parser "-- :name list-by-map \n-- comment content\nselect * from table-a {where 1 = 1\n--comment\n and {a = :a}}")
  (listsql->hugsql "-- :name list-by-map \n-- comment content\nselect * from table-a {where 1 = 1 and {a = :a}}")
  (listsql->hugsql "-- :name list-by-map \n-- comment content\nselect * from table-a {where 1 = 1\n and {a = :a}}")
  (listsql->hugsql "-- :name list-by-map \n-- comment content\nselect * from table-a {where 1 = 1\n--comment\n and {a = :a}}"))
