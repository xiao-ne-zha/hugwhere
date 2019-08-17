(ns org.to.vitory.db.hugwhere-test
  (:require [clojure.test :refer :all]
            [org.to.vitory.db.hugwhere :refer :all]))

(deftest test-default-dynamic
  (testing "keep constant condition"
    (are [params exp] (= (where params "where a = 100 and b = :b or c like :c") exp)
      nil "where a = 100"
      {:b nil} "where a = 100"
      {:b 1} "where a = 100 and b = :b"
      {:c "name"} "where a = 100 or c like :c"
      {:b 1 :c "x"} "where a = 100 and b = :b or c like :c"))
  (testing "normal in readme"
    (are [params exp] (= (where params "where id = :id and name like :l:name and is_valid = 1") exp)
      nil "where is_valid = 1"
      {:id 1} "where id = :id and is_valid = 1"
      {:name "nezha"} "where name like :l:name and is_valid = 1"
      {:id 1 :name "nezha"} "where id = :id and name like :l:name and is_valid = 1")))

(deftest test-sensitive-dynamic
  (testing "remove constant condition if params is nil"
    (are [params exp] (= (where params "where [a = 100 and b = :b or c like :c]") exp)
      nil nil
      {:b nil} nil
      {:b 1} "where a = 100 and b = :b"
      {:c "name"} "where a = 100 or c like :c"
      {:b 1 :c "x"} "where a = 100 and b = :b or c like :c"))
  (testing "sensitive in readme"
    (are [params exp] (= (where params "where id = :id and [name like :l:name and is_valid = 1]") exp)
      nil nil
      {:id 1} "where id = :id"
      {:name "nezha"} "where name like :l:name and is_valid = 1"
      {:id 1 :name "nezha"} "where id = :id and name like :l:name and is_valid = 1")))
