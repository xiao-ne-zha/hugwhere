(ns org.tovictory.db.japi
  (:require [org.tovictory.db.hack-hugsql :refer [hack-hugsql]]
            [clojure.java.io :as io]
            [hawk.core :as hawk]
            [clojure.tools.logging :as log]
            [org.tovictory.db.listsql :refer [listsql->hugsql]]
            [hugsql.core :as hs])
  (:import [org.tovictory.db.japi SqlVo])
  (:gen-class :name org.tovictory.db.japi.SqlGetterImpl
              :implements [org.tovictory.db.japi.SqlGetter]
              :init init
              :post-init post-init
              :constructors {[java.util.List] []}
              :state info))

(hack-hugsql)
(import 'org.tovictory.db.japi.SqlGetterImpl)

(defn- sql-file?
  ([file]
   (let [file (io/file file)]
     (and file (.isFile file) (re-matches #"^.+\.sql$" (.getName file)))))
  ([_ {:keys [file]}]
   (and file (.isFile file) (re-matches #"^.+\.sql$" (.getName file)))))

(defn- load-query-file [file]
  (let [filename (.getName file)]
    (try (let [hsql (->> file slurp listsql->hugsql)
               qt (hs/map-of-sqlvec-fns-from-string hsql)]
           (when-not (empty? qt)
             (log/info "高级查询配置文件" filename "分析完成")
             qt))
         (catch Exception e
           (log/warn "高级查询配置文件" filename "分析失败，错误原因：" (.getMessage e))))))

(defn- load-all-query-files [^java.util.List paths]
  (let [files (mapcat #(-> % io/file file-seq) paths)]
    (->> files
         (filter sql-file?)
         (map load-query-file)
         (reduce merge))))

(defn- watch-query-file [paths on-change]
  (hawk/watch! [{:paths paths
                 :filter sql-file?
                 :handler (fn [ctx, e]
                            (when (#{:modify :create} (:kind e))
                              (log/info "发生文件增加修改事件：" e)
                              (on-change (:file e)))
                            ctx)}]))

(defn- -init [^java.util.List watch-paths]
  [[] (atom {})])

(defn- -post-init [this ^java.util.List watch-paths]
  (let [sfm (load-all-query-files watch-paths)
        on-change (fn [file]
                    (swap! (.info this) merge (load-query-file file)))]
    (watch-query-file watch-paths on-change)
    (swap! (.info this) merge sfm)))

(defn- ^SqlVo -getSql [this ^String sqlFname, ^java.util.Map params]
  (let [params (update-keys params keyword)
        fm @(.info this)]
    (when-let [f (-> sqlFname (str "-sqlvec") keyword fm :fn)]
      (let [[sql & args] (f params)]
        (SqlVo. sql args)))))

(comment
  (def sg (SqlGetterImpl. ["/media/zszhang/Data/workspaces/tydicgit/bsaids/sysmng/sql"]))
  (doseq [p [nil {"dept_id" 108}]]
    (let [sv (.getSql sg "test-select-dept" p)]
      (println {:sql (.getSql sv)
                :args (.getArgs sv)}))))
