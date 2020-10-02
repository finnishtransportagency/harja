(ns harja.palvelin.komponentit.tapahtumat
  "Klusteritason tapahtumien kuuntelu

  Tätä käytetään mm TLOIK-komponentissa, Sonja-yhteysvarmistuksessa, ja ilmoitukset-APIssa.
  Ilmoitusten suhteen on olemassa urakkakohtaisia notifikaatiojonoja, joiden kautta voidaan seurata
  ja ilmoittaa urakkakohtaisista ilmoitukset-tapahtumista"

  (:require [com.stuartsierra.component :as component]
            [cheshire.core :as cheshire]
            [clojure.core.async :refer [thread] :as async]
            [clojure.string :as clj-str]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.tapahtumat :as q-tapahtumat]
            [harja.fmt :as fmt]
            [taoensso.timbre :as log])
  (:import [org.postgresql PGNotification]
           [org.postgresql.util PSQLException]
           [java.util UUID]
           [java.net InetAddress]))

(defn- uusi-tapahtuman-kanava []
  (str "k_" (clj-str/replace (str (UUID/randomUUID)) #"-" "_")))

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

(defn tapahtuman-kanava
  "Palauttaa PostgreSQL:n kanavan tekstinä annetulle tapahtumalle."
  [db tapahtuma]
  {:pre [(string? tapahtuma)]
   :post [(or (nil? %)
              (string? %))]}
  (-> (q-tapahtumat/tapahtuman-kanava db {:nimi tapahtuma})
      first
      :kanava))

(defn- kaytettava-kanava!
  "Yrittää tallenntaa kantaan uuden kanava-tapahtuma parin kantaan ja plauttaa kanavan.
   Jos kannassa on jo tapahtumalle kanava, ei tallenta mitään vaan palautetaan olemassa oleva kanava."
  [db tapahtuma]
  {:pre [(string? tapahtuma)]
   :post [(string? %)]}
  (let [uusi-tapahtuman-kanava (uusi-tapahtuman-kanava)
        vastaus (q-tapahtumat/lisaa-tapahtuma db {:nimi tapahtuma :kanava uusi-tapahtuman-kanava})]
    (println "---> VASTAUS: " vastaus)
    (if (empty? vastaus)
      (tapahtuman-kanava db {:nimi tapahtuma})
      (:kanava (first vastaus)))))

(defn- kuuntele-klusterin-tapahtumaa! [db tapahtuma]
  (let [kaytettava-kanava (kaytettava-kanava! db tapahtuma)]
    (jdbc/with-db-connection [db db]
      (let [connection (jdbc/db-connection db)]
        (u connection (str "LISTEN " kaytettava-kanava)))
      kaytettava-kanava)))

(defn- kuuroudu-klusterin-tapahtumasta [db tapahtuma]
  (let [tapahtuman-kanava (tapahtuman-kanava db tapahtuma)]
    (jdbc/with-db-connection [db db]
      (let [connection (jdbc/db-connection db)]
        (u connection (str "UNLISTEN " tapahtuman-kanava))))))


(def get-notifications (->> (Class/forName "org.postgresql.jdbc.PgConnection")
                            .getMethods
                            (filter #(and (= (.getName %) "getNotifications")
                                          (= 0 (count (.getParameters %)))))
                            first))

(defn- tapahtuman-nimi [kw]
  (-> kw
      name
      (.replace "-" "_")
      (.replace "!" "")
      (.replace "?" "")
      (.replace "<" "")
      (.replace ">" "")))

#_(defn- uusi-tietokantayhteys! [db ajossa connection kuuntelijat tarkkailijat]
  ;; Luotetaan siihen, että tätä ajaa vain yksi säie, start-funktion (thread (loop ... )) -blokki,
  ;; joten tätä ei yritetä tehdä yhtä aikaa useasta suunnasta/säikeestä.
  (reset! ajossa false)
  (log/info "Uudelleenalustetaan tietokannan kuunteluyhteys")
  (try
    (log/debug "Yritetään sulkea vanha tietokantayhteys")
    (.close @connection)
    (catch Exception e
      (log/warn "Vanhan tietokanta yhteyden sulkemisessa tapahtui poikkeus" e)))
  (reset! connection (.getConnection (:datasource db)))
  (log/debug "Saatiin uusi uusi tietokantayhteys")
  (doseq [kanava (distinct (concat (keys @kuuntelijat)
                                   (keys @tarkkailijat)))]
    (log/debug "Aloitetaan kuuntelu kanavalle: " kanava)
    (u @connection (str "LISTEN " kanava)))
  (reset! ajossa true))

(defprotocol Kuuntele
  (kuuntele! [this tapahtuma callback])
  (tarkkaile! [this tapahtuma] [this tapahtuma tyyppi]))

(defprotocol Julkaise
  (julkaise! [this tapahtuma payload]))

(defprotocol Kuuroudu
  (kuuroudu! [this tapahtuma]))

(defrecord Tapahtumat [kuuntelijat tarkkailijat ajossa]
  component/Lifecycle
  (start [this]
    (let [tarkkailu-kanava (async/chan (async/sliding-buffer 1000)
                                       (map (fn [arvo]
                                              (log/debug (str "[KOMPONENTTI-EVENT] Lähetetään tiedot perus-broadcast jonosta\n"
                                                              "  tiedot: " arvo))
                                              arvo))
                                       (fn [t]
                                         (log/error t "perus-broadcast jonossa tapahtui virhe")))
          broadcast (async/pub tarkkailu-kanava ::tapahtuma (fn [] (async/sliding-buffer 1000)))
          this (assoc this ::tarkkailu-kanava tarkkailu-kanava
                      ::broadcast broadcast)]
      (log/info "Tapahtumat-komponentti käynnistyy")
      (reset! tarkkailijat {})
      (reset! kuuntelijat {})
      (reset! ajossa true)
      #_(reset! connection (doto (.getConnection (:datasource (:db this)))
                           (.setAutoCommit false)))
      ;; kuuntelijat-mapin avaimina on notifikaatiojonon id, esim "sonjaping" tai "urakan_123_tapahtumat".
      ;; arvona kullakin avaimella on seq async-kanavia (?)
      (thread (loop []
                (jdbc/with-db-connection [db (get-in this [:db :db-spec])]
                  (let [connection (cast (Class/forName "org.postgresql.jdbc.PgConnection")
                                         (jdbc/db-connection db))]
                    (loop [break? false]
                      (when (and (not break?)
                                 @ajossa)
                        (let [tapahtumien-haku-onnistui? (try
                                                           (doseq [^PGNotification notification (.getNotifications connection)]
                                                             (log/info "TAPAHTUI" (.getName notification) " => " (.getParameter notification))
                                                             (log/debug "kuuntelijat:" @kuuntelijat)
                                                             (let [data (cheshire/decode (.getParameter notification))]
                                                               (doseq [kasittelija (get @kuuntelijat (.getName notification))]
                                                                 ;; Käsittelijä ei sitten saa blockata
                                                                 (kasittelija data))
                                                               (async/put! tarkkailu-kanava {::tapahtuma (.getName notification) ::data data})))
                                                           true
                                                           (catch PSQLException e
                                                             (log/debug "Tapahtumat-kuuntelijassa poikkeus, SQL state" (.getSQLState e))
                                                             (log/warn "Tapahtumat-kuuntelijassa tapahtui tietokantapoikkeus: " e)
                                                             false))]
                          (Thread/sleep 5000)
                          (recur tapahtumien-haku-onnistui?))))))
                (recur)))
      this))

  (stop [this]
    (reset! ajossa false)
    (jdbc/with-db-connection [db (get-in this [:db :db-spec])]
      (let [connection (jdbc/db-connection db)]
        (run! #(u connection (str "UNLISTEN " %))
              (map first @kuuntelijat))
        (doseq [[pos-kanava async-kanava] @tarkkailijat]
          (u connection (str "UNLISTEN " pos-kanava))
          (async/close! async-kanava))))
    (async/close! (::tarkkailu-kanava this))
    this)

  Kuuroudu
  (kuuroudu! [this tapahtuma]
    (let [kanava (tapahtuman-kanava (:db this) tapahtuma)]
      (swap! kuuntelijat #(dissoc % kanava))
      #_(u @connection (str "UNLISTEN " kanava))))

  Kuuntele
  ;; Kuutele! ja tarkkaile! ero on se, että eventin sattuessa kuuntele! kutsuu callback funktiota kun taas
  ;; tarkkaile! lisää eventin async/chan:iin, joka palautetaan kutsujalle.
  (kuuntele! [this tapahtuma callback]
    (let [tapahtuma (tapahtuman-nimi tapahtuma)
          kanava (kuuntele-klusterin-tapahtumaa! (get-in this [:db :db-spec]) tapahtuma)]
      (swap! kuuntelijat update kanava conj callback)))
  (tarkkaile! [this tapahtuma tyyppi]
    (let [kuuntelija-kanava (async/chan 1000
                                        (map (fn [v]
                                               (log/debug (str "[KOMPONENTTI-EVENT] Saatiin tiedot\n"
                                                               "  event: " tapahtuma "\n"
                                                               "  tiedot: " v))
                                               (::data v)))
                                        (fn [t]
                                          (log/error t (str "Kuuntelija kanavassa error eventille " tapahtuma))))
          possu-kanava (kaytettava-kanava! (:db this) tapahtuma)]
      (case tyyppi
        :perus nil
        :viimeisin (when-let [arvo (-> (q-tapahtumat/uusin-arvo (:db this) {:nimi tapahtuma}) first :uusin-arvo)]
                     (async/put! kuuntelija-kanava {::data arvo})))
      (async/sub (::broadcast this) possu-kanava kuuntelija-kanava)
      (swap! (:tarkkailijat this) assoc possu-kanava kuuntelija-kanava)
      kuuntelija-kanava))
  (tarkkaile! [this tapahtuma]
    (tarkkaile! this tapahtuma :perus))

  Julkaise
  (julkaise! [this tapahtuma payload]
    (let [tapahtuma (tapahtuman-nimi tapahtuma)
          db (:db this)
          kanava (tapahtuman-kanava db tapahtuma)]
      (q-tapahtumat/julkaise-tapahtuma db {:kanava kanava :data (cheshire/encode {:payload payload
                                                                                  :palvelin (fmt/leikkaa-merkkijono 512
                                                                                                                    (.toString (InetAddress/getLocalHost)))})}))))

(defn luo-tapahtumat []
  (->Tapahtumat (atom nil) (atom nil) (atom false)))
