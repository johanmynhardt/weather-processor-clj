#!/usr/bin/env bb32m
(ns user
  (:require [org.httpkit.server :as server]))

(def data-dir (or (System/getenv "DATA_DIR") (System/getProperty "user.dir")))

(println "data-dir: " data-dir)

(def base-dir (System/getProperty "user.dir"))

(def data-file (str data-dir "/weather.log"))

(def banner (slurp (str base-dir "/resources/banner.txt")))

(defn weather-index [] (slurp (str base-dir "/resources/weather-index.html")))

(defn K->°C [k]
  (- k 273.15))

(defn celcius-temperatures [record]
  (reduce
   (fn [acc next] (update-in acc [:main next] K->°C))
   record
   [:temp :feels_like :temp_min :temp_max]))

(defn drop-feels-like [record]
  (update-in record [:main] dissoc :feels_like))

(defn extract-rain-rate [{:keys [rain] :as record}]
  (update-in record [:rain] #(get % :1h 0)))

(defn full-date [{:keys [dt] :as record}]
  (assoc record :date (java.util.Date. (* 1000 dt))))

(def records-cache
  (atom {:updated nil :records nil}))

(defn update-records []
  (println "Updating cache @" (java.util.Date. (System/currentTimeMillis)))
  (let [lines (->> data-file slurp str/split-lines (remove empty?))]
    (reset! records-cache
            {:updated (System/currentTimeMillis)
             :records
             (cond
               (seq? lines)
               (->> lines
                    (map (comp #(select-keys % [:dt :main :rain])
                               #(json/parse-string % keyword)))

                    (map (comp drop-feels-like
                               celcius-temperatures
                               extract-rain-rate
                               full-date))
                    (take-last 24))

               :else [])})))

(defn process-dates [& [{:keys [print-records?] :or {print-records? false}}]]
  (let [now (System/currentTimeMillis)
        updated (:updated @records-cache)
        cache-expired? (or (nil? updated)
                           (> now (+ (* 5 60 1000)
                                     updated)))

        records (:records (if cache-expired? (update-records) @records-cache))]

    (when print-records?
      (doseq [record records]
        (println record)))

    records))

(defn chart-data []
  (let [records (process-dates)
        extract (fn [efn] (->> records (map efn)))

        labels (extract :date)
        temperature (extract #(get-in % [:main :temp]))
        humidity (extract #(get-in % [:main :humidity]))
        rain (extract :rain)
        pressure (extract #(get-in % [:main :pressure]))]

    {:type "bar"
     :data
     {:labels labels
      :datasets
      [{:label "Temperature"
        :backgroundColor "rgba(255,165,0, 0.5)"
        :data temperature
        :type "line"
        :yAxisID "other-y-axis"}

       {:label "Humidity"
        :backgroundColor "rgba(255,255,153, 0.5)"
        :data humidity
        :type "line"
        :yAxisID "other-y-axis"}

       {:label "Pressure"
        :backgroundColor "rgba(100, 100, 100, 0.5)"
        :data pressure
        :type "line"
        :yAxisID "1100-y-axis"}

       {:label "Rain Rate"
        :backgroundColor "rgba(0, 0, 255, 0.5)"
        :data rain
        :yAxisID "rain-y-axis"}]}

     :options
     {:scales
      {:yAxes
       [{:id "rain-y-axis" :type "linear" :position "right"}
        {:id "1100-y-axis" :type "linear" :position "right"}
        {:id "other-y-axis" :type "linear" :position "left"}]}}}))

(defn my-handler [{:keys [uri] :as req}]
  (condp = uri
    "/"
    {:status 200
     :body banner}

    "/weather"
    {:status 200
     :headers {"content-type" "text/html"}
     :body (weather-index)}

    "/weather/chart-data"
    (let [records (process-dates)
          labels (->> records (map :date) vec)
          data (->> records (map #(get-in % [:main :temp])) vec)
          humidity (->> records (map #(get-in % [:main :humidity])) vec)
          rain (->> records (map :rain) vec)
          pressure (->> records (map #(get-in % [:main :pressure])) vec)]
      {:status 200
       :headers {"content-type" "application/json"}
       :body (-> (chart-data) json/generate-string)})

    #_else
    {:status 200
     :body (str {:msg "Hello, World! I'm OK"
                 :request req})}))


(defn wrap-x-app-header [handler val]
  (fn [request]
    (-> (handler request)
        (assoc-in [:headers "x-app"] val))))

(def app
  (-> my-handler
      (wrap-x-app-header "Weather Processor")))

(def ctx (atom {:server nil}))

(defn start-server []
  (println "start server...")
  (swap! ctx assoc
         :server
         (server/run-server #'app {:legacy-return-value? false})))

(defn stop-server []
  (when-not (nil? (:server @ctx))
    (println "\nstopping server...")
    (server/server-stop! (:server @ctx))
    (swap! ctx assoc :server nil)))

(defn -main [& args]
  (-> (Runtime/getRuntime)
      (.addShutdownHook
       (Thread.
        #(do (stop-server)
             (println "bye")))))
  (print banner)
  (start-server)
  (println "running!")
  (while true
    (Thread/sleep 10000)))

(-main)


#_(slurp "http://localhost:8090/weather")
