(ns harja.palvelin.komponentit.tapahtumat
  "Klusteritason tapahtumien kuuntelu

  Tätä käytetään mm TLOIK-komponentissa, Sonja-yhteysvarmistuksessa, ja ilmoitukset-APIssa.
  Ilmoitusten suhteen on olemassa urakkakohtaisia notifikaatiojonoja, joiden kautta voidaan seurata
  ja ilmoittaa urakkakohtaisista ilmoitukset-tapahtumista"

  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :refer [thread]]
            [taoensso.timbre :as log])
  (:import [com.mchange.v2.c3p0 C3P0ProxyConnection]
           [org.postgresql PGNotification]
           [org.postgresql.util PSQLException]))



(defn- aseta-ps-parametrit [ps parametrit]
  (loop [i 1
         [p & parametrit] parametrit]
    (when p
      (.setString ps i p)
      (recur (inc i) parametrit))))

(defn- u [c sql & parametrit]
  (with-open [ps (.prepareStatement c sql)]
    (aseta-ps-parametrit ps parametrit)
    (.executeUpdate ps)))

(defn- q [c sql & parametrit]
  (with-open [ps (.prepareStatement c sql)]
    (aseta-ps-parametrit ps parametrit)
    (.next (.executeQuery ps))))


(def get-notifications (->> (Class/forName "org.postgresql.jdbc.PgConnection")
                            .getMethods
                            (filter #(and (= (.getName %) "getNotifications")
                                          (= 0 (count (.getParameters %)))))
                            first))

(defn- kanava-nimi [kw]
  (-> kw
      name
      (.replace "-" "_")
      (.replace "!" "")
      (.replace "?" "")
      (.replace "<" "")
      (.replace ">" "")))

(defn- uusi-tietokantayhteys! [db ajossa connection kuuntelijat]
  (reset! ajossa false)
  (log/info "Uudelleenalustetaan tietokannan kuunteluyhteys")
  (try
    (log/debug "yritetään sulkea vanha tk-yhteys")
    (.close @connection)
    (catch Exception e
      (log/debug "vanhan tk-yhteyden sulku:" e)))
  (reset! connection (.getConnection (:datasource db)))
  (log/debug "saatiin uusi tk-yhteys")
  (doseq [kanava (keys @kuuntelijat)]
    (log/debug "sanotaan LISTEN kanavalle" kanava)
    (u @connection (str "LISTEN " kanava ";")))
  (log/debug "palautetaan ajossa -> true")
  (reset! ajossa true))

(defprotocol Kuuntele
  (kuuntele! [this kanava callback]))

(defprotocol Julkaise
  (julkaise! [this kanava payload]))

(defprotocol Kuuroudu
  (kuuroudu! [this kanava]))

(defrecord Tapahtumat [connection kuuntelijat ajossa]
  component/Lifecycle
  (start [this]
    (log/info "Tapahtumat-komponentti käynnistyy")
    (reset! kuuntelijat {})
    (reset! ajossa true)
    (reset! connection (.getConnection (:datasource (:db this))))
    ;; kuuntelijat-mapin avaimina on notifikaatiojonon id, esim "sonjaping" tai "urakan_123_tapahtumat".
    ;; arvona kullakin avaimella on seq async-kanavia (?)
    (thread (loop []
              (when @ajossa
                (try
                  (with-open [stmt (.createStatement @connection)
                              rs (.executeQuery stmt "SELECT 1")]
                    (doseq [^PGNotification notification (seq (.rawConnectionOperation @connection
                                                                                       get-notifications
                                                                                       C3P0ProxyConnection/RAW_CONNECTION
                                                                                       (into-array Object [])))]
                      (log/info "TAPAHTUI" (.getName notification) " => " (.getParameter notification))
                      (log/debug "kuuntelijat:" @kuuntelijat)
                      (doseq [kasittelija (get @kuuntelijat (.getName notification))]
                        ;; Käsittelijä ei sitten saa blockata
                        (kasittelija (.getParameter notification)))))
                  (catch PSQLException ex
                    (log/debug "Tapahtumat-kuuntelijassa poikkeus, errorcode" (.getErrorCode ex))
                    (log/warn "Tietokantayhteydessä poikkeus: " ex)
                    (uusi-tietokantayhteys! (:db this) ajossa connection kuuntelijat)))

                (Thread/sleep 150)
                (recur))))
    this)

  (stop [this]

    (reset! ajossa false)
    (run! #(u @connection (str "UNLISTEN " % ";"))
          (map first @kuuntelijat))
    (.close @connection)
    this)

  Kuuroudu
  (kuuroudu! [this kanava]
    (swap! kuuntelijat #(dissoc % kanava))
    (u @connection (str "UNLISTEN " kanava ";")))

  Kuuntele
  (kuuntele! [this kanava callback]
    (let [kanava (kanava-nimi kanava)]
      (when-not (get @kuuntelijat kanava)
        ;; LISTEN
        (log/debug "aloitetaan kuuntelu kanavalle" kanava)
        (u @connection (str "LISTEN " kanava ";")))
      (swap! kuuntelijat update-in [kanava] conj callback)))

  Julkaise
  (julkaise! [_ kanava payload]
    (let [kanava (kanava-nimi kanava)]
      (q @connection "SELECT pg_notify(?, ?)" kanava (str payload)))))

(defn luo-tapahtumat []
  (->Tapahtumat (atom nil) (atom nil) (atom false)))
