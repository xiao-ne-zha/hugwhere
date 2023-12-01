(ns org.tovictory.db.hugwhere-test
  (:require [clojure.test :refer :all]
            [org.tovictory.db.parablock-test :refer [fmt-str]]
            [org.tovictory.db.hugwhere :refer :all]))

(deftest test-default-dynamic
  (testing "keep constant condition"
    (are [params exp] (= (fmt-str (where params "where a = 100 and b = :b or c like :c")) exp)
      nil "where a = 100 and b = :b or c like :c"
      {:b nil} "where a = 100 and b = :b or c like :c"
      {:b 1} "where a = 100 and b = :b or c like :c"
      {:c "name"} "where a = 100 and b = :b or c like :c"
      {:b 1 :c "x"} "where a = 100 and b = :b or c like :c"))
  (testing "normal in readme"
    (are [params exp]
        (= (fmt-str (where params "where { id = :id and } { name like :l:name and } is_valid = 1")) exp)
      nil "where is_valid = 1"
      {:id 1} "where id = :id and is_valid = 1"
      {:name "nezha"} "where name like :l:name and is_valid = 1"
      {:id 1 :name "nezha"} "where id = :id and name like :l:name and is_valid = 1")))

(deftest test-sensitive-dynamic
  (testing "remove constant condition if params is nil"
    (are [params exp]
        (= (fmt-str (where params "where { a = 100 { and b = :b } { or c like :c } }")) exp)
      nil "where"
      {:b nil} "where"
      {:b 1} "where a = 100 and b = :b"
      {:c "name"} "where a = 100 or c like :c"
      {:b 1 :c "x"} "where a = 100 and b = :b or c like :c"))
  (testing "sense c but not b"
    (are [params exp]
        (= (fmt-str (where params "where { { a = 100 and b = :b or } c like :c }")) exp)
      nil "where"
      {:b nil} "where"
      {:b 1} "where"
      {:c "name"} "where c like :c"
      {:b 1 :c "x"} "where a = 100 and b = :b or c like :c"))
  (testing "sensitive in readme"
    (are [params exp]
        (= (fmt-str (where params "where id = :id { and name like :l:name and is_valid = 1 }")) exp)
      nil "where id = :id"
      {:id 1} "where id = :id"
      {:name "nezha"} "where id = :id and name like :l:name and is_valid = 1"
      {:id 1 :name "nezha"} "where id = :id and name like :l:name and is_valid = 1")))

(comment
  (def params [nil {:b nil} {:b 1} {:c "name"} {:b 1 :c "x"} {:name "nezha"} {:id 1 :name "nezha"}])
  (defn- test-sql [sql]
    (for [p params] (where p sql))))
