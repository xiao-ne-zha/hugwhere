(ns org.tovictory.db.hack-hugsql-test
  (:require [org.tovictory.db.hack-hugsql :as sut]
            [clojure.test :refer :all]
            [hugsql.core :as hs]
            [clojure.string :as str]))

(def dbfn-sql
  [["test1" "where a = 100 [[ and b = :b ]] [[ or c like :c ]]"]
   ["list-users" "where [[ id = :id and ]] [[ name like :l:name and ]] is_valid = 1"]
   ["test2" "where [[ a = 100 [[ and b = :b ]] [[ or c like :c ]] ]]"]
   ["list-users2" "where [[ [[ id = :id and ]] [[ name like :l:name and ]] is_valid = 1 ]]"]
   ["test-influence" "where a = 1 [[ and b like :ll:b and c = 1 ]] [[ and d = :d and e != 0 ]]"]
   ["test-func" "where a = 1 [[ and b = f( :b ,1) ]] [[ and c = fs( :c , :d ) ]]"]
   ["test-func2" "where a = 1 [[ and b = f(f2( :b ),1) ]]"]])

(defn- sql-fn-str [[fn-name where]]
  (str "-- :name " fn-name " :? :* :D\nselect * from users\n--~ " where))

(sut/hack-hugsql)
(->> (map sql-fn-str dbfn-sql)
     (str/join "\n")
     hs/def-sqlvec-fns-from-string)

(defn- iare2 [[_ [f]] p r]
  (str (list f p) " => " r))

(defmacro are2 [_ expr & body]
  (let [prs (partition 2 body)
        r (map #(apply iare2 expr %) prs)]
    (str/join "\n" r)))

(deftest test-use-func
  (testing "simple function"
    (are [params sqls]
        (= (test-func-sqlvec params) sqls)
      nil ["select * from users\nwhere a = 1"]
      {:b "name"} ["select * from users\nwhere a = 1 and b = f( ? ,1)" "name"]
      ;;{:c 100} ["select * from users\nwhere a = 1"] 报错，此处要求c、d必须同时提供，大部分函数也不支持变参
      {:c 100, :d 1} ["select * from users\nwhere a = 1 and c = fs( ? , ? )" 100 1]
      ;;{:b "name" :c 100} ["select * from users\nwhere a = 1 and b = f(?,1)" "name"]
      {:b nil :c 100 :d 1} ["select * from users\nwhere a = 1 and c = fs( ? , ? )" 100 1]))
  (testing "cascade function"
    (are [params sqls]
        (= (test-func2-sqlvec params) sqls)
      nil ["select * from users\nwhere a = 1"]
      {:b nil} ["select * from users\nwhere a = 1"]
      {:b 1} ["select * from users\nwhere a = 1 and b = f(f2( ? ),1)" 1]
      {:b false} ["select * from users\nwhere a = 1 and b = f(f2( ? ),1)" false])))

(deftest test-sensitive-influence
  (are [params sqls] (= (test-influence-sqlvec params) sqls)
    nil ["select * from users\nwhere a = 1"]
    {:b "x"} ["select * from users\nwhere a = 1 and b like ? and c = 1" "x%"]
    {:d 100} ["select * from users\nwhere a = 1 and d = ? and e != 0" 100]
    {:b "x" :d 100} ["select * from users\nwhere a = 1 and b like ? and c = 1 and d = ? and e != 0" "x%" 100]))

(deftest test-default-dynamic
  (testing "keep constant condition"
    (are [params sqls]
        (= (test1-sqlvec params) sqls)
      nil ["select * from users\nwhere a = 100"]
      {:b nil} ["select * from users\nwhere a = 100"]
      {:b 1} ["select * from users\nwhere a = 100 and b = ?" 1]
      {:c "name"} ["select * from users\nwhere a = 100 or c like ?" "name"]
      {:b 1 :c "x"} ["select * from users\nwhere a = 100 and b = ? or c like ?" 1 "x"]))
  (testing "normal in readme"
    (are [params sqls]
        (= (list-users-sqlvec params) sqls)
      nil ["select * from users\nwhere is_valid = 1"]
      {:id 1} ["select * from users\nwhere id = ? and is_valid = 1" 1]
      {:name "nezha"} ["select * from users\nwhere name like ? and is_valid = 1" "%nezha%"]
      {:id 1 :name "nezha"} ["select * from users\nwhere id = ? and name like ? and is_valid = 1" 1 "%nezha%"])))

(deftest test-sensitive-dynamic
  (testing "remove constant condition if params is nil"
    (are [params exp] (= (test2-sqlvec params) exp)
      nil ["select * from users"]
      {:b nil} ["select * from users"]
      {:b 1} ["select * from users\nwhere a = 100 and b = ?" 1]
      {:c "name"} ["select * from users\nwhere a = 100 or c like ?" "name"]
      {:b 1 :c "x"} ["select * from users\nwhere a = 100 and b = ? or c like ?" 1 "x"]))
  (testing "sensitive in readme"
    (are [params exp] (= (list-users2-sqlvec params) exp)
      nil ["select * from users"]
      {:id 1} ["select * from users\nwhere id = ? and is_valid = 1" 1]
      {:name "nezha"} ["select * from users\nwhere name like ? and is_valid = 1" "%nezha%"]
      {:id 1 :name "nezha"} ["select * from users\nwhere id = ? and name like ? and is_valid = 1" 1 "%nezha%"])))
