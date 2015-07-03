(ns harja.palvelin.komponentit.sonja
  "Komponentti Sonja-väylään liittymiseksi."
  (:require [com.stuartsierra.component :as component]
            [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [clojure.data.zip.xml :as z]
            [clojure.core.async :refer [go <! >! thread >!!]]
            [taoensso.timbre :as log]
            [hiccup.core :refer [html]]
            [clojure.string :as str])
  (:import (progress.message.jclient QueueConnectionFactory)
           (javax.jms Session Destination Queue TextMessage)))


;; SONJA JMS jonoihin kytkeytyminen, alustavaa testailukoodia tulevaisuuden lähtökohdaksi
;; kytkeydy jonoon:
;; ssh -L2511:192.83.32.231:2511 harja-mule1-stg
;;
;; (def qcf (QueueConnectionFactory. "localhost:2511"))
;; (def conn (.createConnection qcf "harja" "harjaxx"))
;; (def s (session conn))
;; (def s-to-h (queue s +sampo-to-harja+))
;; (def c (consumer s s-to-h))
;; (.start conn)
;; Viestin vastaanotto: (blokkaa, jos viestiä ei ole odottamassa)
;; (def m (.receive c))


(defprotocol Sonja
  (kuuntele [this jonon-nimi kuuntelija-fn]
    "Lisää uuden kuuntelijan annetulle jonolle. Jos jonolla on monta kuuntelijaa, viestit välitetään jokaiselle kuuntelijalle.
Kuuntelijafunktiolle annetaan suoraan javax.jms.Message objekti. Kuuntelija blokkaa käsittelyn ajan, joten samasta jonosta voidaan lukea vain yksi viesti kerrallaan. Jos käsittelijä haluaa tehdä jotain pitkäaikaista, täytyy sen hoitaa se uudessa säikeessä.")

  (laheta [this jono viesti]
    "Lähettää viestin nimettyyn jonoon. Palauttaa message id:n."))

(defn- yhdista [{:keys [url kayttaja salasana tyyppi]}]
  (log/info "Yhdistetään JMS-brokeriin (tyyppi:" tyyppi ") URL:lla:" url )
  (let [qcf (if (= tyyppi :activemq)
              (org.apache.activemq.ActiveMQConnectionFactory.)
              (doto (QueueConnectionFactory. url)
                (.setFaultTolerant true)
                (.setFaultTolerantReconnectTimeout (int 30))))
        conn (.createConnection qcf kayttaja salasana)]
    (.start conn)
    conn))


(defn- jonon-kuuntelija
  "Luo jonon kuuntelijan annetulle istunnolle."
  [istunto jonon-nimi kasittelija]
  (let [jono (.createQueue istunto jonon-nimi)
        consumer (.createConsumer istunto jono)]
    (thread
      (try
        (loop [v (.receive consumer)]
          (log/debug "Vastaanotettu viesti Sonja jonosta: " jonon-nimi)
          (try
            (kasittelija v)
            (catch Exception e
              (log/warn e (str "Viestin käsittelijä heitti poikkeuksen, jono: " jonon-nimi))))
          (recur (.receive consumer)))
        (catch Exception e
          (log/warn e (str "Virhe Sonja kuuntelijassa, jono: " jonon-nimi)))))
    consumer))

(defn- varmista-jono
  "Varmistaa, että nimetylle jonolle on luotu Queue instanssi. Palauttaa jonon."
  [istunto jonot jonon-nimi]
  (if-let [jono (get-in @jonot [jonon-nimi :queue])]
    jono
    (let [q (.createQueue istunto jonon-nimi)]
      (swap! jonot assoc-in [jonon-nimi :queue] q)
      q)))

(defn- varmista-producer
  "Varmistaa, että nimetylle jonolle on luotu producer viestien lähettämistä varten. Palauttaa producerin."
  [istunto jonot jonon-nimi]
  (if-let [producer (get-in @jonot [jonon-nimi :producer])]
    producer
    (let [q (varmista-jono istunto jonot jonon-nimi)
          p (.createProducer istunto q)]
      (swap! jonot assoc-in [jonon-nimi :producer] p)
      p)))

(defprotocol LuoViesti
  (luo-viesti [x istunto]))

(extend-protocol LuoViesti

  java.lang.String
  (luo-viesti [s istunto]
    (doto (.createTextMessage istunto)
      (.setText s))))


(defrecord SonjaYhteys [asetukset istunto yhteys jonot]
  component/Lifecycle
  (start [this]
    (let [yhteys (yhdista asetukset)
          istunto (.createSession yhteys false Session/AUTO_ACKNOWLEDGE)]
      (assoc this
        :yhteys yhteys
        :istunto istunto

        ;; Jonot on mäppäys jonon nimestä {:queue #<queu impl> :consumer #<consumer impl> :producer #<producer impl> :kuuntelijat #{}}
        :jonot (atom {}))))

  (stop [{:keys [istunto yhteys] :as this}]
    (when istunto
      (.close istunto))
    (when yhteys
      (.close yhteys))
    (assoc this
      :yhteys nil
      :istunto nil
      :jonot nil))

  Sonja
  (kuuntele [{:keys [istunto jonot]} jonon-nimi kuuntelija-fn]
    (let [jono (get @jonot jonon-nimi)]
      (if (:consumer jono)
        (do
          (log/info "Lisätään kuuntelija jonoon " jonon-nimi)
          (swap! jonot update-in [jonon-nimi :kuuntelijat] conj kuuntelija-fn))

        (do (log/info "Ensimmäinen kuuntelija jonolle " jonon-nimi ", luodaan consumer.")
            (let [consumer (jonon-kuuntelija istunto jonon-nimi
                                             #(doseq [k (get-in @jonot [jonon-nimi :kuuntelijat])]
                                               (k %)))]
              (swap! jonot update-in [jonon-nimi] assoc
                     :consumer consumer
                     :kuuntelijat #{kuuntelija-fn}))))
      ;; palauta funktio, jolla kuuntelu voidaan lopettaa
      #(swap! jonot update-in [jonon-nimi :kuuntelijat] disj kuuntelija-fn)))

  (laheta [{:keys [istunto jonot]} jonon-nimi viesti]
    (try
      (let [producer (varmista-producer istunto jonot jonon-nimi)
            msg (luo-viesti viesti istunto)]
        (log/debug "Lähetetään JMS viesti ID:llä " (.getJMSMessageID msg))
        (.send producer msg)
        (.getJMSMessageID msg))

      (catch Exception e
        (log/error e "Virhe JMS-viestin lähettämisessä jonoon: " jonon-nimi)))))


(defn luo-sonja [asetukset]
  (if (and asetukset (not (str/blank? (:url asetukset))))
    (->SonjaYhteys asetukset nil nil nil)
    (reify
      component/Lifecycle
      (start [this] this)
      (stop [this] this)

      Sonja
      (kuuntele [this jonon-nimi kuuntelija-fn]
        (log/debug "Feikki Sonja, aloita muka kuuntelu jonossa: " jonon-nimi)
        #(log/debug "Feikki Sonja, lopeta muka kuuntelu jonossa: " jonon-nimi))
      (laheta [this jonon-nimi viesti]
        (log/debug "Feikki Sonja, lähetä muka viesti jonoon: " jonon-nimi)
        (str "ID:" (System/currentTimeMillis))))))


;;(def +sampo-to-harja+ "Harja13-16.SampoToHarja.Msg") ;; SAMPO -> Harja (SAMPOn hankkeet,urakat, jne)
;;(def +sampo-to-harja-ack+ "Harja13-16.HarjaToSampo.Ack") ;; Harjan vastausviestit edellisiin
;;
;;
;;(def +harja-to-sampo+ "Harja13-16.HarjaToSampo.Msg")
;,(def +harja-to-sampo-ack+ "Harja13-16.SampoToHarja.Ack")
;;
;;
;;(defn parse-resource [r]
;,  {:etunimi (z/xml1-> r (z/attr :first_name))
;;   :sukunimi (z/xml1-> r (z/attr :last_name))})
;;
;;(defn parse-date [d]
;;  (.parse (java.text.SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss.S") d))
;;
;;(defn parse-program [p]
;;  {:sampo-id (z/xml1-> p (z/attr :id))
;;   :alkupvm (z/xml1-> p (z/attr :schedule_start)
;;                      parse-date)
;;   :loppupvm (z/xml1-> p (z/attr :schedule_finish) parse-date)
;;   :nimi (z/xml1-> p (z/attr :name))})
;;
;;(defn parse-sampo [payload]
;;  (let [xml (xml-zip (parse (java.io.ByteArrayInputStream. (.getBytes payload "UTF-8"))))]
;;    {:resurssit (z/xml-> xml
;;                         :Resource
;;                         parse-resource)
;;     :hankkeet (z/xml-> xml :Program
;;                        parse-program)}))

  
