(ns harja.palvelin.integraatiot.jms-clientit.apache-artemis
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clojure.core.async :as async]
            [harja.palvelin.tyokalut.komponentti-protokollat :as kp]
            [harja.palvelin.integraatiot.jms :as jms])
  (:import (clojure.lang PersistentArrayMap)
           (javax.jms Session ExceptionListener JMSException MessageListener TextMessage)
           (org.apache.activemq.artemis.api.core.client FailoverEventListener FailoverEventType)
           (org.apache.activemq.artemis.jms.client ActiveMQJMSConnectionFactory)
           (clojure.core.async.impl.channels ManyToManyChannel)))

(defn konfiguroi-activemq-jms-artemis-connection-factory [connection-factory url]
  (doto connection-factory
    (.setInitialConnectAttempts 5)
    (.setReconnectAttempts -1)
    (.setRetryIntervalMultiplier (double 2))
    (.setMaxRetryInterval 300000)
    (.setCallFailoverTimeout 60000)
    (.setCallTimeout 6000)))

(defn- luo-connection-factory [url kayttaja salasana]
  (let [connection-factory (ActiveMQJMSConnectionFactory. url kayttaja salasana)]
    (konfiguroi-activemq-jms-artemis-connection-factory connection-factory url)))

(defn- yhdista [asetukset qcf aika jms-connection-tila]
  (try
    (let [yhteys (.createQueueConnection qcf )]
      (doto yhteys
        (.setExceptionListener (reify ExceptionListener
                                 (onException [this e]
                                   (log/error "JMS yhteydessä oli virhe. " (.getMessage e) "\n stackTrace: " (.getStackTrace e))
                                   (async/put! jms-connection-tila "FAILED"))))
        (.setFailoverListener (reify FailoverEventListener
                                (failoverEvent [this eventType]
                                  (log/warn "JMS yhteydestä saatiin failoverEvent:" eventType)

                                  (case (str eventType)
                                    "FAILOVER_COMPLETED" (async/put! jms-connection-tila "ACTIVE")
                                    "FAILOVER_FAILED" (async/put! jms-connection-tila "FAILED")
                                    "FAILURE_DETECTED" (async/put! jms-connection-tila "RECONNECTING"))))))
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

(defn- yhdista! [{:keys [nimi asetukset]} jms-connection-tila]
  (log/info "Yhdistetään ActiveMQ Artemis JMS-brokeriin URL:lla:" (:url asetukset))
  (future
    (let [qcf (luo-connection-factory (:url asetukset) (:kayttaja asetukset) (:salasana asetukset))
          yhteys (yhdista asetukset qcf 10000 jms-connection-tila)]
      ;; En hoksannut mitään järkevää testiä sille, että eihän yhteyttä ole sammutettu.
      ;; isStarted ei kelepaa, kun ei se aluksi olekkaan aloitettu
      {:yhteys yhteys :qcf qcf})))

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


(defrecord ApacheArtemis [nimi asetukset]
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