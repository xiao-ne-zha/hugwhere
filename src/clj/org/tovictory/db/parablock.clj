(ns org.tovictory.db.parablock
  (:require  [clojure.string :as str]
             [instaparse.core :refer [defparser] :as insta]
             [clojure.core.cache :as cache]
             [clojure.tools.logging :as log]
             [clojure.java.io :as io]
             [hugsql.parameters :as hp]))

(defparser ^:private parser (slurp (io/resource "parablock.bnf")) :auto-whitespace :standard)

(defn not-nil?
  "检查参数中指定路径的值是否不为nil。

   params - 参数map
   para-key-vec - 参数路径向量，如 [:a :b :c]
   返回: 当路径存在且值不为nil时返回true，否则返回false"
  [params para-key-vec]
  (not (nil? (get-in params para-key-vec))))

(defn contain-para-name?
  "检查参数map中是否包含指定的路径键。

   递归检查路径中的每一层是否都存在于参数map中。
   与 not-nil? 不同，此函数只检查键是否存在，不检查值是否为nil。

   params - 参数map
   para-key-vec - 参数路径向量，如 [:a :b :c]
   返回: 当路径中的所有键都存在时返回true，否则返回false"
  [params para-key-vec]
  (loop [fk (first para-key-vec) rkvec (rest para-key-vec) p params]
    (if (nil? fk)
      true
      (if-not (and (coll? p) (contains? p fk))
        false
        (recur (first rkvec) (rest rkvec) (get p fk))))))

(defn xf-parameter
  "对参数进行转换。当在块内时，能获取到值，才保留。

   单参数形式（无参数map）：
   直接转换为参数字符串，不进行值检查。用于块外的参数。

   双参数形式（带参数map）：
   根据参数值的存在性决定是否保留参数。用于块内的参数。

   参数格式：
   - [:para-name] 转换为 :para-name
   - [:para-type :para-name] 转换为 :para-type:para-name

   ast - 参数的AST节点，格式为 [:parameter para-type? para-name]
   params - 参数map
   options - 配置选项
     :pred-keep-fn - 参数保留谓词函数，默认为 not-nil?
                    接收 [params para-key-vec]，返回是否保留参数

   返回: 转换后的参数字符串，或不满足条件时返回nil"
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

   块的保留规则：
   1. 当任意parameter在请求参数map中没有值时，整个block返回nil
      如果block内有多个parameter，只要有一个没有值，整个block就返回nil
      主要考虑是如果允许部分parameter的值为nil，会导致SQL语法错误
   2. 当没有parameter且所有下层block均为nil时，返回nil
   3. 否则，保留本层的所有内容（包括other和parameter）

   ast - 块的AST节点，格式为 [:block & elements]
   params - 参数map
   options - 配置选项，传递给 xf-parameter

   返回: 转换后的SQL片段字符串，或不满足条件时返回nil"
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
  "对AST节点进行转换。

   单参数形式：
   options默认为nil

   双参数形式：
   根据节点类型进行相应的转换

   params - 参数map
   options - 配置选项
   ast - AST节点，可以是以下类型：
     :parameter - 参数节点，不在块内时不传参数map
     :block - 块节点，需要传递参数map
     其他 - 直接返回节点内容

   返回: 转换后的SQL片段字符串"
  ([params ast] (xf-ast params nil ast))
  ([params options [tp :as ast]]
   (case tp
     ;; 此时不在块内，所以不能传参数
     :parameter (xf-parameter ast)
     :block (xf-block ast params options)
     (second ast))))

(defn- extract-parameters
  "从AST中递归提取所有参数信息。

   根据参数所在位置（块内或块外）判断参数是否为必需参数。

   ast-node - AST节点
   in-block? - 布尔值，表示当前参数是否在块内
               true表示在块内，参数为可选（required=false）
               false表示不在块内，参数为必需（required=true）

   返回: 参数信息map的序列
         [{:para_name \"参数名\" :para_type \"参数类型\" :required true/false} ...]"
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

   参数说明：
   sql - SQL模板字符串

   返回格式：
   {:params [{:para_name \"para_name\" :para_type \"para_type\" :required true/false}]
    :system_params [{:para_name \"para_name\" :para_type \"para_type\" :required true/false}]}

   注意事项：
   1. 当para_type没有值时，取默认值 \"v\"
   2. 当para_name以`_`开头时，放到system_params中，否则放到params中
   3. 在{{}}块内的参数为可选参数（required=false），不在其中的为必选参数（required=true）
   4. 解析失败时返回空列表并记录错误日志"
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

   查找以'--~'开头（前面可能有空白符）且包含
   (if (seq (:_cols params))...的注释行，从该行的第二个字符串中提取列信息。

   参数说明：
   sql - 完整的SQL字符串

   返回: 列信息map的向量，格式如下：
         [{:col_name \"id\"} {:col_name \"customer_no\"} ...]
         未找到时返回nil，解析出错时记录日志并返回nil

   示例注释行：
   --~ (if (seq (:_cols params)) \"id, name, email\" \"id, name\")"
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

   这是一个组合函数，整合了 extract-named-parameters 和 extract-result-columns 的结果。

   参数说明：
   sql - 完整的SQL字符串

   返回格式：
   {:params [{:para_name \"para_name\" :para_type \"para_type\" :required true/false}]
    :system_params [{:para_name \"para_name\" :para_type \"para_type\" :required true/false}]
    :result_columns [{:col_name \"col_name\"}]}

   注意事项：
   1. 当para_type没有值时，取默认值 \"v\"
   2. 当para_name以`_`开头时，放到system_params中
   3. 在{{}}块内的参数为可选参数（required=false），不在其中的为必选参数（required=true）
   4. result_columns从注释行 --~ (if (seq (:_cols params))... 的第二个字符串中提取"
  [sql]
  (let [named-params (extract-named-parameters sql)
        result-cols (extract-result-columns sql)]
    (assoc named-params :result_columns result-cols)))

(defn xf-statement
  "将SQL模板语句转换为最终SQL语句。

   对SQL语句中的每个元素进行转换和拼接，处理参数替换和块条件。

   单参数形式：
   options默认为nil

   双参数形式：
   使用options配置进行转换

   参数说明：
   params - 参数map，包含参数键值对
   sql - SQL模板字符串
   options - 配置选项，传递给 xf-block 和 xf-parameter
     :pred-keep-fn - 参数保留谓词函数，默认为 not-nil?

   返回: 转换后的SQL字符串
         解析失败时返回nil并记录错误日志"
  ([params sql] (xf-statement params nil sql))
  ([params options sql]
   (let [result (parser sql)]
     (if (insta/failure? result)
       (log/error "parse sql error, sql template is " sql "\nfailure info:\n" result)
       (let [f (partial xf-ast params options)]
         (->> result rest (map f) (filter identity) (str/join \space)))))))
