(defproject org.to.victory.db/hugwhere "0.1.0-SNAPSHOT"
  :description "dynamic `where conditions` when you use hugsql to create your function that access database\n 当你使用hugsql来编写数据库访问函数时，该库可以让你省去编写动态拼接where条件的繁琐"
  :url "https://github.com/xiao-ne-zha/hugwhere"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [com.layerware/hugsql "0.4.9"]
                 [instaparse "1.4.9"]
                 [robert/hooke "1.3.0"]]
  :repl-options {:init-ns org.to.vitory.db.hugwhere})
