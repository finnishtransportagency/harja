(ns harja.palvelin.integraatiot.jms-clientit.apache-classic
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clojure.core.async :as async]
            [harja.palvelin.tyokalut.komponentti-protokollat :as kp]
            [harja.palvelin.integraatiot.jms :as jms])
  (:import (clojure.lang PersistentArrayMap)
           (javax.jms Session ExceptionListener JMSException MessageListener TextMessage)
           (java.util Date)
           (java.lang.reflect Proxy InvocationHandler)
           (java.net InetAddress)
           (java.util.concurrent TimeoutException)
           (clojure.core.async.impl.channels ManyToManyChannel)
           (org.apache.activemq ActiveMQConnection ActiveMQConnectionFactory)))

;;  Huomioi, että sovelluksessa käytetää Apache ActiveMQ Classic clienttiä, ja yhteys otetaan ActiveMQ Artemis brokeriin.
;;  Artemis broker tukee JMS protokollaa, kuten myös Classic client.
;;  Mutta, client-versiota päivittäessä (project.clj tiedostossa) on hyvä tarkistaa, että client ja broker versiot ovat yhteensopivia
;;  JMS protokollan osalta.
;;  ActiveMQ Classic Client: https://activemq.apache.org/components/classic/documentation/jms2
;;  ActiveMQ Artemis Broker: https://activemq.apache.org/components/artemis/documentation/latest/protocols-interoperability.html#openwire

(defn konfiguroi-activemq-jms-connection-factory
  "Konfiguroidaan ActiveMQ JMS-yhteyden asetukset."
  [connection-factory url]
  (doto connection-factory
    ;; https://activemq.apache.org/components/classic/documentation/failover-transport-reference
    ;; initialReconnectDelay: Aika millisekunteina, jonka jälkeen ensimmäinen reconnect tehdään.
    ;; maxReconnectAttempts: Kuinka monta kertaa yritetään reconnectia. -1 = loputtomiin.
    ;; TODO: (Tutki optio) trackMessages: Pitää cachea in-flight viesteistä, jotka flushataan brokerille reconnectin jälkeen. Default: false
    ;; TODO: (Tutki optio) warnAfterReconnectAttempts: Kuinka monta reconnectia tehdään ennen kuin lokitetaan varoitus. Default: 10
    (.setBrokerURL (str "failover:(" url ")?initialReconnectDelay=100&maxReconnectAttempts=-1"))

    ;; https://activemq.apache.org/components/classic/documentation/async-sends
    ;; TODO: Selvitä tarviiko edes asettaa, koska defaulttina ActiveMQ lähettää valtaosan viesteistä asynkronisesti.
    ;;       Sitaatti dokumentaatiosta:
    ;;       "The good news is that ActiveMQ Classic sends message in async mode by default in several cases.
    ;;        It is only in cases where the JMS specification required the use of sync sending that we default to sync sending.
    ;;        The cases that we are forced to send in sync mode are when persistent messages are being sent outside of a transaction."

    ;; Konfiguroidaan lähetykset tapahtumaan asynkronisesti, kuten Sonic-clientissä.
    ;; Tämä on OK, koska vastauksia odotetaan muutenkin eri jonossa.
    ;; Asynkronisesti siksi, että saadaan parempi suorituskyky, eikä blokata JMS-säiettä.
    ;; Mahdollisten virheiden kiinniotto on vielä kysymysmerkki activemq:n osalta, Sonicissa
    ;; se tapahtuu RejectionListener:in kautta.
    ;;(.setUseAsyncSend true)
    ))

(defn- ^ActiveMQConnectionFactory luo-connection-factory [url]
  (let [connection-factory ^ActiveMQConnectionFactory
                           (-> "org.apache.activemq.ActiveMQConnectionFactory"
                             Class/forName
                             (.getConstructor (into-array Class [String]))
                             (.newInstance (into-array Object [url])))]
    (konfiguroi-activemq-jms-connection-factory connection-factory url)))

(defn tee-activemq-jms-tilanmuutoskuuntelija [jms-connection-tila]
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
        (.addTransportListener (tee-activemq-jms-tilanmuutoskuuntelija jms-connection-tila)))

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
            yhteys (yhdista asetukset qcf 10000 jms-connection-tila)]
        ;; Jos yhteys olio ollaan saatu luotua, mutta se on kiinni tilassa (brokerin päässä saattaa olla jotain
        ;; vikaa), niin yritetään luoda yhteyttä vielä uudestaan.
        (if (not (or (.isClosed yhteys) (.isClosing yhteys)))
          (do
            (log/info "Yhteyden metadata: " (when-let [meta-data (.getMetaData yhteys)]
                                              (.getJMSProviderName meta-data)))
            (log/info (str "Luotiin yhteysobjekti " nimi " JMS-brokeriin."))
            {:yhteys yhteys :qcf qcf})
          (do
            (log/error (str "Jokin meni vikaan, kun yritettiin saada yhteys " nimi "... Yritetään yhdistää uudelleen."))
            ;; Yhteys objekti kummiskin pitää sammuttaa, jotta ylimääräiset säikeet sammutetaan ja muistia vapautetaan
            (try (.close yhteys)
                 (catch Throwable t
                   (log/debug "EI saatu sulettua epäonnistunutta yhteyttä")))
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


(defrecord ApacheClassic [nimi asetukset]
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
