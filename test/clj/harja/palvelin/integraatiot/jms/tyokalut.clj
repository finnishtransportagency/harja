(ns harja.palvelin.integraatiot.jms.tyokalut
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.tyokalut.env :as env]
            [clojure.core.async :as a :refer [<!! <! >!! >! go go-loop thread timeout put! alts!! chan poll!]]
            [clojure.string :as clj-str]
            [cheshire.core :as cheshire]
            [org.httpkit.client :as http]
            [taoensso.timbre :as log]))

;; Jolokia-operaatiot listattuna
;;  Artemis: http://localhost:8161/console/artemis/operations?tab=artemis&nid=root-org.apache.activemq.artemis-0.0.0.0
;;  Jolokia POST-protokolla: https://jolokia.org/reference/html/manual/jolokia_protocol.html#post-requests

(defn jms-jolokia-api-kutsu--artemis
  "ActiveMQ Artemis Jolokia API-kutsu
  https://activemq.apache.org/components/artemis/documentation/latest/management#exposing-jmx-using-jolokia"
  ([jms-client sanoma] (jms-jolokia-api-kutsu--artemis jms-client sanoma nil))
  ([jms-client sanoma options]
  #_(println "#### [jms-jolokia-api-kutsu--artemis] Lähetettävä sanoma: ")
  #_(clojure.pprint/pprint sanoma)

  (let [options (merge
                  {:timeout 200
                   :basic-auth [(env/env "HARJA_ITMF_BROKER_KAYTTAJA" "admin")
                                (env/env "HARJA_ITMF_BROKER_SALASANA" "admin")]
                   :body (cheshire/encode sanoma)}
                  options)
        vastaus @(http/post (str "http://"
                              (case jms-client
                                "itmf" (env/env "HARJA_ITMF_BROKER_HOST" "localhost"))
                              ":"
                              (case jms-client
                                "itmf" (env/env "HARJA_ITMF_BROKER_AI_PORT" 8171))
                              "/console/jolokia/")
                   options)]

    #_(println "##### --> Vastaus Artemikselta: ")
    #_(clojure.pprint/pprint vastaus)

    vastaus)))

(defn jms-laheta-jonoon--artemis [jms-client jonon-nimi sanoma]
  "Erillinen työkalu viestin lähettämiseen ActiveMQ Artemis jonoon testeissä.
  Viesti lähetetään mbean-tyyppisenä sanomana ActiveMQ Artemiksen management API:n kautta (Jolokia)."

  (let [sanoma-mbean {:mbean (str "org.apache.activemq.artemis:"
                               "broker=\"0.0.0.0\""
                               ",component=addresses"
                               ",address=\"" jonon-nimi "\"")
                      :type "EXEC"
                      :operation "sendMessage(java.util.Map, int, java.lang.String, boolean, java.lang.String, java.lang.String, boolean)"
                      ;; Operaatiofuntion argumentit
                      :arguments [;; Headers
                                  {}
                                  ;; Type (3 = TEXT)
                                  3
                                  ;; Body
                                  sanoma
                                  ;; Durable = true/false
                                  ;; Durable messages are persisted to disk and survive broker restarts or crashes.
                                  true
                                  ;; User
                                  nil
                                  ;; Password
                                  nil
                                  ;; Create Message ID
                                  false
                                  ]}
        options {:timeout 5000}]
    (jms-jolokia-api-kutsu--artemis jms-client sanoma-mbean options)))

(defn jms-jolokia-connection--artemis
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

    (jms-jolokia-api-kutsu--artemis jms-client sanoma)))

;; TODO: Tätä ei kutsuta tällä hetkellä missään testissä.
;;       Jos tulee tarve hallita itse JMS brokeria, niin pitää muodostaa sopiva mbean sanoma, jonka ActiveMQ Artemis ymmärtää.
;;       Tämä sisältää vielä vanhaa ActiveMQ Classic -koodia.
(defn jms-jolokia-broker--artemis [jms-client attribute operation]
  (let [attribute (when attribute
                    {:type "read"
                     :attribute (case attribute
                                  :status "health")})
        ;; TODO: Käytä operaatioita, jotka ActiveMQ Artemis ymmärtää.
        operation (when operation
                    {:type "EXEC"
                     :operation (case operation
                                  :start "start"
                                  :restart "restart"
                                  :stop "stop")})
        sanoma (merge {:mbean (str "org.apache.activemq.artemis:"
                                "broker=\"0.0.0.0\"")}
                 attribute
                 operation)]
    (log/warn "Tätä funktiota ei ole vielä toteutettu Artemis Jolokia API:lle.")

    (jms-jolokia-api-kutsu--artemis jms-client sanoma)))

(defn jms-jolokia-jono--artemis
  "Työkalu jonojen hallintaan testeissä.
  Käyttää Artemiksen Jolokia API:a: https://activemq.apache.org/components/artemis/documentation/latest/management#exposing-jmx-using-jolokia"
  [jms-client jonon-nimi attribute operation]
  (let [attribute (when attribute
                    {:type "read"
                     :attribute (case attribute
                                  ;; Aiemmin käytettyjen Apache Classic attribuuttien dokumentaatio
                                  ;; Testeissä käytettiin attribuutteja: DispatchCount, InFlightCount, DequeueCount, EnqueueCount
                                  ;;  https://activemq.apache.org/components/classic/documentation/how-do-i-find-the-size-of-a-queue
                                  ;;  * Enqueue Count - the total number of messages sent to the queue since the last restart
                                  ;;  * Dequeue Count - the total number of messages removed from the queue (ack’d by consumer) since last restart
                                  ;;  * Inflight Count - the number of messages sent to a consumer session and have not received an ack
                                  ;;  * Dispatch Count - the total number of messages sent to consumer sessions (Dequeue + Inflight)
                                  ;;  * Expired Count - the number of messages that were not delivered because they were expired

                                  ;; Uudet ActiveMQ Artemis attribuutit:
                                  ;; Artemis: Number of messages acknowledged from this queue since it was created (HOX! Siitä lähtien kun jono luotu)
                                  :messages-acknowledged "MessagesAcknowledged"
                                  ;; Artemis: Number of messages added to this queue since it was created (HOX! Siitä lähtien kun jono luotu)
                                  :messages-added "MessagesAdded"
                                  ;; Artemis: number of messages that this queue is currently delivering to its consumers
                                  :delivering-count "DeliveringCount"
                                  ;; Artemis: Number of messages currently in this queue (includes scheduled, paged, and in-delivery messages)
                                  :message-count "MessageCount")})
        operation (when operation
                    {:type "EXEC"
                     :operation (case operation
                                  ;; Vastaava operaatio Artemiksessa on "removeAllMessages()"
                                  ;; Artemis: Remove all the messages from the Queue (and returns the number of removed messages)
                                  :purge "removeAllMessages()")})
        sanoma (merge {:mbean (str "org.apache.activemq.artemis:"
                                "broker=\"0.0.0.0\""
                                ",component=addresses"
                                ",address=\"" jonon-nimi "\""
                                ",subcomponent=queues"
                                ",routing-type=\"anycast\""
                                ",queue=\"" jonon-nimi "\"")}
                 attribute
                 operation)]

    (jms-jolokia-api-kutsu--artemis jms-client sanoma)))

(defn itmf-laheta [jonon-nimi sanoma]
  (jms-laheta-jonoon--artemis "itmf" jonon-nimi sanoma))

(defn itmf-jolokia [sanoma]
  (jms-jolokia-api-kutsu--artemis "itmf" sanoma))

(defn itmf-jolokia-jono [jonon-nimi attribute operation]
  (jms-jolokia-jono--artemis "itmf" jonon-nimi attribute operation))

(defn itmf-jolokia-connection [attribute operation]
  (jms-jolokia-connection--artemis "itmf" attribute operation))

(defn itmf-jolokia-broker [attribute operation]
  (jms-jolokia-broker--artemis "itmf" attribute operation))

(defn jms-laheta-odota [jms-client jonon-nimi sanoma]
  (let [kasitellyn-tapahtuman-id (fn []
                                   (not-empty
                                     (first (q (str "SELECT it.id "
                                                 "FROM integraatiotapahtuma it"
                                                 "  JOIN integraatioviesti iv ON iv.integraatiotapahtuma=it.id "
                                                    "WHERE iv.sisalto ILIKE('" sanoma "') AND "
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
