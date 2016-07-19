(ns mb-uhr.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.middleware.json :as ring-json]
            [ring.middleware.default-charset :as ring-charset]
            [ring.util.response :as rr]
            [clj-http.client :as client]
            [cemerick.url :refer (url)])
  (:import (java.util Date Locale Calendar)
           (java.text SimpleDateFormat)))

(def base-url "http://www.stadtwerke-muenster.de")
(def request-url-part "ajaxrequest.php")
(def departure-regex #"<div class=\"\w+\"><div class=\"line\">([^<]+?)</div><div class=\"direction\">([^<]+?)</div><div class=\"\w+\">((?:[^<]*?)|(?:<div class=\"borden\"></div>))</div><br class=\"clear\" /></div>")
(def departure-at-regex #"(\d+)min")

(defn get-request-url [id]
  (-> (url base-url "fis" request-url-part)
      (assoc :query {:mastnr id :_ (-> (new Date) .getTime)})
      (str)))

(defn get-response-string [id]
  (let [response (client/get (get-request-url id))]
    (println (str "Response: " response))
    (:body response)))

(defn get-departure-parts [response-string]
  (let [matches (re-seq departure-regex response-string)]
      (println (map #(-> (zipmap [:_ :bus-line :_ :departure-value] %) (dissoc :_)) matches))
      (map #(-> (zipmap [:_ :bus-line :_ :departure-value] %) (dissoc :_)) matches)))

(defn get-departure-time [dep-time-str]
  (cond
    (.startsWith dep-time-str "<div") :now
    (.contains dep-time-str ":") :departure-at
    :else :departure-in))

(defn calculate-departure-at [departure-value]
  (if (= (get-departure-time departure-value) :now)
    (java.util.Date.)
    (let [[regex-matched min-until-arrival] (re-find departure-at-regex departure-value)]
      (if regex-matched ; need to transform "165min" into other format
        (let [dateformatter (SimpleDateFormat. "HH:mm" Locale/GERMANY)
              calendar (Calendar/getInstance)]
          (doto calendar
            (.setTime (new Date))
            (.add Calendar/MINUTE (Integer/parseInt min-until-arrival)))
          (.format dateformatter (.getTime calendar)))
        departure-value)))) ; value is already in correct format

(defn calculate-departure-in [departure-value]
  (if (= (get-departure-time departure-value) :now)
    "0"
    :in)) ; TODO

(defn transform-model [raw-seq]
  (map #(-> %
            (assoc
              :departure-at (calculate-departure-at (:departure-value %))
              :departure-in (calculate-departure-in (:departure-value %)))
            (dissoc :departure-value))
       raw-seq))

(defroutes app-routes
  (GET "/departures/:id" [id]
    (rr/response
      (-> (get-response-string id) ; TODO: This just returns the first departure
          (get-departure-parts)
          (transform-model))))
  (route/not-found "Not Found"))

(def app
  (-> app-routes
    (ring-json/wrap-json-response)
    (ring-json/wrap-json-body)
    (ring-charset/wrap-default-charset "utf-8")))
