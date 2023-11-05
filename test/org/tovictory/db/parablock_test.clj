(ns org.tovictory.db.parablock-test
  (:require [org.tovictory.db.parablock :as sut]
            [clojure.test :refer :all]
            [clojure.string :as str]))

(defn fmt-str [s]
  (when s
    (let [s (str/replace s #"\s{2,}" " ")]
      (if (= \space (last s))
        (subs s 0 (dec (count s)))
        s))))

(deftest xf-statement-test
  (testing "normal sql"
    (are [params exp]
        (= (fmt-str (sut/xf-statement params "select * from table1 where '123' = id"))
           exp)
      nil "select * from table1 where '123' = id"
      {:a 1} "select * from table1 where '123' = id"
      {} "select * from table1 where '123' = id"))
  (testing "postgresql '::type' sql"
    (are [params exp]
        (= (fmt-str (sut/xf-statement params "select * from table1 where '123'::integer = id"))
           exp)
      nil "select * from table1 where '123' ::integer = id"
      {} "select * from table1 where '123' ::integer = id"
      {:a 1} "select * from table1 where '123' ::integer = id"))
  (testing "include spectural charactor sql"
    (are [params exp]
        (= (fmt-str (sut/xf-statement
                     params "select * from table1 where 'abc''def' = 'abc\\'def' and  '123'::integer = id"))
           exp)
      nil "select * from table1 where 'abc''def' = 'abc\\'def' and '123' ::integer = id"
      {} "select * from table1 where 'abc''def' = 'abc\\'def' and '123' ::integer = id"
      {:a 1} "select * from table1 where 'abc''def' = 'abc\\'def' and '123' ::integer = id"))
  (testing "keep constant condition"
    (are [params exp]
        (= (fmt-str (sut/xf-statement params "where a = 100 and b = :b or c like :c"))
           exp)
      nil "where a = 100 and b = :b or c like :c"
      {:b nil} "where a = 100 and b = :b or c like :c"
      {:b 1} "where a = 100 and b = :b or c like :c"
      {:c "name"} "where a = 100 and b = :b or c like :c"
      {:b 1 :c "x"} "where a = 100 and b = :b or c like :c"))
  (testing "normal block in sql"
    (are [params exp]
        (= (fmt-str (sut/xf-statement params "where { id = :id and } { name like :l:name and } is_valid = 1"))
           exp)
      nil "where is_valid = 1"
      {:id 1} "where id = :id and is_valid = 1"
      {:name "nezha"} "where name like :l:name and is_valid = 1"
      {:id 1 :name "nezha"} "where id = :id and name like :l:name and is_valid = 1"))
  (testing "remove constant condition if params is nil"
    (are [params exp]
        (= (fmt-str (sut/xf-statement params " {where a = 100 { and b = :b } { or c like :c } }"))
           exp)
      nil ""
      {:b nil} ""
      {:b 1} "where a = 100 and b = :b"
      {:c "name"} "where a = 100 or c like :c"
      {:b 1 :c "x"} "where a = 100 and b = :b or c like :c"))
  (testing "sense c but not b"
    (are [params exp]
        (= (fmt-str (sut/xf-statement params " {where { a = 100 and b = :b or } c like :c }"))
           exp)
      nil ""
      {:b nil} ""
      {:b 1} ""
      {:c "name"} "where c like :c"
      {:b 1 :c "x"} "where a = 100 and b = :b or c like :c"))
  (testing "sensitive in readme"
    (are [params exp]
        (= (fmt-str (sut/xf-statement params "where id = :id { and name like :l:name and is_valid = 1 }"))
           exp)
        nil "where id = :id"
        {:id 1} "where id = :id"
        {:name "nezha"} "where id = :id and name like :l:name and is_valid = 1"
        {:id 1 :name "nezha"} "where id = :id and name like :l:name and is_valid = 1"))
  (testing "直接依赖有一个为空就返回空"
    (are [params exp]
        (= exp
           (fmt-str
            (sut/xf-statement
             params
             "{id = :id and name like :r:name and xxx}")))
      nil ""
      {} ""
      {:id nil :name nil} ""
      {:id 1} ""
      {:name "xx"} ""
      {:id 1 :name "xx"} "id = :id and name like :r:name and xxx"))
  (testing "间接依赖均为空整个block返回空"
    (are [params exp]
        (= exp
           (fmt-str
            (sut/xf-statement
             params
             "{where xxx {and id = :id} {and name like :r:name}}")))
      nil ""
      {} ""
      {:id 1} "where xxx and id = :id"
      {:name "xx"} "where xxx and name like :r:name"
      {:id 1 :name "xx"} "where xxx and id = :id and name like :r:name")))
