(ns harja.palvelin.integraatiot.jms-clientit.sonic
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
           (clojure.core.async.impl.channels ManyToManyChannel)))

(defmacro esta-class-not-found-virheet [body]
  `(try
     (eval ~body)
     (catch Throwable ~'t
       (taoensso.timbre/error "JMS eval EPÄONNISTUI: " (.getMessage ~'t) "\nStackTrace: " (.getStackTrace ~'t)))))

(defn konfiguroi-sonic-jms-connection-factory [connection-factory]
  (doto connection-factory
    ;; Fault tolerant –asetuksen pois kytkeminen puolestaan johtuu siitä, että jos se on käytössä, client vastaanottaa JMS-”palvelimelta” tiedon siitä,
    ;; mitä osoitteita ja portteja on käytössä samassa klusterissa – ja ainakin aiemmin Sonja oli konfiguroitu siten, että se antoi täsmälleen yhden osoitteen,
    ;; johon yhteyden katketessa pantiin sitten hanskat tiskiin.
    ;; Pyritään saamaan yhteyden uudelleenmuodostuminen käyttöön asettamalla faultTolerant trueksi. Vaatinee myös
    ;; setConnectionURLs asettamista, joka toistaiseksi tekemättä
    (.setFaultTolerant true)
    ;; Pingillä pyritään pitämään hengissä olemassa olevaa yhteyttä, vaikka siellä ei varsinaisesti liikettä olisikaan.
    (.setPingInterval (int 30))
    ;; Yrittää reconnectata loputtomiin. Pitää wrapata intiin, jotta tyypiksi tulee Integer, eikä Float
    (.setFaultTolerantReconnectTimeout (int 0))
    ;; Configuroidaan niin, että lähetykset tapahtuu asyncisti. Tämä on ok, sillä vastauksia jäädään muutenkin
    ;; odottamaan eri jonoon. Asyncisti sen takia, että JMS-säije ei blokkaa lähetykseen. Mahdolliset virheet
    ;; otetaan kiinni sitten yhteysolion setRefectionListener:issä
    (.setAsynchronousDeliveryMode (esta-class-not-found-virheet '(. progress.message.jclient.Constants ASYNC_DELIVERY_MODE_ENABLED)))))

(defn- luo-connection-factory [url]
  (let [connection-factory (-> "progress.message.jclient.QueueConnectionFactory"
                               Class/forName
                               (.getConstructor (into-array Class [String]))
                               (.newInstance (into-array Object [url])))]
    (konfiguroi-sonic-jms-connection-factory connection-factory)))

(defn tee-sonic-jms-tilamuutoskuuntelija [jms-connection-tila]
  (let [lokita-tila #(case %
                       0 (do (log/info "JMS yhteyden tila: ACTIVE")
                             (async/put! jms-connection-tila "ACTIVE"))
                       1 (do (log/info "JMS yhteyden tila: RECONNECTING")
                             (async/put! jms-connection-tila "RECONNECTING"))
                       2 (do (log/error "JMS yhteyden tila: FAILED")
                             (async/put! jms-connection-tila "FAILED"))
                       3 (do (log/info "JMS yhteyden tila: CLOSED")
                             (async/put! jms-connection-tila "CLOSED")))
        kasittelija (reify InvocationHandler (invoke [_ _ _ args] (lokita-tila (first args))))
        luokka (Class/forName "progress.message.jclient.ConnectionStateChangeListener")]
    (Proxy/newProxyInstance (.getClassLoader luokka) (into-array Class [luokka]) kasittelija)))

(defn- yhdista [{:keys [kayttaja salasana] :as asetukset} qcf aika jms-connection-tila]
  (try
    (let [yhteys (.createQueueConnection qcf kayttaja salasana)]
      (doto yhteys
        (.setConnectionStateChangeListener (tee-sonic-jms-tilamuutoskuuntelija jms-connection-tila))
        ;; Otetaan kiinni epäonnistuneet lähetykset
        (.setRejectionListener (esta-class-not-found-virheet '(reify progress.message.jclient.RejectionListener
                                                                (onRejectedMessage [this msg e]
                                                                  (try
                                                                    (taoensso.timbre/error
                                                                      (str "Harjasta on lähetetty viesti JMSn kautta jonnekkin, mutta"
                                                                           " sitä viestiä ei saatu vastaanottopäässä käsiteltyä."))
                                                                    (taoensso.timbre/error
                                                                      (str "JMS:lta tullut virhe msg: " msg
                                                                           " Virhekoodi: " (.getErrorCode e)))
                                                                    ;; Halutaan ottaa kaikki virheet kiinni, sillä yksikin käsittelemätön virhe
                                                                    ;; eaiheuttaa muiden viestien käsittelyn.

                                                                    ;; Älä tee mitään aikaa vievää täällä. Muuten yhteyttä tai sessiota ei saada välttämättä kiinni.
                                                                    (catch Throwable t
                                                                      (taoensso.timbre/error (str "Epäonnistuneen viestin käsittely epäonnistui: "
                                                                                                  (.getMessage t) "\nStackTrace: "))
                                                                      (.printStackTrace t))))))))
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
  (log/info "Yhdistetään Sonic JMS-brokeriin URL:lla:" (:url asetukset))
  (future
    (loop []
      (let [qcf (luo-connection-factory (:url asetukset))
            yhteys (yhdista asetukset qcf 10000 jms-connection-tila)]
        ;; Jos yhteys olio ollaan saatu luotua, mutta se on kiinni tilassa (brokerin päässä saattaa olla jotain
        ;; vikaa), niin yritetään luoda yhteyttä vielä uudestaan.
        (if (= (.getConnectionState yhteys) 0)
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


(defrecord Sonic [nimi asetukset]
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
