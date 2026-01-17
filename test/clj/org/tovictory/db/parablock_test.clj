(ns org.tovictory.db.parablock-test
  (:require [org.tovictory.db.parablock :as sut]
            [clojure.test :refer :all]
            [clojure.string :as str]))

(defn fmt-str [s]
  (when s
    (-> s (str/replace #"\s{2,}" " ") str/trim)))

(deftest xf-statement-test
  (testing "normal sql"
    (are [params exp]
        (= (fmt-str (sut/xf-statement params "select * from table1 where '123' = id"))
           exp)
      nil "select * from table1 where '123' = id"
      {:a 1} "select * from table1 where '123' = id"
      {} "select * from table1 where '123' = id")
    (are [params exp]
        (= (fmt-str (sut/xf-statement params {:pred-keep-fn sut/contain-para-name?} "select * from table1 where '123' = id"))
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
      {:a 1} "select * from table1 where '123' ::integer = id")
    (are [params exp]
        (= (fmt-str (sut/xf-statement params {:pred-keep-fn sut/contain-para-name?} "select * from table1 where '123'::integer = id"))
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
      {:a 1} "select * from table1 where 'abc''def' = 'abc\\'def' and '123' ::integer = id")
    (are [params exp]
        (= (fmt-str (sut/xf-statement
                     params
                     {:pred-keep-fn sut/contain-para-name?}
                     "select * from table1 where 'abc''def' = 'abc\\'def' and  '123'::integer = id"))
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
      {:b 1 :c "x"} "where a = 100 and b = :b or c like :c")
    (are [params exp]
        (= (fmt-str (sut/xf-statement params {:pred-keep-fn sut/contain-para-name?} "where a = 100 and b = :b or c like :c"))
           exp)
      nil "where a = 100 and b = :b or c like :c"
      {:b nil} "where a = 100 and b = :b or c like :c"
      {:b 1} "where a = 100 and b = :b or c like :c"
      {:c "name"} "where a = 100 and b = :b or c like :c"
      {:b 1 :c "x"} "where a = 100 and b = :b or c like :c"))
  (testing "normal block in sql"
    (are [params exp]
        (= (fmt-str (sut/xf-statement params "where {{ id = :id and }} {{ name like :l:name and }} is_valid = 1"))
           exp)
      nil "where is_valid = 1"
      {:id 1} "where id = :id and is_valid = 1"
      {:name "nezha"} "where name like :l:name and is_valid = 1"
      {:id 1 :name "nezha"} "where id = :id and name like :l:name and is_valid = 1")
    (are [params exp]
        (= (fmt-str (sut/xf-statement
                     params
                     {:pred-keep-fn sut/contain-para-name?}
                     "where {{ id = :id and }} {{ name like :l:name and }} is_valid = 1"))
           exp)
      nil "where is_valid = 1"
      {:id 1} "where id = :id and is_valid = 1"
      {:name "nezha"} "where name like :l:name and is_valid = 1"
      {:id 1 :name "nezha"} "where id = :id and name like :l:name and is_valid = 1"))
  (testing "remove constant condition if params is nil"
    (are [params exp]
        (= (fmt-str (sut/xf-statement params " {{where a = 100 {{ and b = :b }} {{ or c like :c }} }}"))
           exp)
      nil ""
      {:b nil} ""
      {:b 1} "where a = 100 and b = :b"
      {:c "name"} "where a = 100 or c like :c"
      {:b 1 :c "x"} "where a = 100 and b = :b or c like :c"))
  (testing "remove constant condition if params is not exists but not nil"
    (are [params exp]
        (= (fmt-str (sut/xf-statement
                     params
                     {:pred-keep-fn sut/contain-para-name?}
                     " {{where a = 100 {{ and b = :b }} {{ or c like :c }} }}"))
           exp)
      nil ""
      {:b nil} "where a = 100 and b = :b"
      {:b 1} "where a = 100 and b = :b"
      {:c "name"} "where a = 100 or c like :c"
      {:b 1 :c "x"} "where a = 100 and b = :b or c like :c"))
  (testing "sense c but not b"
    (are [params exp]
        (= (fmt-str (sut/xf-statement params " {{where {{ a = 100 and b = :b or }} c like :c }}"))
           exp)
      nil ""
      {:b nil} ""
      {:b 1} ""
      {:c "name"} "where c like :c"
      {:b 1 :c "x"} "where a = 100 and b = :b or c like :c"))
  (testing "sense c but not b (exiists not nil)"
    (are [params exp]
        (= (fmt-str (sut/xf-statement
                     params
                     {:pred-keep-fn sut/contain-para-name?}
                     " {{where {{ a = 100 and b = :b or }} c like :c }}"))
           exp)
      nil ""
      {:b nil} ""
      {:b 1} ""
      {:c nil} "where c like :c"
      {:c "name"} "where c like :c"
      {:b nil :c "x"} "where a = 100 and b = :b or c like :c"
      {:b 1 :c "x"} "where a = 100 and b = :b or c like :c"))
  (testing "sensitive in readme"
    (are [params exp]
        (= (fmt-str (sut/xf-statement params "where id = :id {{ and name like :l:name and is_valid = 1 }}"))
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
             "{{id = :id and name like :r:name and xxx}}")))
      nil ""
      {} ""
      {:id nil :name nil} ""
      {:id 1} ""
      {:name "xx"} ""
      {:id 1 :name "xx"} "id = :id and name like :r:name and xxx"))
  (testing "没有直接依赖时，所有间接依赖均为空整个block返回空"
    (are [params exp]
        (= exp
           (fmt-str
            (sut/xf-statement
             params
             "{{where xxx {{and id = :id}} {{and name like :r:name}}}}")))
      nil ""
      {} ""
      {:id 1} "where xxx and id = :id"
      {:name "xx"} "where xxx and name like :r:name"
      {:id 1 :name "xx"} "where xxx and id = :id and name like :r:name")))

(deftest extract-sql-metadata-test
  (testing "提取包含select-list的SQL元数据"
    (let [sql "SELECT
   [[ID, CODE, NAME, TML_ID, MODEL_ID, LIFE_STATE_ID, CREATE_DATE, count(1) AS cnt]]
FROM OBD
WHERE model_id = :model_id AND life_state_id = :life_state_id
{{ AND code like :l:code }}
{{ AND name like :l:name }} {{ AND create_date >= :create_date_start }} {{ AND create_date <= :create_date_end }}
order by :_order_by
{{ LIMIT :_limit OFFSET :_offset }}"
          metadata (sut/extract-sql-metadata sql)]
      ;; 测试普通参数（包括块内的可选参数）
      (is (= [{:para_name "model_id" :para_type "v" :required true}
              {:para_name "life_state_id" :para_type "v" :required true}
              {:para_name "code" :para_type "l" :required false}
              {:para_name "name" :para_type "l" :required false}
              {:para_name "create_date_start" :para_type "v" :required false}
              {:para_name "create_date_end" :para_type "v" :required false}]
             (:params metadata)))
      ;; 测试系统参数（只有以_开头的参数）
      (is (= 4 (count (:system_params metadata))))
      (is (some #(= {:para_name "_cols" :para_type "sql" :required false} %)
                (:system_params metadata)))
      (is (some #(= {:para_name "_order_by" :para_type "sql" :required false} %)
                (:system_params metadata)))
      (is (some #(= {:para_name "_limit" :para_type "v" :required false} %)
                (:system_params metadata)))
      (is (some #(= {:para_name "_offset" :para_type "v" :required false} %)
                (:system_params metadata)))
      ;; 测试结果列（注意：函数调用中的空格会被规范化）
      (is (= [{:code "ID" :alias "ID"}
              {:code "CODE" :alias "CODE"}
              {:code "NAME" :alias "NAME"}
              {:code "TML_ID" :alias "TML_ID"}
              {:code "MODEL_ID" :alias "MODEL_ID"}
              {:code "LIFE_STATE_ID" :alias "LIFE_STATE_ID"}
              {:code "CREATE_DATE" :alias "CREATE_DATE"}
              {:code "count ( 1 )" :alias "cnt"}]
             (:result_columns metadata)))))

  (testing "提取带别名的select-list元数据"
    (let [sql "SELECT
   [[ID id_alias, 1+2 AS text_match, 1 + 2 test_math2, CODE, NAME, TML_ID, MODEL_ID, LIFE_STATE_ID, CREATE_DATE, count(1) AS cnt]]
FROM OBD
WHERE model_id = :model_id AND life_state_id = :life_state_id"
          metadata (sut/extract-sql-metadata sql)]
      ;; 测试普通参数
      (is (= [{:para_name "model_id" :para_type "v" :required true}
              {:para_name "life_state_id" :para_type "v" :required true}]
             (:params metadata)))
      ;; 测试系统参数
      (is (= [{:para_name "_cols" :para_type "sql" :required false}]
             (:system_params metadata)))
      ;; 测试结果列（带别名，注意：算术表达式和函数调用会被规范化）
      (is (= [{:code "ID" :alias "id_alias"}
              {:code "1 + 2" :alias "text_match"}
              {:code "1 + 2" :alias "test_math2"}
              {:code "CODE" :alias "CODE"}
              {:code "NAME" :alias "NAME"}
              {:code "TML_ID" :alias "TML_ID"}
              {:code "MODEL_ID" :alias "MODEL_ID"}
              {:code "LIFE_STATE_ID" :alias "LIFE_STATE_ID"}
              {:code "CREATE_DATE" :alias "CREATE_DATE"}
              {:code "count ( 1 )" :alias "cnt"}]
             (:result_columns metadata)))))

  (testing "提取限定列名select-list元数据"
    (let [sql "SELECT
  [[a.ID, a.CODE, a.NAME, a.REGION_ID, a.TML_ID, a.FACILITY_ID, a.DEVICE_ID, a.SEQ, a.ROW_NO, a.COL_NO, a.CREATOR_ID, a.CREATE_DATE, a.MODIFIER_ID, a.MODIFY_DATE, a.VERSION, a.NOTES]]
FROM SLOT a,WARE_HOLD_WARE b
WHERE 1 = 1
AND a.id = b.child_id
AND b.parent_spec_id = 1030200001
{{ AND a.ID in (:v*:_ids) }}
{{ AND a.code like :l:code }}
{{ AND a.name like :l:name }}
AND b.parent_id = :parent_id
order by :_order_by
{{ LIMIT :_limit OFFSET :_offset }}"
          metadata (sut/extract-sql-metadata sql)]
      ;; 测试普通参数（包括块内的可选参数）
      (is (= [{:para_name "code" :para_type "l" :required false}
              {:para_name "name" :para_type "l" :required false}
              {:para_name "parent_id" :para_type "v" :required true}]
             (:params metadata)))
      ;; 测试系统参数（_cols, _order_by, _limit, _offset, _ids）
      (is (= 5 (count (:system_params metadata))))
      (is (some #(= {:para_name "_ids" :para_type "v*" :required false} %)
                (:system_params metadata)))
      (is (some #(and (= "_order_by" (:para_name %))
                      (= "sql" (:para_type %))
                      (= false (:required %)))
                (:system_params metadata)))
      ;; 测试结果列（限定列名，别名是点号后的部分）
      (is (= [{:code "a.ID" :alias "ID"}
              {:code "a.CODE" :alias "CODE"}
              {:code "a.NAME" :alias "NAME"}
              {:code "a.REGION_ID" :alias "REGION_ID"}
              {:code "a.TML_ID" :alias "TML_ID"}
              {:code "a.FACILITY_ID" :alias "FACILITY_ID"}
              {:code "a.DEVICE_ID" :alias "DEVICE_ID"}
              {:code "a.SEQ" :alias "SEQ"}
              {:code "a.ROW_NO" :alias "ROW_NO"}
              {:code "a.COL_NO" :alias "COL_NO"}
              {:code "a.CREATOR_ID" :alias "CREATOR_ID"}
              {:code "a.CREATE_DATE" :alias "CREATE_DATE"}
              {:code "a.MODIFIER_ID" :alias "MODIFIER_ID"}
              {:code "a.MODIFY_DATE" :alias "MODIFY_DATE"}
              {:code "a.VERSION" :alias "VERSION"}
              {:code "a.NOTES" :alias "NOTES"}]
             (:result_columns metadata)))))

  (testing "没有select-list的SQL元数据"
    (let [sql "SELECT * FROM users WHERE id = :id AND status = :status"
          metadata (sut/extract-sql-metadata sql)]
      ;; 测试普通参数
      (is (= [{:para_name "id" :para_type "v" :required true}
              {:para_name "status" :para_type "v" :required true}]
             (:params metadata)))
      ;; 测试系统参数为空
      (is (empty? (:system_params metadata)))
      ;; 测试结果列为空
      (is (empty? (:result_columns metadata)))))

  (testing "解析错误的SQL返回空参数列表"
    (let [sql "SELECT [[INVALID SYNTAX"
          metadata (sut/extract-sql-metadata sql)]
      ;; 测试返回空参数
      (is (empty? (:params metadata)))
      (is (empty? (:system_params metadata)))
      ;; 测试结果列为空
      (is (empty? (:result_columns metadata)))))

  (testing "提取select-list中各种select-expr类型"
    (testing "qualified-name: 简单列名"
      (let [sql "SELECT [[col1, col2, col3]] FROM table1"
            metadata (sut/extract-sql-metadata sql)]
        (is (= [{:code "col1" :alias "col1"}
                {:code "col2" :alias "col2"}
                {:code "col3" :alias "col3"}]
               (:result_columns metadata)))))

    (testing "qualified-name: 限定列名 (table.column)"
      (let [sql "SELECT [[t1.col1, t2.col2]] FROM table1 t1, table2 t2"
            metadata (sut/extract-sql-metadata sql)]
        (is (= [{:code "t1.col1" :alias "col1"}
                {:code "t2.col2" :alias "col2"}]
               (:result_columns metadata)))))

    (testing "qualified-name: 模式限定列名 (schema.table.column)"
      (let [sql "SELECT [[public.users.id, public.users.name]] FROM public.users"
            metadata (sut/extract-sql-metadata sql)]
        (is (= [{:code "public.users.id" :alias "id"}
                {:code "public.users.name" :alias "name"}]
               (:result_columns metadata)))))

    (testing "function-call: 简单函数调用"
      (let [sql "SELECT [[COUNT(*), SUM(amount), MAX(created_date)]] FROM orders"
            metadata (sut/extract-sql-metadata sql)]
        (is (= [{:code "COUNT ( * )" :alias "COUNT ( * )"}
                {:code "SUM ( amount )" :alias "SUM ( amount )"}
                {:code "MAX ( created_date )" :alias "MAX ( created_date )"}]
               (:result_columns metadata)))))

    (testing "function-call: 带别名的函数调用"
      (let [sql "SELECT [[COUNT(*) AS total, SUM(amount) AS total_amount]] FROM orders"
            metadata (sut/extract-sql-metadata sql)]
        (is (= [{:code "COUNT ( * )" :alias "total"}
                {:code "SUM ( amount )" :alias "total_amount"}]
               (:result_columns metadata)))))

    (testing "function-call: 嵌套函数调用"
      (let [sql "SELECT [[UPPER(LEFT(name, 3)) AS short_name]] FROM users"
            metadata (sut/extract-sql-metadata sql)]
        ;; 注意：嵌套函数中的逗号前后会有空格
        (is (= [{:code "UPPER ( LEFT ( name , 3 ) )" :alias "short_name"}]
               (:result_columns metadata)))))

    (testing "function-call: 模式限定函数名"
      (let [sql "SELECT [[schema.funcname(col1) AS result]] FROM table1"
            metadata (sut/extract-sql-metadata sql)]
        (is (= [{:code "schema.funcname ( col1 )" :alias "result"}]
               (:result_columns metadata)))))

    (testing "literal: 整数字面量"
      (let [sql "SELECT [[1, 2, 3]] FROM dual"
            metadata (sut/extract-sql-metadata sql)]
        (is (= [{:code "1" :alias "1"}
                {:code "2" :alias "2"}
                {:code "3" :alias "3"}]
               (:result_columns metadata)))))

    (testing "literal: 字符串字面量"
      (let [sql "SELECT [['hello', \"world\", 'it''s']] FROM dual"
            metadata (sut/extract-sql-metadata sql)]
        (is (= [{:code "'hello'" :alias "'hello'"}
                {:code "\"world\"" :alias "\"world\""}
                {:code "'it''s'" :alias "'it''s'"}]
               (:result_columns metadata)))))

    (testing "arithmetic-expr: 算术表达式"
      (let [sql "SELECT [[price * quantity AS total, a + b, x / y]] FROM items"
            metadata (sut/extract-sql-metadata sql)]
        (is (= [{:code "price * quantity" :alias "total"}
                {:code "a + b" :alias "a + b"}
                {:code "x / y" :alias "x / y"}]
               (:result_columns metadata)))))

    (testing "cast-expr: CAST 表达式"
      (let [sql "SELECT [[CAST(id AS VARCHAR), CAST(created_date AS DATE) AS create_date]] FROM users"
            metadata (sut/extract-sql-metadata sql)]
        ;; 注意：CAST 类型参数不会添加空格
        (is (= [{:code "CAST ( id AS VARCHAR )" :alias "CAST ( id AS VARCHAR )"}
                {:code "CAST ( created_date AS DATE )" :alias "create_date"}]
               (:result_columns metadata)))))

    (testing "case-expr: CASE 表达式"
      (let [sql "SELECT [[CASE WHEN status = 1 THEN 'active' WHEN status = 0 THEN 'inactive' ELSE 'unknown' END AS status_text]] FROM users"
            metadata (sut/extract-sql-metadata sql)]
        ;; CASE 表达式带 AS 别名时，使用别名
        (is (= [{:code "CASE WHEN status = 1 THEN 'active' WHEN status = 0 THEN 'inactive' ELSE 'unknown' END"
                :alias "status_text"}]
               (:result_columns metadata)))))

    (testing "case-expr: 带 AS 别名的 CASE 表达式"
      (let [sql "SELECT [[CASE WHEN score >= 90 THEN 'A' WHEN score >= 80 THEN 'B' ELSE 'C' END AS grade]] FROM exams"
            metadata (sut/extract-sql-metadata sql)]
        (is (= [{:code "CASE WHEN score >= 90 THEN 'A' WHEN score >= 80 THEN 'B' ELSE 'C' END"
                :alias "grade"}]
               (:result_columns metadata)))))

    (testing "混合多种 select-expr 类型"
      (let [sql "SELECT [[id, name, COUNT(*) AS total, price * quantity AS total_price, CAST(created_date AS DATE) AS create_date, CASE WHEN active = 1 THEN 'yes' ELSE 'no' END AS is_active]] FROM orders"
            metadata (sut/extract-sql-metadata sql)]
        (is (= [{:code "id" :alias "id"}
                {:code "name" :alias "name"}
                {:code "COUNT ( * )" :alias "total"}
                {:code "price * quantity" :alias "total_price"}
                {:code "CAST ( created_date AS DATE )" :alias "create_date"}
                {:code "CASE WHEN active = 1 THEN 'yes' ELSE 'no' END" :alias "is_active"}]
               (:result_columns metadata)))))))
