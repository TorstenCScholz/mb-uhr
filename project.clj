(defproject mb-uhr "0.1.0"
  :description "The Münster Bus departure API translator"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [compojure "1.5.1"]
                 [ring/ring-defaults "0.2.1"]
                 [ring/ring-json "0.4.0"]
                 [ring/ring-headers "0.2.0"]
                 [org.clojure/core.async "0.2.374"]
                 [clj-http "2.2.0"]
                 [com.cemerick/url "0.1.1"]]
  :plugins [[lein-ring "0.9.7"]]
  :ring {:handler mb-uhr.handler/app}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.3.0"]]}})
