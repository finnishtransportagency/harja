(ns harja.palvelin.komponentit.sonja
  "Komponentti Sonja-väylän JMS-jonoihin liittymiseksi."
  (:require [com.stuartsierra.component :as component]
            [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [clojure.core.async :refer [go <! >! thread >!!] :as async]
            [taoensso.timbre :as log]
            [hiccup.core :refer [html]]
            [clojure.string :as str])
  (:import (javax.jms Session ExceptionListener)
           (java.lang.reflect Proxy InvocationHandler))
  (:use [slingshot.slingshot :only [try+ throw+]]))

(def agentin-alkutila
  {:yhteys nil :istunto nil :jonot {}})

(def ei-jms-yhteytta {:type :jms-yhteysvirhe
                      :virheet [{:koodi :ei-yhteytta
                                 :viesti "Sonja yhteyttä ei saatu. Viestiä ei voida lähettää."}]})
(defprotocol LuoViesti
  (luo-viesti [x istunto]))

(extend-protocol LuoViesti
  String
  (luo-viesti [s istunto]
    (doto (.createTextMessage istunto)
      (.setText s))))

(defprotocol Sonja
  (kuuntele [this jonon-nimi kuuntelija-fn]
    "Lisää uuden kuuntelijan annetulle jonolle. Jos jonolla on monta kuuntelijaa,
    viestit välitetään jokaiselle kuuntelijalle.
    Kuuntelijafunktiolle annetaan suoraan javax.jms.Message objekti.
    Kuuntelija blokkaa käsittelyn ajan, joten samasta jonosta voidaan lukea vain yksi viesti kerrallaan.
    Jos käsittelijä haluaa tehdä jotain pitkäaikaista, täytyy sen hoitaa se uudessa säikeessä.")

  (laheta [this jono viesti] [this jono viesti otsikot]
    "Lähettää viestin nimettyyn jonoon. Palauttaa message id:n."))

(def jms-driver-luokka {:activemq "org.apache.activemq.ActiveMQConnectionFactory"
                        :sonicmq "progress.message.jclient.QueueConnectionFactory"})

(declare aloita-yhdistaminen
         yhdista-kuuntelija
         tee-jms-poikkeuskuuntelija)

(defn- poista-consumerit [{jonot :jonot :as tila}]
  (reduce (fn [tila jono]
            (assoc-in tila [:jonot jono :consumer] nil))
          tila
          (keys jonot)))

(defn yhdista-uudelleen [{:keys [yhteys jonot] :as tila} agentti asetukset yhteys-ok?]
  (log/info "Yritetään yhdistään JMS-yhteys uudelleen")
  (when yhteys
    (try
      (.close yhteys)
      (catch Exception e
        (log/error e "JMS-yhteyden sulkemisessa tapahtui poikkeus: " (.getMessage e)))))
  (loop [tila (aloita-yhdistaminen (poista-consumerit tila) asetukset (tee-jms-poikkeuskuuntelija agentti asetukset yhteys-ok?) yhteys-ok?)
         [[jonon-nimi kuuntelija] & kuuntelijat]
         (mapcat (fn [[jonon-nimi {kuuntelijat :kuuntelijat}]]
                   (log/info (format "Yhdistetään uudestaan kuuntelijat jonoon: %s" jonon-nimi))
                   (map (fn [k] [jonon-nimi k]) @kuuntelijat)) jonot)]
    (if (not jonon-nimi)
      tila
      (recur (yhdista-kuuntelija tila jonon-nimi kuuntelija)
             kuuntelijat))))

(defn tee-sonic-jms-tilamuutoskuuntelija []
  (let [lokita-tila #(case %
                      0 (log/info "Sonja JMS yhteyden tila: ACTIVE")
                      1 (log/info "Sonja JMS yhteyden tila: RECONNECTING")
                      2 (log/error "Sonja JMS yhteyden tila: FAILED")
                      3 (log/info "Sonja JMS yhteyden tila: CLOSED"))
        kasittelija (reify InvocationHandler (invoke [_ _ _ args] (lokita-tila (first args))))
        luokka (Class/forName "progress.message.jclient.ConnectionStateChangeListener")
        instanssi (Proxy/newProxyInstance (.getClassLoader luokka) (into-array Class [luokka]) kasittelija)]
    instanssi))

(defn tee-jms-poikkeuskuuntelija [agentti asetukset yhteys-ok?]
  (reify ExceptionListener
    (onException [_ e]
      (log/error e (str "Tapahtui JMS-poikkeus: " (.getMessage e)))
      (send-off agentti yhdista-uudelleen agentti asetukset yhteys-ok?))))

(defn konfiguroi-sonic-jms-connection-factory [connection-factory]
  (doto connection-factory
    (.setFaultTolerant true)
    (.setFaultTolerantReconnectTimeout (int 300))))

(defn- luo-connection-factory [url tyyppi]
  (let [connection-factory (-> tyyppi
                               jms-driver-luokka
                               Class/forName
                               (.getConstructor (into-array Class [String]))
                               (.newInstance (into-array Object [url])))]
    (if (= tyyppi :activemq)
      connection-factory
      (konfiguroi-sonic-jms-connection-factory connection-factory))))

(defn- viestin-kasittelija [kasittelija]
  (let [ch (async/chan)]
    (go (loop [viesti (<! ch)]
          (when viesti
            (kasittelija viesti)
            (recur (<! ch)))))
    ch))

(defn- yhdista [{:keys [url kayttaja salasana tyyppi]} poikkeuskuuntelija]
  (log/info "Yhdistetään " (if (= tyyppi :activemq) "ActiveMQ" "Sonic") " JMS-brokeriin URL:lla:" url)
  (try
    (let [qcf (luo-connection-factory url tyyppi)
          yhteys (.createConnection qcf kayttaja salasana)]
      (when (= tyyppi :sonicmq)
        (.setConnectionStateChangeListener yhteys (tee-sonic-jms-tilamuutoskuuntelija)))
      (.setExceptionListener yhteys poikkeuskuuntelija)
      (.start yhteys)
      yhteys)
    (catch Exception e
      (log/error "JMS brokeriin yhdistäminen epäonnistui: " e)
      nil)))

(defn aloita-yhdistaminen [tila asetukset poikkeuskuuntelija yhteys-ok?]
  (loop [aika 10000]
    (let [yhteys (yhdista asetukset poikkeuskuuntelija)]
      (if yhteys
        (let [istunto (.createSession yhteys false Session/AUTO_ACKNOWLEDGE)]
          (log/debug "Saatiin yhteys Sonjan JMS-brokeriin.")
          (reset! yhteys-ok? true)
          (assoc tila :yhteys yhteys :istunto istunto))
        (do
          (log/warn (format "Ei saatu yhteyttä Sonjan JMS-brokeriin. Yritetään uudestaan %s millisekunnin päästä." aika))
          (Thread/sleep aika)
          (recur (min (* 2 aika) 600000)))))))

(defn- luo-jonon-kuuntelija
  "Luo jonon kuuntelijan annetulle istunnolle."
  [istunto jonon-nimi kasittelija]
  (let [jono (.createQueue istunto jonon-nimi)
        consumer (.createConsumer istunto jono)
        viesti-ch (viestin-kasittelija kasittelija)]
    (log/debug (format "Luodaan jono kuuntelija jonoon: %s" jonon-nimi))
    (thread
      (try
        (loop [viesti (.receive consumer)]
          (if-not viesti
            (log/info (format "JMS jonon: %s consumer suljettu. Lopetetaan kuuntelu." jonon-nimi))
            (do
              (log/debug "Vastaanotettu viesti Sonja jonosta: " jonon-nimi)
              (try
                (>!! viesti-ch viesti)
                (catch Exception e
                  (log/warn e (str "Viestin käsittelijä heitti poikkeuksen, jono: " jonon-nimi))))
              (recur (.receive consumer)))))
        (catch Exception e
          (log/warn e (str "Virhe Sonja kuuntelijassa, jono: " jonon-nimi)))))
    consumer))

(defn- varmista-jono
  "Varmistaa, että nimetylle jonolle on luotu Queue instanssi. Palauttaa jonon."
  [istunto jonot jonon-nimi]
  (if-let [jono (get-in jonot [jonon-nimi :queue])]
    jono
    (let [q (.createQueue istunto jonon-nimi)]
      ;; todo: Näyttää epäilyttävälle. Paluuarvoa ei käytetä mihinkään.
      (assoc-in jonot [jonon-nimi :queue] q)
      q)))

(defn- varmista-producer
  "Varmistaa, että nimetylle jonolle on luotu producer viestien lähettämistä varten. Palauttaa producerin."
  [istunto jonot jonon-nimi]
  (if-let [producer (get-in jonot [jonon-nimi :producer])]
    producer
    (let [jono (varmista-jono istunto jonot jonon-nimi)
          producer (.createProducer istunto jono)]
      ;; todo: Näyttää epäilyttävälle. Paluuarvoa ei käytetä mihinkään.
      (assoc-in jonot [jonon-nimi :producer] producer)
      producer)))

(defn poista-kuuntelija [tila jonon-nimi kuuntelija-fn]
  (update-in tila [:jonot jonon-nimi] (fn [{:keys [consumer kuuntelijat] :as jono}]
                                        (swap! kuuntelijat disj kuuntelija-fn)
                                        (if (empty? @kuuntelijat)
                                          (do
                                            (.close consumer)
                                            nil)
                                          jono))))

(defn yhdista-kuuntelija [{:keys [istunto] :as tila} jonon-nimi kuuntelija-fn]
  (log/debug (format "Yhdistetään kuuntelija jonoon: %s. Tila: %s." jonon-nimi tila))
  (update-in tila [:jonot jonon-nimi]
             (fn [{:keys [consumer kuuntelijat] :as jonon-tiedot}]
               (let [kuuntelijat (or kuuntelijat (atom #{}))]
                 (swap! kuuntelijat conj kuuntelija-fn)
                 (assoc jonon-tiedot
                   :consumer (or consumer
                                 (luo-jonon-kuuntelija istunto jonon-nimi
                                                       #(doseq [kuuntelija @kuuntelijat]
                                                         (log/debug (format "Vastaanotettiin viesti jonosta: %s." jonon-nimi))
                                                         (kuuntelija %))))
                   :kuuntelijat kuuntelijat)))))

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
    (throw+ ei-jms-yhteytta)))

(defrecord SonjaYhteys [asetukset tila yhteys-ok?]
  component/Lifecycle
  (start [this]
    (let [agentti (agent agentin-alkutila)
          yhteys-ok? (atom false)
          poikkeus-kuuntelija (tee-jms-poikkeuskuuntelija agentti asetukset yhteys-ok?)]
      (send-off agentti aloita-yhdistaminen asetukset poikkeus-kuuntelija yhteys-ok?)
      (assoc this
        :tila agentti
        :yhteys-ok? yhteys-ok?)))

  (stop [this]
    (when @yhteys-ok?
      (let [tila @tila]
        (some-> tila :istunto .close)
        (some-> tila :yhteys .close)))
    (assoc this
      :tila nil))

  Sonja
  (kuuntele [this jonon-nimi kuuntelija-fn]
    (assert (some? jonon-nimi) "Ei voida yhdistää tyhjään jonoon")
    (log/debug (format "Aloitetaan JMS-jonon kuuntelu: %s" jonon-nimi))
    (send tila
          (fn [tila]
            (yhdista-kuuntelija tila jonon-nimi kuuntelija-fn)))
    #(send tila poista-kuuntelija jonon-nimi kuuntelija-fn))

  (laheta [this jonon-nimi viesti {:keys [correlation-id]}]
    (if-not @yhteys-ok?
      (throw+ ei-jms-yhteytta)
      (let [tila @tila]
        (laheta-viesti (:istunto tila) (:jonot tila) jonon-nimi viesti correlation-id))))

  (laheta [this jonon-nimi viesti]
    (laheta this jonon-nimi viesti nil)))

(defn luo-oikea-sonja [asetukset]
  (->SonjaYhteys asetukset nil nil))

(defn luo-feikki-sonja []
  (reify
    component/Lifecycle
    (start [this] this)
    (stop [this] this)

    Sonja
    (kuuntele [this jonon-nimi kuuntelija-fn]
      (log/debug "Feikki Sonja, aloita muka kuuntelu jonossa: " jonon-nimi))
    (laheta [this jonon-nimi viesti otsikot]
      (log/debug "Feikki Sonja, lähetä muka viesti jonoon: " jonon-nimi)
      (str "ID:" (System/currentTimeMillis)))
    (laheta [this jonon-nimi viesti]
      (laheta this jonon-nimi viesti nil))))

(defn luo-sonja [asetukset]
  (if (and asetukset (not (str/blank? (:url asetukset))))
    (luo-oikea-sonja asetukset)
    (luo-feikki-sonja)))
