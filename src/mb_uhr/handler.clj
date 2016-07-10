(ns mb-uhr.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.middleware.json :as ring-json]
            [ring.middleware.default-charset :as ring-charset]
            [ring.util.response :as rr]
            [clj-http.client :as client]
            [cemerick.url :refer (url)])
  (:import (java.util Date)))

(def base-url "http://www.stadtwerke-muenster.de")
(def request-url-part "ajaxrequest.php")
(def departure-regex #"<div class=\"\w+\"><div class=\"line\">([^<]+?)</div><div class=\"direction\">([^<]+?)</div><div class=\"\w+\">((?:[^<]*?)|(?:<div class=\"borden\"></div>))</div><br class=\"clear\" /></div>")

(defn get-response-string [id]
  (:body (client/get (get-request-url id))))

(defn get-request-url [id]
  (-> (url base-url "fis" request-url-part)
      (assoc :query {:mastnr id :_ (-> (new java.util.Date) .getTime)})
      (str)))

(defn get-departure-parts [response-string]
  (let [[_ bus-line _ departure-value] (re-find departure-regex response-string)]
    {:bus-line bus-line :departure-value departure-value}))

(defn get-departure-time [dep-time-str]
  (cond
    (.startsWith dep-time-str "<div") :now
    (.contains dep-time-str ":") :departure-at
    :else :departure-in))

(defn calculate-departure-at [departure-value]
  (if (= (get-departure-time departure-value) :now)
    (java.util.Date.)
    :at)) ; TODO

(defn calculate-departure-in [departure-value]
  (if (= (get-departure-time departure-value) :now)
    "0"
    :in)) ; TODO

(defn transform-model [raw]
  (-> raw (assoc
            :departure-at (calculate-departure-at (:departure-value raw))
            :departure-in (calculate-departure-in (:departure-value raw)))
          (dissoc :departure-value)))

(defroutes app-routes
  (GET "/departures/:id" [id]
    (rr/response ; TODO: This just returns the first departure
      (-> (get-response-string id)
          (get-departure-parts)
          (transform-model))))
  (route/not-found "Not Found"))

(def app
  (-> app-routes
    (ring-json/wrap-json-response)
    (ring-json/wrap-json-body)
    (ring-charset/wrap-default-charset "utf-8")))
