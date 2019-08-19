(defproject org.to.victory.example/hello-hugwhere "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [migratus "1.2.4"]
                 [conman "0.8.2"]
                 [org.to.victory.db/hugwhere "0.1.0-SNAPSHOT"]
                 [com.h2database/h2 "1.4.193"]
                 [ch.qos.logback/logback-classic "1.2.3"]
                 [mount "0.1.16"]]

  :plugins [[migratus-lein "0.7.2"]]
  :migratus {:store :database,
             :migration-dir "migrations"
             :db "jdbc:h2:./db/dev_user.db"}
  :main ^:skip-aot org.to.victory.example.hello-hugwhere
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})