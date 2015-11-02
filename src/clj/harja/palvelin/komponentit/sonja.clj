(ns harja.palvelin.komponentit.sonja
  "Komponentti Sonja-väylän JMS-jonoihin liittymiseksi."
  (:require [com.stuartsierra.component :as component]
            [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [clojure.core.async :refer [go <! >! thread >!!]]
            [taoensso.timbre :as log]
            [hiccup.core :refer [html]]
            [clojure.string :as str])
  (:import (progress.message.jclient QueueConnectionFactory)
           (javax.jms Session)
           (org.apache.activemq ActiveMQConnectionFactory))
  (:use [slingshot.slingshot :only [try+ throw+]]))

(def agentin-alkutila
  {:yhteys nil :istunto nil :kuuntelijat [] :jonot {}})

(defprotocol LuoViesti
  (luo-viesti [x istunto]))

(extend-protocol LuoViesti
  String
  (luo-viesti [s istunto]
    (doto (.createTextMessage istunto)
      (.setText s))))

(defprotocol Sonja
  (kuuntele [this jonon-nimi kuuntelija-fn]
    "Lisää uuden kuuntelijan annetulle jonolle. Jos jonolla on monta kuuntelijaa, viestit välitetään jokaiselle kuuntelijalle.
Kuuntelijafunktiolle annetaan suoraan javax.jms.Message objekti. Kuuntelija blokkaa käsittelyn ajan, joten samasta jonosta voidaan lukea vain yksi viesti kerrallaan. Jos käsittelijä haluaa tehdä jotain pitkäaikaista, täytyy sen hoitaa se uudessa säikeessä.")

  (laheta [this jono viesti] [this jono viesti otsikot]
    "Lähettää viestin nimettyyn jonoon. Palauttaa message id:n."))

(defn- luo-connection-factory [url tyyppi]
  (if (= tyyppi :activemq)
    (ActiveMQConnectionFactory. url)
    (doto (QueueConnectionFactory. url)
      (.setFaultTolerant true)
      (.setFaultTolerantReconnectTimeout (int 30)))))

(defn- luo-jonon-kuuntelija
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
  (if-let [jono (get-in jonot [jonon-nimi :queue])]
    jono
    (let [q (.createQueue istunto jonon-nimi)]
      (assoc-in jonot [jonon-nimi :queue] q)
      q)))

(defn- varmista-producer
  "Varmistaa, että nimetylle jonolle on luotu producer viestien lähettämistä varten. Palauttaa producerin."
  [istunto jonot jonon-nimi]
  (if-let [producer (get-in jonot [jonon-nimi :producer])]
    producer
    (let [jono (varmista-jono istunto jonot jonon-nimi)
          producer (.createProducer istunto jono)]
      (assoc-in jonot [jonon-nimi :producer] producer)
      producer)))

(defn- yhdista [{:keys [url kayttaja salasana tyyppi]}]
  (log/info "Yhdistetään " (if (= tyyppi :activemq) "ActiveMQ" "Sonic") " JMS-brokeriin URL:lla:" url)
  (try
    (let [qcf (luo-connection-factory url tyyppi)
          yhteys (.createConnection qcf kayttaja salasana)]
      (.start yhteys)
      yhteys)
    (catch Exception e
      (log/error "JMS brokeriin yhdistäminen epäonnistui: " e)
      nil)))

(defn aloita-yhdistaminen [tila asetukset]
  (loop []
    (let [yhteys (yhdista asetukset)]
      (if yhteys
        (let [istunto (.createSession yhteys false Session/AUTO_ACKNOWLEDGE)]
          (assoc tila :yhteys yhteys :istunto istunto))
        (do
          (log/warn "Ei saatu yhteyttä Sonjan JMS-brokeriin. Yritetään uudestaan 10 minuutin päästä.")
          (Thread/sleep 600000)
          (recur))))))

(defn poista-kuuntelija [jonot jonon-nimi kuuntelija-fn]
  (update-in jonot [jonon-nimi :kuuntelijat] disj kuuntelija-fn))

(defn yhdista-kuuntelija [tila jonon-nimi kuuntelija-fn]
  ;; todo: selvitä miksi ei yhdistä
  (println "----> Yhdistetään kuuntelija jonoon:" jonon-nimi)
  (let [istunto (:istunto tila)
        jonot (:jonot @tila)
        jono (get jonot jonon-nimi)]
    (if (:consumer jono)
      (do
        (log/info "Lisätään kuuntelija jonoon " jonon-nimi)
        (update-in jonot [jonon-nimi :kuuntelijat] conj kuuntelija-fn))

      (do (log/info "Ensimmäinen kuuntelija jonolle " jonon-nimi ", luodaan consumer.")
          (let [consumer (luo-jonon-kuuntelija istunto jonon-nimi
                                               #(doseq [k (get-in jonot [jonon-nimi :kuuntelijat])]
                                                 (k %)))]
            (update-in jonot [jonon-nimi] assoc
                       :consumer consumer
                       :kuuntelijat #{kuuntelija-fn}))))
    #(poista-kuuntelija jonot jonon-nimi kuuntelija-fn)))

(defn laheta-viesti [istunto jonot jonon-nimi viesti correlation-id]
  (if istunto
    (try
      (let [producer (varmista-producer istunto jonot jonon-nimi)
            msg (luo-viesti viesti istunto)]
        (log/debug "Lähetetään JMS viesti ID:llä " (.getJMSMessageID msg))
        (when correlation-id
          (.setJMSCorrelationID msg correlation-id))
        (.send producer msg)
        (.getJMSMessageID msg))
      (catch Exception e
        (log/error e "Virhe JMS-viestin lähettämisessä jonoon: " jonon-nimi)))
    (throw+ {:type    :jms-yhteysvirhe
             :virheet [{:koodi  :ei-yhteytta
                        :viesti "Sonja yhteyttä ei saatu. Viestiä ei voida lähettää."}]})))

(defrecord SonjaYhteys [asetukset tila]
  component/Lifecycle
  (start [this]
    (let [this (assoc this :tila (agent agentin-alkutila))]
      (send-off (:tila this) aloita-yhdistaminen asetukset)
      this))

  (stop [{:keys [tila] :as this}]
    (when @(:istunto tila)
      (.close (:istunto @tila)))
    (when @(:yhteys tila)
      (.close (:yhteys @tila)))
    (assoc this
      :tila (restart-agent (:tila this) agentin-alkutila)))

  Sonja
  (kuuntele [this jonon-nimi kuuntelija-fn]
    ;; todo: selvitä miksi ei kutsuta yhdista-kuuntelija -funktiota
    (send (:tila this)
          (fn [tila]
            (yhdista-kuuntelija tila jonon-nimi kuuntelija-fn))))

  (laheta [{:keys [tila]} jonon-nimi viesti {:keys [correlation-id]}]
    (laheta-viesti (:istunto @tila) (:jonot @tila) jonon-nimi viesti correlation-id))

  (laheta [this jonon-nimi viesti]
    (laheta this jonon-nimi viesti nil)))

(defn luo-oikea-sonja [asetukset]
  (->SonjaYhteys asetukset nil))

(defn luo-feikki-sonja []
  (reify
    component/Lifecycle
    (start [this] this)
    (stop [this] this)

    Sonja
    (kuuntele [this jonon-nimi kuuntelija-fn]
      (log/debug "Feikki Sonja, aloita muka kuuntelu jonossa: " jonon-nimi)
      #(log/debug "Feikki Sonja, lopeta muka kuuntelu jonossa: " jonon-nimi))
    (laheta [this jonon-nimi viesti otsikot]
      (log/debug "Feikki Sonja, lähetä muka viesti jonoon: " jonon-nimi)
      (str "ID:" (System/currentTimeMillis)))
    (laheta [this jonon-nimi viesti]
      (laheta this jonon-nimi viesti nil))))

(defn luo-sonja [asetukset]
  (if (and asetukset (not (str/blank? (:url asetukset))))
    (luo-oikea-sonja asetukset)
    (luo-feikki-sonja)))