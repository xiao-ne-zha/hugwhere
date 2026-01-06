(ns org.tovictory.db.hack-hugsql-test
  (:require [org.tovictory.db.hug-params] ;; 引入此命名空间主要是为了使like参数生效
            [clojure.test :refer :all]
            [org.tovictory.db.parablock-test :refer [fmt-str]]
            [hugsql.core :as hs]
            [org.tovictory.db.easysql :refer [easysql->hugsql]]
            [clojure.string :as str]))

(def dbfn-sql
  [["test1" "where a = 100 {{ and b = :b }} {{ or c like :c }}"]
   ["list-users" "where {{ id = :id and }} {{ name like :l:name and }} is_valid = 1"]
   ["test2" "{{where  a = 100 {{ and b = :b }} {{ or c like :c }} }}"]
   ["list-users2" "{{where  {{ id = :id and }} {{ name like :l:name and }} is_valid = 1 }}"]
   ["test-influence" "where a = 1 {{ and b like :ll:b and c = 1 }} {{ and d = :d and e != 0 }}"]
   ["test-func" "where a = 1 {{ and b = f( :b ,1) }} {{ and c = fs( :c , :d ) }}"]
   ["test-func2" "where a = 1 {{ and b = f(f2( :b ),1) }}"]])

(defn- sql-fn-str [[fn-name where]]
  (easysql->hugsql (str "-- :name " fn-name " :? :*\nselect * from users\n--$" where
                        "\n-- :name " fn-name "-keep-null :? :*\nselect * from users\n--@" where)))

(defn fmt-sqlvec [sqlvec]
  (let [[sql & vs] sqlvec]
    (concat [(fmt-str sql)] vs)))

(hs/def-sqlvec-fns-from-string
  (str/join "\n"
            (map sql-fn-str dbfn-sql))
  {:require-str "[org.tovictory.db.hugwhere :refer [smart-block order-by not-nil? contain-para-name?]]"})

(defn- iare2 [[_ [f]] p r]
  (str (list f p) " => " r))

(defmacro are2 [_ expr & body]
  (let [prs (partition 2 body)
        r (map #(apply iare2 expr %) prs)]
    (str/join "\n" r)))

(deftest test-use-func
  (testing "simple function"
    (are [params sqls]
        (= (fmt-sqlvec (test-func-sqlvec params)) sqls)
      nil ["select * from users\nwhere a = 1"]
      {:b nil} ["select * from users\nwhere a = 1"]
      {:b "name"} ["select * from users\nwhere a = 1 and b = f( ? ,1)" "name"]
      {:c 100} ["select * from users\nwhere a = 1"] ;;报错，此处要求c、d必须同时提供，大部分函数也不支持变参
      {:c 100, :d 1} ["select * from users\nwhere a = 1 and c = fs( ? , ? )" 100 1]
      {:b "name" :c 100} ["select * from users\nwhere a = 1 and b = f( ? ,1)" "name"]
      {:b nil :c 100 :d 1} ["select * from users\nwhere a = 1 and c = fs( ? , ? )" 100 1]))
  (testing "cascade function"
    (are [params sqls]
        (= (fmt-sqlvec (test-func2-sqlvec params)) sqls)
      nil ["select * from users\nwhere a = 1"]
      {:b nil} ["select * from users\nwhere a = 1"]
      {:b 1} ["select * from users\nwhere a = 1 and b = f(f2( ? ),1)" 1]
      {:b false} ["select * from users\nwhere a = 1 and b = f(f2( ? ),1)" false])))

(deftest test-use-func-keep-null
  (testing "simple function"
    (are [params sqls]
        (= (fmt-sqlvec (test-func-keep-null-sqlvec params)) sqls)
      nil ["select * from users\nwhere a = 1"]
      {:b nil} ["select * from users\nwhere a = 1 and b = f( ? ,1)" nil]
      {:b "name"} ["select * from users\nwhere a = 1 and b = f( ? ,1)" "name"]
      {:c 100} ["select * from users\nwhere a = 1"] ;;报错，此处要求c、d必须同时提供，大部分函数也不支持变参
      {:c 100, :d 1} ["select * from users\nwhere a = 1 and c = fs( ? , ? )" 100 1]
      {:b "name" :c 100} ["select * from users\nwhere a = 1 and b = f( ? ,1)" "name"]
      {:b nil :c 100 :d 1} ["select * from users\nwhere a = 1 and b = f( ? ,1) and c = fs( ? , ? )" nil 100 1]))
  (testing "cascade function"
    (are [params sqls]
        (= (fmt-sqlvec (test-func2-sqlvec params)) sqls)
      nil ["select * from users\nwhere a = 1"]
      {:b nil} ["select * from users\nwhere a = 1"]
      {:b 1} ["select * from users\nwhere a = 1 and b = f(f2( ? ),1)" 1]
      {:b false} ["select * from users\nwhere a = 1 and b = f(f2( ? ),1)" false])))

(deftest test-sensitive-influence
  (are [params sqls] (= (fmt-sqlvec (test-influence-sqlvec params)) sqls)
    nil ["select * from users\nwhere a = 1"]
    {:b "x"} ["select * from users\nwhere a = 1 and b like ? and c = 1" "x%"]
    {:d 100} ["select * from users\nwhere a = 1 and d = ? and e != 0" 100]
    {:b "x" :d 100} ["select * from users\nwhere a = 1 and b like ? and c = 1 and d = ? and e != 0" "x%" 100]))

(deftest test-default-dynamic
  (testing "keep constant condition"
    (are [params sqls]
        (= (fmt-sqlvec (test1-sqlvec params)) sqls)
      nil ["select * from users\nwhere a = 100"]
      {:b nil} ["select * from users\nwhere a = 100"]
      {:b 1} ["select * from users\nwhere a = 100 and b = ?" 1]
      {:c "name"} ["select * from users\nwhere a = 100 or c like ?" "name"]
      {:b 1 :c "x"} ["select * from users\nwhere a = 100 and b = ? or c like ?" 1 "x"]))
  (testing "normal in readme"
    (are [params sqls]
        (= (fmt-sqlvec (list-users-sqlvec params)) sqls)
      nil ["select * from users\nwhere is_valid = 1"]
      {:id 1} ["select * from users\nwhere id = ? and is_valid = 1" 1]
      {:name "nezha"} ["select * from users\nwhere name like ? and is_valid = 1" "%nezha%"]
      {:id 1 :name "nezha"} ["select * from users\nwhere id = ? and name like ? and is_valid = 1" 1 "%nezha%"])))

(deftest test-sensitive-dynamic
  (testing "remove constant condition if params is nil"
    (are [params exp] (= (fmt-sqlvec (test2-sqlvec params)) exp)
      nil ["select * from users"]
      {:b nil} ["select * from users"]
      {:b 1} ["select * from users\nwhere a = 100 and b = ?" 1]
      {:c "name"} ["select * from users\nwhere a = 100 or c like ?" "name"]
      {:b 1 :c "x"} ["select * from users\nwhere a = 100 and b = ? or c like ?" 1 "x"]))
  (testing "sensitive in readme"
    (are [params exp] (= (fmt-sqlvec (list-users2-sqlvec params)) exp)
      nil ["select * from users"]
      {:id 1} ["select * from users\nwhere id = ? and is_valid = 1" 1]
      {:name "nezha"} ["select * from users\nwhere name like ? and is_valid = 1" "%nezha%"]
      {:id 1 :name "nezha"} ["select * from users\nwhere id = ? and name like ? and is_valid = 1" 1 "%nezha%"])))
