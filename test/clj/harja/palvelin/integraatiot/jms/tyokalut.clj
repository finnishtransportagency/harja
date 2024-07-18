(ns harja.palvelin.integraatiot.jms.tyokalut
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.tyokalut.env :as env]
            [clojure.core.async :as a :refer [<!! <! >!! >! go go-loop thread timeout put! alts!! chan poll!]]
            [clojure.string :as clj-str]
            [cheshire.core :as cheshire]
            [org.httpkit.client :as http]))

;; Jolokia-operaatiot listattuna
;;  Artemis: http://localhost:8161/console/artemis/operations?tab=artemis&nid=root-org.apache.activemq.artemis-0.0.0.0
;;  Jolokia POST-protokolla: https://jolokia.org/reference/html/manual/jolokia_protocol.html#post-requests

(defn jms-laheta [jms-client jonon-nimi sanoma]
  (let [options {:timeout 5000
                 :basic-auth ["admin" "admin"]
                 :headers {"Content-Type" "application/xml"}
                 :body sanoma}]
    @(http/post (str "http://"
                     (case jms-client
                       "itmf" (env/env "HARJA_ITMF_BROKER_HOST" "localhost"))
                     ":"
                     (case jms-client
                       "itmf" (env/env "HARJA_ITMF_BROKER_AI_PORT" 8171))
                     "/api/message/"
                     jonon-nimi
                     "?type=queue")
                options)))

;; TODO: Artemiksessa ei ole defaulttina Rest API:a käytössä, vaan se pitäisi ottaa käyttöön erikseen
;;       Tässä on vielä Apache Classic brokerin käyttämä /api/message endpoint esimerkkinä, joka ei Artemisen suhteen toimi.
;;       Ideana on ollut testeissä ilmeisesti se, että olisi jokin helppo tapa lähettää Rest API:n kautta viestejä jonoille.
;;       ---
;;       Apache Classic myös ilmeisesti on toiminut siten, että se on luonut jonon automaattisesti brokerin puolella,
;;       mikäli siihen on yritetty lähettää viestiä ja jonoa ei ole olemassa.
;;       Ei ole varmuutta toimiiko Artemis samalla tavalla ennen kuin pääsemme kokeilemaan viestin lähetystä jonoon.
(defn jms-laheta-artemis [jms-client jonon-nimi sanoma]
  (let [options {:timeout 5000
                 :basic-auth ["admin" "admin"]
                 :headers {"Content-Type" "application/xml"}
                 :body sanoma}]
    @(http/post (str "http://"
                  (case jms-client
                    "itmf" (env/env "HARJA_ITMF_BROKER_HOST" "localhost"))
                  ":"
                  (case jms-client
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
                       "itmf" (env/env "HARJA_ITMF_BROKER_HOST" "localhost"))
                     ":"
                     (case jms-client
                       "itmf" (env/env "HARJA_ITMF_BROKER_AI_PORT" 8171))
                     "/api/jolokia/")
                options)))

(defn jms-jolokia-artemis
  "ActiveMQ artemis jolokia API: https://activemq.apache.org/components/artemis/documentation/latest/management#exposing-jmx-using-jolokia"
  [jms-client sanoma]
  (println "#### [jms-jolokia-artemis] Lähetettävä sanoma: " (cheshire/encode sanoma))
  (clojure.pprint/pprint sanoma)

  (let [options {:timeout 200
                 :basic-auth ["admin" "admin"]
                 :body (cheshire/encode sanoma)}
        vastaus @(http/post (str "http://"
                              (case jms-client
                                "itmf" (env/env "HARJA_ITMF_BROKER_HOST" "localhost"))
                              ":"
                              (case jms-client
                                "itmf" (env/env "HARJA_ITMF_BROKER_AI_PORT" 8171))
                              "/console/jolokia/")
                   options)]

    (println "#### -> Vastaus Artemikselta: ")
    (clojure.pprint/pprint vastaus)

    vastaus))

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

(defn jms-jolokia-connection-artemis
  "Työkalu yhteyden hallintaan ActiveMQ Artemis -palvelimella.
  Käyttää Artemiksen Jolokia API:a: https://activemq.apache.org/components/artemis/documentation/latest/management#exposing-jmx-using-jolokia"
  [jms-client attribute operation]
  (let [attribute (when attribute
                    {:type "read"
                     :attribute (case attribute
                                  :status "health")})
        operation (when operation
                    {:type "EXEC"
                     :operation (case operation
                                  :start "start"
                                  :stop "stop")})
        sanoma (merge {:mbean (str "org.apache.activemq.artemis:"
                                "broker=\"0.0.0.0\""
                                ",component=acceptors"
                                ",name=\"artemis\"")}
                 attribute
                 operation)]

    (println "#### [jms-jolokia-connection-artemis] Lähetetään Jolokia-viesti Artemikselle...")

    (jms-jolokia-artemis jms-client sanoma)))

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

;; TODO: Tätä ei taideta kutsua missään testissä.
;;       Jos tarve käyttää, niin pitää muodostaa sopiva mbean sanoma Artemikselle
(defn jms-jolokia-broker-artemis [jms-client attribute operation]
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
        ;; TODO: Tarkista miten muodostetaan mbean sanoma, jonka Artemis ymmärtää ja toteuttaa saman toiminnallisuuden kuin
        ;;       jms-jolokia-broker. Tämä on vielä keskeneräinen testi, joka ei toimi.
        sanoma (merge {:mbean (str "org.apache.activemq.artemis:"
                                "broker=\"0.0.0.0\"")}
                 attribute
                 operation)]
    (jms-jolokia jms-client sanoma)))

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

(defn jms-jolokia-jono-artemis
  "Työkalu jonojen hallintaan testeissä.
  Käyttää Artemiksen Jolokia API:a: https://activemq.apache.org/components/artemis/documentation/latest/management#exposing-jmx-using-jolokia"
  [jms-client jonon-nimi attribute operation]
  (let [attribute (when attribute
                    {:type "read"
                     :attribute (case attribute
                                  ;; Aiemmin käytettyjen Apache Classic attribuuttien (DispatchCount, InFlightCount, DequeueCount, EnqueueCount) dokumentaatio:
                                  ;;  https://activemq.apache.org/components/classic/documentation/how-do-i-find-the-size-of-a-queue
                                  ;;  * Enqueue Count - the total number of messages sent to the queue since the last restart
                                  ;;  * Dequeue Count - the total number of messages removed from the queue (ack’d by consumer) since last restart
                                  ;;  * Inflight Count - the number of messages sent to a consumer session and have not received an ack
                                  ;;  * Dispatch Count - the total number of messages sent to consumer sessions (Dequeue + Inflight)
                                  ;;  * Expired Count - the number of messages that were not delivered because they were expired

                                  ;; -- Vastaavat Apache Artemis attribuutit (kaivettu Apache Artmemis kälin kautta:
                                  ;; TODO: dispatch-countille en löytänyt ihan heti vastaavaa attribuuttia Artemiksesta, pitää penkoa lisää.
                                  :dispatch-count "DispatchCount"
                                  ;; Artemis: number of messages that this queue is currently delivering to its consumers
                                  :in-flight-count "DeliveringCount"
                                  ;; Artemis: Number of messages acknowledged from this queue since it was created
                                  :dequeue-count "MessagesAcknowledged"
                                  ;; Artemis: Number of messages added to this queue since it was created
                                  :enqueue-count "MessagesAdded")})
        operation (when operation
                    {:type "EXEC"
                     :operation (case operation
                                  ;; Vastaava operaatio Artemiksessa on "removeAllMessages"
                                  ;; Remove all the messages from the Queue (and returns the number of removed messages)
                                  :purge "removeAllMessages")})
        sanoma (merge {:mbean (str "org.apache.activemq.artemis:"
                                "broker=\"0.0.0.0\""
                                ",component=addresses"
                                ",address=\"" jonon-nimi "\""
                                ",subcomponent=queues"
                                ",routing-type=\"anycast\""
                                ",queue=\"" jonon-nimi "\"")}
                 attribute
                 operation)]

    (println "#### [jms-jolokia-jono-artemis] Lähetetään Jolokia-viesti Artemikselle...")

    (jms-jolokia-artemis jms-client sanoma)))

(defn itmf-laheta [jonon-nimi sanoma]
  (jms-laheta-artemis "itmf" jonon-nimi sanoma))

(defn itmf-jolokia [sanoma]
  (jms-jolokia-artemis "itmf" sanoma))

(defn itmf-jolokia-jono [jonon-nimi attribute operation]
  (jms-jolokia-jono-artemis "itmf" jonon-nimi attribute operation))

(defn itmf-jolokia-connection [attribute operation]
  (jms-jolokia-connection-artemis "itmf" attribute operation))

(defn itmf-jolokia-broker [attribute operation]
  (jms-jolokia-broker-artemis "itmf" attribute operation))

(defn jms-laheta-odota [jms-client jonon-nimi sanoma]
  (let [kasitellyn-tapahtuman-id (fn []
                                   (not-empty
                                     (first (q (str "SELECT it.id "
                                                    "FROM integraatiotapahtuma it"
                                                    "  JOIN integraatioviesti iv ON iv.integraatiotapahtuma=it.id "
                                                    "WHERE iv.sisalto ILIKE('" (clj-str/replace sanoma #"ä" "Ã¤") "') AND "
                                                    "it.paattynyt IS NOT NULL")))))]
    (case jms-client
      "itmf" (itmf-laheta jonon-nimi sanoma))
    (<!!
      (go-loop [kasitelty? (kasitellyn-tapahtuman-id)
                aika 0]
               (if (or kasitelty? (> aika 10))
                 kasitelty?
                 (do
                   (<! (timeout 1000))
                   (recur (kasitellyn-tapahtuman-id)
                          (inc aika))))))))
