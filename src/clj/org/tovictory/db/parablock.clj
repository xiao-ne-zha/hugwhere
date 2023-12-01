(ns org.tovictory.db.parablock
  (:require  [clojure.string :as str]
             [instaparse.core :refer [defparser] :as insta]
             [clojure.core.cache :as cache]
             [clojure.tools.logging :as log]
             [clojure.java.io :as io]
             [hugsql.parameters :as hp]))

(defparser ^:private parser (slurp (io/resource "parablock.bnf")) :auto-whitespace :standard)

(defn xf-parameter
  "对参数进行转换。当在块内时，能获取到值，才保留。"
  ([[_ & parameter]]
   (if (= 1 (count parameter))
     (str ":" (first parameter))
     (str ":" (first parameter) (second parameter))))
  ([[_ & parameter] params]
   (let [para-type (when (= 2 (count parameter)) (first parameter))
         para-name (if (= 1 (count parameter)) (first parameter) (second parameter))
         para-str (if (= 1 (count parameter)) (str ":" para-name) (str ":" para-type ":" para-name))]
     (let [para-key-vec (-> para-name keyword hp/deep-get-vec)]
       (when-not (nil? (get-in params para-key-vec)) para-str)))))

(defn xf-block
  "根据参数中值的情况，转换得到块的结果。
  1. 当parameter对应在请求参数map中没有值时，block返回nil.如果block内有多个parameter时，只要有一个没有值，整个block就返回nil.主要考虑是如果允许部分parameter的值为nil,会导致sql语法错误
  2. 当没有parameter时，下层的block全部返回nil值时，本block返回nil值
  3. 当有parameter时， 保留本层的other, parameter"
  [[_ & elements] params]
  (let [snippets (map (fn [[tp :as ast]]
                        (case tp
                          :parameter [:p (xf-parameter ast params)]
                          :block [:b (xf-block ast params)]
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

(defn xf-statement
  "每个元素均做转换拼接"
  [params sql]
  (let [result (parser sql)]
    (if (insta/failure? result)
      (log/error "parse sql error, sql template is " sql "\nfailure info:\n" result)
      (str/join \space
                (map (fn [[tp :as ast]]
                       (case tp
                         ;; 此时不在块内，所以不能传参数
                         :parameter (xf-parameter ast)
                         :block (xf-block ast params)
                         (second ast)))
                     (rest result))))))
