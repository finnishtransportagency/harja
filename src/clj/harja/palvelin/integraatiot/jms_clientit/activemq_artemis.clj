(ns harja.palvelin.integraatiot.jms-clientit.activemq-artemis
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clojure.core.async :as async]
            [harja.palvelin.tyokalut.komponentti-protokollat :as kp]
            [harja.palvelin.integraatiot.jms :as jms])
  (:import (javax.jms QueueConnection Session ExceptionListener JMSException MessageListener TextMessage)
           (org.apache.activemq.artemis.api.core.client FailoverEventListener FailoverEventType)
           (org.apache.activemq.artemis.jms.client ActiveMQConnection ActiveMQSession ActiveMQConnectionFactory)))

(defn konfiguroi-activemq-jms-connection-factory
  "Konfiguroidaan ActiveMQ Artemis JMS-yhteyden asetukset."
  [connection-factory url]
  (doto ^ActiveMQConnectionFactory connection-factory
    (.setBrokerURL (str url))))

#_(defn- ^ActiveMQConnectionFactory luo-connection-factory [url]
    (let [connection-factory ^ActiveMQConnectionFactory
                             (-> "org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory"
                               Class/forName
                               (.getConstructor (into-array Class [String]))
                               (.newInstance (into-array Object [url])))]
      (konfiguroi-activemq-jms-connection-factory connection-factory url)))

(defn- ^ActiveMQConnectionFactory luo-connection-factory [url]
  (let [connection-factory (ActiveMQConnectionFactory.)]
    (konfiguroi-activemq-jms-connection-factory connection-factory url)))


;; TODO: Selvitä, miten Artemis-clientin automaattinen yhteyskatkoksesta toipuminen saadaan sovitettua
;;       Harja-sovelluksen nykyiseen toimintalogiikkaan. (failover- ja exception-kuuntelijat)
;;       Yhteydestä toipuminen pitää saada vakaaksi ja testattua huolellisesti.
(defn tee-jms-failover-kuuntelija [jms-connection-tila]
  (reify FailoverEventListener
    (failoverEvent [this evt]
      (println evt)
      (cond
        (= evt FailoverEventType/FAILURE_DETECTED) (async/put! jms-connection-tila "RECONNECTING")
        (= evt FailoverEventType/FAILOVER_COMPLETED) (async/put! jms-connection-tila "ACTIVE")
        (= evt FailoverEventType/FAILOVER_FAILED) (async/put! jms-connection-tila "CLOSED")))))

;; https://activemq.apache.org/components/artemis/documentation/2.32.0/ha.html#getting-notified-of-connection-failure
(defn tee-jms-exception-kuuntelija [jms-connection-tila]
  (reify ExceptionListener
    (^void onException [this ^JMSException e]
      (println e)
      (cond
        (= (.getErrorCode e) "FAILOVER") (async/put! jms-connection-tila "ACTIVE")
        (= (.getErrorCode e) "DISCONNECT") (async/put! jms-connection-tila "CLOSED"))
      nil)))

;; TODO: Poista: Vanhan ActiveMQ Classic clientin failover-kuuntelija
#_(defn tee-activemq-jms-tilanmuutoskuuntelija [jms-connection-tila]
    (reify org.apache.activemq.transport.TransportListener
      (onCommand [this komento]
        ;; Ei tehdä tässä mitään, mutta kaikki interfacen metodit on implementoitava.
        )
      (onException [this e] (async/put! jms-connection-tila "FAILED"))
      (transportInterupted [this] (async/put! jms-connection-tila "RECONNECTING"))
      (transportResumed [this] (async/put! jms-connection-tila "ACTIVE"))))

(defn- yhdista [{:keys [kayttaja salasana] :as asetukset} ^ActiveMQConnectionFactory qcf aika jms-connection-tila]
  (try
    (let [yhteys ^ActiveMQConnection (.createQueueConnection qcf kayttaja salasana)]
      (doto yhteys
        (.setExceptionListener (tee-jms-exception-kuuntelija jms-connection-tila))
        (.setFailoverListener (tee-jms-failover-kuuntelija jms-connection-tila)))

      yhteys)

    (catch JMSException e
      (log/warn (format "Ei saatu yhteyttä JMS-brokeriin. Yritetään uudestaan %s millisekunnin päästä. " aika) e)
      (Thread/sleep aika)
      (yhdista asetukset qcf (min (* 2 aika) 600000) jms-connection-tila))
    (catch Throwable t
      (log/error "JMS brokeriin yhdistäminen epäonnistui: " (.getMessage t) "\nStackTrace: ")
      (.printStackTrace t)
      (Thread/sleep aika)
      (yhdista asetukset qcf (min (* 2 aika) 600000) jms-connection-tila))))

(defn yhdista! [{:keys [nimi asetukset]} jms-connection-tila]
  (log/info "Yhdistetään ActiveMQ Artemis JMS-brokeriin URL:lla:" (:url asetukset))
  (future
    (loop []
      (let [qcf (luo-connection-factory (:url asetukset))
            yhteys ^ActiveMQConnection (yhdista asetukset qcf 10000 jms-connection-tila)]
        ;; Jos yhteysolio on saatu luotua, mutta se on kiinni tilassa (brokerin päässä saattaa olla jotain
        ;; vikaa), yritetään luoda yhteyttä vielä uudestaan.
        ;; TODO: Tarkista, että yhteys ei ole closed tilassa, jos se on closed, niin yritä uudelleen.
        ;;       Artemis-clientissä ei näy olevan vastaavaa apumetodia tai attribuuttia kuin mitä käytetty ActiveMQ Classic clientissä.
        (if true
          (do
            (log/info "Yhteyden metadata: " (when-let [meta-data (.getMetaData yhteys)]
                                              (.getJMSProviderName meta-data)))
            (log/info (str "Luotiin yhteysobjekti " nimi " JMS-brokeriin."))
            {:yhteys yhteys :qcf qcf})
          (do
            (log/error (str "Jokin meni vikaan, kun yritettiin saada yhteys " nimi "... Yritetään yhdistää uudelleen."))
            ;; Yhteys pitää sammuttaa, jotta ylimääräiset säikeet sammutetaan ja muistia vapautetaan
            (try (.close yhteys)
                 (catch Throwable t
                   (log/debug "EI saatu suljettua epäonnistunutta yhteyttä")))
            (async/<!! (async/timeout 10000))
            (recur)))))))

(defn- sammuta-yhteys! [this jms-connection-tila]
  (future (try (let [{:keys [tila yhteys-aloitettu?]} this
                     {:keys [yhteys]} @tila]
                 (log/debug "Lopetetaan yhteys")
                 (let [lopeta-yhteys (future (.close yhteys))
                       yhteyden-lopetus-arvo (deref lopeta-yhteys (* 1000 30) ::timeout)]
                   (when (= yhteyden-lopetus-arvo ::timeout)
                     (future-cancel lopeta-yhteys)
                     (log/error "Yhteyttä ei saatu lopetettua oikein")))
                 (async/put! jms-connection-tila "CLOSED")
                 (reset! yhteys-aloitettu? false)
                 true)
               (catch Exception e
                 (log/error "VIRHE TAPAHTUI :lopeta-yhteys " (.getMessage e) "\nStackTrace: ")
                 (.printStackTrace e)
                 {:virhe e}))))


(defrecord ActiveMQArtemis [nimi asetukset]
  component/Lifecycle
  (start [this]
    (jms/oletus-start this))
  (stop [this]
    (jms/oletus-stop this))
  jms/JMS
  (kuuntele! [this jonon-nimi kuuntelija-fn jarjestelma]
    (jms/oletus-kuuntele this jonon-nimi kuuntelija-fn jarjestelma))
  (kuuntele! [this jonon-nimi kuuntelija-fn]
    (jms/oletus-kuuntele this jonon-nimi kuuntelija-fn))

  (laheta [this jonon-nimi viesti otsikot jarjestelma]
    (jms/oletus-laheta this jonon-nimi viesti otsikot jarjestelma))
  (laheta [this jonon-nimi viesti otsikot]
    (jms/oletus-laheta this jonon-nimi viesti otsikot))
  (laheta [this jonon-nimi viesti]
    (jms/oletus-laheta this jonon-nimi viesti))
  (sammuta-lahettaja [this jonon-nimi jarjestelma]
    (jms/sammuta-lahettaja this jonon-nimi jarjestelma))
  (sammuta-lahettaja [this jonon-nimi]
    (jms/sammuta-lahettaja this jonon-nimi))
  (kasky [this kaskyn-tiedot]
    (jms/oletus-kasky this kaskyn-tiedot))
  jms/JMSClientYhdista
  (-yhdista! [this jms-connection-tila]
    (yhdista! this jms-connection-tila))
  (-sammuta-yhteys! [this jms-connection-tila]
    (sammuta-yhteys! this jms-connection-tila))
  kp/IStatus
  (-status [this]
    (jms/oletus-status this)))
