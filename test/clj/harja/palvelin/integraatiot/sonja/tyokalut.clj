(ns harja.palvelin.integraatiot.sonja.tyokalut
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.tyokalut.env :as env]
            [clojure.core.async :as a :refer [<!! <! >!! >! go go-loop thread timeout put! alts!! chan poll!]]
            [clojure.string :as clj-str]
            [cheshire.core :as cheshire]
            [org.httpkit.client :as http]))

(defonce ai-port 8161)

(defn sonja-laheta [jonon-nimi sanoma]
  (let [options {:timeout 5000
                 :basic-auth ["admin" "admin"]
                 :headers {"Content-Type" "application/xml"}
                 :body sanoma}]
    @(http/post (str "http://" (env/env "HARJA_SONJA_BROKER_HOST" "localhost") ":"
                     ai-port "/api/message/" jonon-nimi "?type=queue")
                options)))

(defn sonja-jolokia [sanoma]
  (let [options {:timeout 200
                 :basic-auth ["admin" "admin"]
                 :body (cheshire/encode sanoma)}]
    @(http/post (str "http://" (env/env "HARJA_SONJA_BROKER_HOST" "localhost") ":"
                     ai-port "/api/jolokia/")
                options)))

(defn sonja-jolokia-jono [jonon-nimi attribute operation]
  (let [attribute (when attribute
                    {:type "read"
                     :attribute (case attribute
                                  :dispatch-count "DispatchCount"
                                  :in-flight-count "InFlightCount"
                                  :dequeue-count "DequeueCount"
                                  :enqueue-count "EnqueueCount")})
        operation (when operation
                    {:type "EXEC"
                     :operation (case operation
                                  :purge "purge")
                     :arguments (case operation
                                  :purge [])})
        sanoma (merge {:mbean (str "org.apache.activemq:"
                                   "brokerName=localhost"
                                   ",destinationName=" jonon-nimi
                                   ",destinationType=Queue"
                                   ",type=Broker")}
                      attribute
                      operation)]
    (sonja-jolokia sanoma)))

(defn sonja-jolokia-connection [attribute operation]
  (let [attribute (when attribute
                    {:type "read"
                     :attribute (case attribute
                                  :status "health")})
        operation (when operation
                    {:type "EXEC"
                     :operation (case operation
                                  :start "start"
                                  :stop "stop")})
        sanoma (merge {:mbean (str "org.apache.activemq:"
                                   "type=Broker"
                                   ",brokerName=localhost"
                                   ",connector=clientConnectors"
                                   ",connectorName=openwire")}
                      attribute
                      operation)]
    (sonja-jolokia sanoma)))

(defn sonja-jolokia-broker [attribute operation]
  (let [attribute (when attribute
                    {:type "read"
                     :attribute (case attribute
                                  :status "health")})
        operation (when operation
                    {:type "EXEC"
                     :operation (case operation
                                  :start "start"
                                  :restart "restart"
                                  :stop "stop")})
        sanoma (merge {:mbean (str "org.apache.activemq:"
                                   "type=Broker"
                                   ",brokerName=localhost")}
                      attribute
                      operation)]
    (sonja-jolokia sanoma)))

(defn sonja-laheta-odota [jonon-nimi sanoma]
  (let [kasitellyn-tapahtuman-id (fn []
                                   (not-empty
                                     (first (q (str "SELECT it.id "
                                                    "FROM integraatiotapahtuma it"
                                                    "  JOIN integraatioviesti iv ON iv.integraatiotapahtuma=it.id "
                                                    "WHERE iv.sisalto ILIKE('" (clj-str/replace sanoma #"ä" "Ã¤") "') AND "
                                                    "it.paattynyt IS NOT NULL")))))]
    (sonja-laheta jonon-nimi sanoma)
    (<!!
      (go-loop [kasitelty? (kasitellyn-tapahtuman-id)
                aika 0]
               (if (or kasitelty? (> aika 10))
                 kasitelty?
                 (do
                   (<! (timeout 1000))
                   (recur (kasitellyn-tapahtuman-id)
                          (inc aika))))))))