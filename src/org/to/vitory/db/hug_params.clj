(ns org.to.vitory.db.hug-params
  (:require  [clojure.string :as str]
             [hugsql.parameters :as hp :refer [apply-hugsql-param]]))

(defprotocol LikeValueParam
  (like-value-param [param data options]))

(defprotocol LeftLikeValueParam
  (left-like-value-param [param data options]))

(defprotocol RightLikeValueParam
  (right-like-value-param [param data options]))

(extend-type Object
  LikeValueParam
  (like-value-param [param data options]
    ["?" (str \% (get-in data (hp/deep-get-vec (:name param))) \%)])

  LeftLikeValueParam
  (left-like-value-param [param data options]
    ["?" (str (get-in data (hp/deep-get-vec (:name param))) \%)])

  RightLikeValueParam
  (right-like-value-param [param data options]
    ["?" (str \% (get-in data (hp/deep-get-vec (:name param))))]))

(defmethod apply-hugsql-param :like [param data options] (like-value-param param data options))
(defmethod apply-hugsql-param :l [param data options] (like-value-param param data options))
(defmethod apply-hugsql-param :left-like [param data options] (left-like-value-param param data options))
(defmethod apply-hugsql-param :ll [param data options] (left-like-value-param param data options))
(defmethod apply-hugsql-param :right-like [param data options] (right-like-value-param param data options))
(defmethod apply-hugsql-param :rl [param data options] (right-like-value-param param data options))
