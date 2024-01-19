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

(defn xf-statement
  "每个元素均做转换拼接"
  ([params sql] (xf-statement params nil sql))
  ([params options sql]
   (let [result (parser sql)]
     (if (insta/failure? result)
       (log/error "parse sql error, sql template is " sql "\nfailure info:\n" result)
       (let [f (partial xf-ast params options)]
         (->> result rest (map f) (filter identity) (str/join \space)))))))
