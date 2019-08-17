(ns org.to.vitory.db.hug-impl
  (:require  [clojure.string :as str]
             [instaparse.core :refer [defparser]]
             [clojure.java.io :as io]))

(defparser ^:private hwp (slurp (io/resource "hugwhere.bnf")) :auto-whitespace :standard)
