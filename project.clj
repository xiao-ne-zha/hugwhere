(defproject org.clojars.xiao-ne-zha/hugwhere "1.1.0-SNAPSHOT"
  :description "dynamic `where conditions` when you use hugsql to create your function that access database
当你使用hugsql来编写数据库访问函数时，该库可以让你省去编写动态拼接where条件的繁琐"
  :url "https://github.com/xiao-ne-zha/hugwhere"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [com.layerware/hugsql "0.5.3"]
                 [instaparse "1.4.12"]
                 [org.clojure/tools.logging "1.2.4"]
                 [org.clojure/core.cache "1.0.225"]
                 [robert/hooke "1.3.0"]]
  :deploy-repositories [["releases" {:url "https://repo.clojars.org"}]
                        ["snapshots" :clojars]]
  :repl-options {:init-ns org.tovitory.db.hugwhere})
