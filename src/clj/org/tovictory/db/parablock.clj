(ns org.tovictory.db.parablock
  (:require  [clojure.string :as str]
             [instaparse.core :refer [defparser] :as insta]
             [clojure.core.cache :as cache]
             [clojure.tools.logging :as log]
             [clojure.java.io :as io]
             [hugsql.parameters :as hp]))

(defparser ^:private parser (slurp (io/resource "parablock.bnf")) :auto-whitespace :standard)

(defn not-nil? [params para-key-vec]
  (not (nil? (get-in params para-key-vec))))

(defn contain-para-name? [params para-key-vec]
  (loop [fk (first para-key-vec) rkvec (rest para-key-vec) p params]
    (if (nil? fk)
      true
      (if-not (and (coll? p) (contains? p fk))
        false
        (recur (first rkvec) (rest rkvec) (get p fk))))))

(defn xf-parameter
  "对参数进行转换。当在块内时，能获取到值，才保留。"
  ([[_ & parameter]]
   (if (= 1 (count parameter))
     (str ":" (first parameter))
     (str ":" (first parameter) (second parameter))))
  ([[_ & parameter] params {pred :pred-keep-fn :or {pred not-nil?} :as options}]
   (let [para-type (when (= 2 (count parameter)) (first parameter))
         para-name (if (= 1 (count parameter)) (first parameter) (second parameter))
         para-str (if (= 1 (count parameter)) (str ":" para-name) (str ":" para-type ":" para-name))
         para-key-vec (-> para-name keyword hp/deep-get-vec)]
     (when (pred params para-key-vec)
       para-str))))

(defn xf-block
  "根据参数中值的情况，转换得到块的结果。
  1. 当parameter对应在请求参数map中没有值时，block返回nil.如果block内有多个parameter时，只要有一个没有值，整个block就返回nil.主要考虑是如果允许部分parameter的值为nil,会导致sql语法错误
  3. 当有parameter时， 保留本层的other, parameter"
  [[_ & elements] params options]
  (let [snippets (map (fn [[tp :as ast]]
                        (case tp
                          :parameter [:p (xf-parameter ast params options)]
                          :block [:b (xf-block ast params options)]
                          ast))
                      elements)
        para-snps (->> snippets (filter #(= :p (first %))) (map second))
        block-snps (->> snippets (filter #(= :b (first %))) (map second))]
    (cond
      ;; 有一个直接依赖（参数）转换结果为空，整个block就返回nil
      (some nil? para-snps)
      nil
      ;; 没有parameter,且所有下层block均为nil时，返回nil
      (and (empty? para-snps) (not-empty block-snps) (every? nil? block-snps))
      nil
      :else
      (->> snippets
           (map second)
           (str/join \space))
      )))

(defn- xf-ast
  ([params ast] (xf-ast params nil ast))
  ([params options [tp :as ast]]
   (case tp
     ;; 此时不在块内，所以不能传参数
     :parameter (xf-parameter ast)
     :block (xf-block ast params options)
     (second ast))))

(defn- extract-parameters
  "从AST中递归提取所有参数信息
   in-block?: 表示当前参数是否在块内（可选参数）"
  [ast-node in-block?]
  (when (vector? ast-node)
    (let [[tp & rest-args] ast-node]
      (case tp
        :parameter
        (let [para-list rest-args
              para-type (when (= 2 (count para-list)) (first para-list))
              para-name (if (= 1 (count para-list))
                          (first para-list)
                          (second para-list))
              para-type (or para-type "v")]
          [{:para_name para-name
            :para_type para-type
            :required (not in-block?)}])

        :block
        ;; 进入块内，所有参数都是可选的
        (mapcat #(extract-parameters % true) rest-args)

        ;; 对于 statement 或其他节点，遍历子节点
        (mapcat #(extract-parameters % in-block?) rest-args)))))

(defn extract-named-parameters
  "根据parablock.bnf语法，提取sql中的命名参数信息。
   返回格式：
   {:params [{:para_name \"para_name\" :para_type \"para_type\" :required true/false}]
    :system_params [{:para_name \"para_name\" :para_type \"para_type\" :required true/false}]}

   注意：
   1. 当para_type没有值时，取默认值 \"v\"
   2. 当para_name以`_`开头时，放到system_params中
   3. 在{{}}块内的参数为可选参数（required=false），不在其中的为必选参数（required=true）"
  [sql]
  (let [result (parser sql)]
    (if (insta/failure? result)
      (do
        (log/error "parse sql error, sql template is " sql "\nfailure info:\n" result)
        {:params [] :system_params []})
      (let [all-params (mapcat #(extract-parameters % false) result)
            params (filter #(not (.startsWith (:para_name %) "_")) all-params)
            system-params (filter #(.startsWith (:para_name %) "_") all-params)]
        {:params params
         :system_params system-params}))))

(defn extract-result-columns
  "从SQL字符串中提取结果列信息。
   查找以'--~'开头（前面可能有空白符）且包含(if (seq (:_cols params))...的注释行，
   从该行的第二个字符串中提取列信息（即默认列列表）

   输入：完整的SQL字符串
   输出：[{:col_name \"id\"} {:col_name \"customer_no\"} ...]"
  [sql]
  (when (string? sql)
    (try
      ;; 按行分割SQL
      (let [lines (str/split-lines sql)
            ;; 查找符合条件的行（--~前面可能有空白符）
            target-line (first (filter #(re-find #"^\s*--~\s*\(\s*if\s+\(\s*seq.+?:_cols" %)
                                       lines))]
        (when target-line
          ;; 使用正则表达式直接提取第二个双引号字符串的内容
          (when-let [match (re-find #"\"([^\"]*?)\"\s+\"([^\"]*)\"" target-line)]
            ;; match 是 [整个匹配 第一个字符串内容 第二个字符串内容]
            (when (>= (count match) 3)
              ;; 获取第二个字符串（默认列列表）
              (let [cols-str (nth match 2)]
                ;; 分割列名并去除空格
                (->> (str/split cols-str #",")
                     (map str/trim)
                     (filter (complement str/blank?))
                     (map #(hash-map :col_name %))
                     vec))))))
      (catch Exception e
        (log/error "parse result columns error, sql:" sql "error:" e)
        nil))))

(defn extract-sql-metadata
  "提取SQL的元数据信息，包括参数信息和结果列信息。

   输入：完整的SQL字符串
   输出：
   {:params [{:para_name \"para_name\" :para_type \"para_type\" :required true/false}]
    :system_params [{:para_name \"para_name\" :para_type \"para_type\" :required true/false}]
    :result_columns [{:col_name \"col_name\"}]}

   注意：
   1. 当para_type没有值时，取默认值 \"v\"
   2. 当para_name以`_`开头时，放到system_params中
   3. 在{{}}块内的参数为可选参数（required=false），不在其中的为必选参数（required=true）
   4. result_columns从注释行 --~ (if (seq (:_cols params))... 的第二个字符串中提取"
  [sql]
  (let [named-params (extract-named-parameters sql)
        result-cols (extract-result-columns sql)]
    (assoc named-params :result_columns result-cols)))

(defn xf-statement
  "每个元素均做转换拼接"
  ([params sql] (xf-statement params nil sql))
  ([params options sql]
   (let [result (parser sql)]
     (if (insta/failure? result)
       (log/error "parse sql error, sql template is " sql "\nfailure info:\n" result)
       (let [f (partial xf-ast params options)]
         (->> result rest (map f) (filter identity) (str/join \space)))))))
