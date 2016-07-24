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
(def departure-in-regex #"(\d+):(\d+)")

(def one-second-in-millis 1000)
(def one-minute-in-millis (* 60 one-second-in-millis))
(def one-hour-in-millis (* 60 one-minute-in-millis))
(def one-day-in-millis (* 24 one-hour-in-millis))

(defn get-request-url [id]
  (-> (url base-url "fis" request-url-part)
      (assoc :query {:mastnr id :_ (-> (new Date) .getTime)})
      (str)))

(defn get-response-string [id]
  (let [response (client/get (get-request-url id))]
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

(defn get-departure-time-at [min-until-arrival]
  (let [calendar (Calendar/getInstance)]
    (doto calendar
      (.setTime (new Date))
      (.add Calendar/MINUTE (Integer/parseInt min-until-arrival)))
    (.getTime calendar)))

(defn get-departure-at-text [departure-value]
  (let [dateformatter (SimpleDateFormat. "HH:mm" Locale/GERMANY)]
    (if (= (get-departure-time departure-value) :now)
      (.format dateformatter (new java.util.Date))
      (let [[regex-matched min-until-arrival] (re-find departure-at-regex departure-value)]
        (if regex-matched ; need to transform "165min" into other format
          (.format dateformatter (get-departure-time-at min-until-arrival))
          departure-value))))) ; value is already in correct format

; TODO: Does not really look Clojure idiomatic, does it?
(defn get-departure-in-text [departure-value]
  (if (= (get-departure-time departure-value) :now)
    "0"
    (let [[regex-matched hours minutes] (re-find departure-in-regex departure-value)]
      (if regex-matched
        (let [hours (Integer/parseInt hours)
              minutes (Integer/parseInt minutes)
              arrival-in-millis (+ (* minutes one-minute-in-millis) (* hours one-hour-in-millis))
              now (java.util.Date.)
              arrival-in-millis (if (> (.getHours now) hours)
                                  (+ arrival-in-millis one-day-in-millis)
                                  arrival-in-millis)
              now-in-millis (+ (* (.getHours now) one-hour-in-millis) (* (.getMinutes now) one-minute-in-millis))]
          (str (/ (- arrival-in-millis now-in-millis) one-minute-in-millis) "min"))
        departure-value)))) ; value is already in correct format

(defn transform-model [raw-seq]
  (map #(-> %
            (assoc
              :departure-at (get-departure-at-text (:departure-value %))
              :departure-in (get-departure-in-text (:departure-value %)))
            (dissoc :departure-value))
       raw-seq))

(defroutes app-routes
  (GET "/departures/:id" [id]
    (rr/response
      (-> (get-response-string id)
          (get-departure-parts)
          (transform-model))))
  (route/not-found "Not Found"))

(def app
  (-> app-routes
    (ring-json/wrap-json-response)
    (ring-json/wrap-json-body)
    (ring-charset/wrap-default-charset "utf-8")))
