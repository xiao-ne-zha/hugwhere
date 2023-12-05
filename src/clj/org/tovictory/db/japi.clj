(ns org.tovictory.db.japi
  (:require [org.tovictory.db.hack-hugsql :refer [hack-hugsql]]
            [clojure.java.io :as io]
            [hawk.core :as hawk]
            [clojure.tools.logging :as log]
            [org.tovictory.db.listsql :refer [listsql->hugsql cudsql->hugsql]]
            [hugsql.core :as hs])
  (:import [org.tovictory.db.japi SqlVo])
  (:gen-class :name org.tovictory.db.japi.SqlGetterImpl
              :implements [org.tovictory.db.japi.SqlGetter]
              :init init
              :post-init post-init
              :constructors {[java.util.List java.util.List java.util.List] []}
              :state info))

(hack-hugsql)
(import 'org.tovictory.db.japi.SqlGetterImpl)

(defn- sql-file?
  ([file]
   (let [file (io/file file)]
     (and file (.isFile file) (re-matches #"^.+\.sql$" (.getName file)))))
  ([_ {:keys [file]}]
   (and file (.isFile file) (re-matches #"^.+\.sql$" (.getName file)))))

(defn- load-easy-sql-file [pf file]
  (let [filename (.getName file)]
    (try (let [hsql (->> file slurp pf)
               qt (hs/map-of-sqlvec-fns-from-string hsql)]
           (when-not (empty? qt)
             (log/info "高级查询配置文件" filename "分析完成")
             qt))
         (catch Exception e
           (log/warn "高级查询配置文件" filename "分析失败，错误原因：" (.getMessage e))))))

(defn- load-sql-paths [f ^java.util.List paths]
  (let [files (mapcat #(-> % io/file file-seq) paths)
        load-sql-file (partial load-easy-sql-file f)]
    (->> files
         (filter sql-file?)
         (map load-sql-file)
         (reduce merge))))

(defn- watch-sql-files [paths on-changes]
  (hawk/watch!
   (mapv (fn [p f]
           {:paths p
            :filter sql-file?
            :handler (fn [ctx, e]
                       (when (#{:modify :create} (:kind e))
                         (log/info "发生文件增加修改事件：" e)
                         (f (:file e)))
                       ctx)})
         paths on-changes)))

(defn- -init [^java.util.List listsql-paths ^java.util.List cudsql-paths ^java.util.List nativesql-paths]
  [[] (atom {})])

(defn- -post-init [this ^java.util.List listsql-paths ^java.util.List cudsql-paths ^java.util.List nativesql-paths]
  (let [lfm (load-sql-paths listsql->hugsql listsql-paths)
        cfm (load-sql-paths cudsql->hugsql cudsql-paths)
        nfm (load-sql-paths identity nativesql-paths)]
    (watch-sql-files [listsql-paths cudsql-paths nativesql-paths]
                      [#(swap! (.info this) merge (load-easy-sql-file listsql->hugsql %))
                       #(swap! (.info this) merge (load-easy-sql-file cudsql->hugsql %))
                       #(swap! (.info this) merge (load-easy-sql-file identity %))])
    (swap! (.info this) merge lfm cfm nfm)))

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
