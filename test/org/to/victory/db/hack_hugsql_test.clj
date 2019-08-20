(ns org.to.victory.db.hack-hugsql-test
  (:require [org.to.victory.db.hack-hugsql :as sut]
            [clojure.test :refer :all]
            [hugsql.core :as hs]
            [clojure.string :as str]))

(def dbfn-sql
  [["test1" "where a = 100 and b = :b or c like :c"]
   ["list-users" "where id = :id and name like :l:name and is_valid = 1"]
   ["test2" "where [a = 100 and b = :b or c like :c]"]
   ["list-users2" "where id = :id and [name like :l:name and is_valid = 1]"]])

(defn- sql-fn-str [[fn-name where]]
  (str "-- :name " fn-name " :? :* :D\nselect * from users\n--~ " where))

(sut/hack-hugsql)
(->> (map sql-fn-str dbfn-sql)
     (str/join "\n")
     hs/def-sqlvec-fns-from-string)

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
      {:id 1} ["select * from users\nwhere id = ?" 1]
      {:name "nezha"} ["select * from users\nwhere name like ? and is_valid = 1" "%nezha%"]
      {:id 1 :name "nezha"} ["select * from users\nwhere id = ? and name like ? and is_valid = 1" 1 "%nezha%"])))
