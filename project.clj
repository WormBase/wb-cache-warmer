(defproject wb-cache-warmer "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/tools.cli "0.4.2"]
                 [clj-http "3.10.0"]
                 [environ "1.1.0"]
                 [factual/durable-queue "0.1.5"]
                 [mount "0.1.11"]
                 [com.taoensso/timbre "4.10.0"]
                 [com.datomic/datomic-pro "0.9.5703"
                  :exclusions [joda-time]]
                 [com.amazonaws/aws-java-sdk-dynamodb "1.11.82"]]
  :main ^:skip-aot wb-cache-warmer.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
