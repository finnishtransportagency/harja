(ns harja.palvelin.integraatiot.jms.tyokalut
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.tyokalut.env :as env]
            [clojure.core.async :as a :refer [<!! <! >!! >! go go-loop thread timeout put! alts!! chan poll!]]
            [clojure.string :as clj-str]
            [cheshire.core :as cheshire]
            [org.httpkit.client :as http]))

(defn jms-laheta [jms-client jonon-nimi sanoma]
  (let [options {:timeout 5000
                 :basic-auth ["admin" "admin"]
                 :headers {"Content-Type" "application/xml"}
                 :body sanoma}]
    @(http/post (str "http://"
                     (case jms-client
                       "sonja" (env/env "HARJA_SONJA_BROKER_HOST" "localhost")
                       "itmf" (env/env "HARJA_ITMF_BROKER_HOST" "localhost"))
                     ":"
                     (case jms-client
                       "sonja" (env/env "HARJA_SONJA_BROKER_AI_PORT" 8161)
                       "itmf" (env/env "HARJA_ITMF_BROKER_AI_PORT" 8171))
                     "/api/message/"
                     jonon-nimi
                     "?type=queue")
                options)))

(defn jms-jolokia [jms-client sanoma]
  (let [options {:timeout 200
                 :basic-auth ["admin" "admin"]
                 :body (cheshire/encode sanoma)}]
    @(http/post (str "http://"
                     (case jms-client
                       "sonja" (env/env "HARJA_SONJA_BROKER_HOST" "localhost")
                       "itmf" (env/env "HARJA_ITMF_BROKER_HOST" "localhost"))
                     ":"
                     (case jms-client
                       "sonja" (env/env "HARJA_SONJA_BROKER_AI_PORT" 8161)
                       "itmf" (env/env "HARJA_ITMF_BROKER_AI_PORT" 8171))
                     "/api/jolokia/")
                options)))

(defn jms-jolokia-jono [jms-client jonon-nimi attribute operation]
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
    (jms-jolokia jms-client sanoma)))

(defn jms-jolokia-connection [jms-client attribute operation]
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
    (jms-jolokia jms-client sanoma)))

(defn jms-jolokia-broker [jms-client attribute operation]
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
    (jms-jolokia jms-client sanoma)))

(defn sonja-laheta [jonon-nimi sanoma]
  (jms-laheta "sonja" jonon-nimi sanoma))

(defn sonja-jolokia [sanoma]
  (jms-jolokia "sonja" sanoma))

(defn sonja-jolokia-jono [jonon-nimi attribute operation]
  (jms-jolokia-jono "sonja" jonon-nimi attribute operation))

(defn sonja-jolokia-connection [attribute operation]
  (jms-jolokia-connection "sonja" attribute operation))

(defn sonja-jolokia-broker [attribute operation]
  (jms-jolokia-broker "sonja" attribute operation))

(defn itmf-laheta [jonon-nimi sanoma]
  (jms-laheta "itmf" jonon-nimi sanoma))

(defn itmf-jolokia [sanoma]
  (jms-jolokia "itmf" sanoma))

(defn itmf-jolokia-jono [jonon-nimi attribute operation]
  (jms-jolokia-jono "itmf" jonon-nimi attribute operation))

(defn itmf-jolokia-connection [attribute operation]
  (jms-jolokia-connection "itmf" attribute operation))

(defn itmf-jolokia-broker [attribute operation]
  (jms-jolokia-broker "itmf" attribute operation))

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