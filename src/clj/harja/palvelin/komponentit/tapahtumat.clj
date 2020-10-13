(ns harja.palvelin.komponentit.tapahtumat
  "Klusteritason tapahtumien kuuntelu

  Tätä käytetään mm TLOIK-komponentissa, Sonja-yhteysvarmistuksessa, ja ilmoitukset-APIssa.
  Ilmoitusten suhteen on olemassa urakkakohtaisia notifikaatiojonoja, joiden kautta voidaan seurata
  ja ilmoittaa urakkakohtaisista ilmoitukset-tapahtumista"

  (:require [com.stuartsierra.component :as component]
            [cognitect.transit :as t]
            [clojure.core.async :refer [thread] :as async]
            [clojure.spec.alpha :as s]
            [clojure.string :as clj-str]
            [clojure.java.jdbc :as jdbc]

            [harja.palvelin.komponentit.event-tietokanta :as event-tietokanta]
            [harja.tyokalut.dev-tyokalut :as dev-tyokalut]

            [harja.kyselyt.konversio :as konv]
            [harja.transit :as transit]

            [harja.kyselyt.tapahtumat :as q-tapahtumat]
            [taoensso.timbre :as log])
  (:import [org.postgresql PGNotification]
           [org.postgresql.util PSQLException]
           [java.util UUID]
           (clojure.lang LazySeq PersistentList)))

#_(defonce ^{:private true
           :doc "Tähän yhteyteen kaikki LISTEN hommat."}
         event-yhteys nil)

(defonce ^{:private true
           :doc "Tämän kautta lähetetään queryja notify loopiin"}
         tapahtuma-loopin-ajot
         (async/chan))

(defonce harja-tarkkailija nil)

(def transit-write-handler {LazySeq
                            (t/write-handler (constantly "lzse")
                                             #(cast PersistentList %))})

(def transit-read-handler {"lzse" (t/read-handler #(cast PersistentList %))})


(s/def ::tapahtuma (s/or :keyword keyword?
                         :string string?))
(s/def ::tyyppi #{:perus :viimeisin})

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
  (q-tapahtumat/tapahtuman-kanava db {:nimi tapahtuma}))

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
      (tapahtuman-kanava db tapahtuma)
      (:kanava (first vastaus)))))

(defn- kuuntelu-fn [kaytettava-kanava tapahtumayhteys]
  (println "LISTENNNENNEENENEN")
  (u tapahtumayhteys (str "LISTEN " kaytettava-kanava)))

(defn- kuuntele-klusterin-tapahtumaa! [db tapahtuma]
  (let [kaytettava-kanava (kaytettava-kanava! db tapahtuma)]
    (println "--- PITÄS LÖYTYÄ")
    (async/put! tapahtuma-loopin-ajot (partial kuuntelu-fn kaytettava-kanava))
    kaytettava-kanava))

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

(defn tarkasta-tapahtuman-data! [{:keys [payload palvelin hash lahetetty-data]} tapahtuma]
  (when-not (= hash (konv/sha256 payload))
    (log/error (str "[KOMPONENTTI-EVENT] Saatiin tapahtumasta " tapahtuma " erillainen data kuin lähetettiin."))
    (when dev-tyokalut/dev-environment?
      (log/info (str "[KOMPONENTTI-EVENT] Lahetetyn datan tiedot: " lahetetty-data))
      (log/info (str "[KOMPONENTTI-EVENT] Saadun datan tiedot: " (dev-tyokalut/datan-tiedot payload {:type-string? true}))))))

(defprotocol Kuuntele
  (kuuntele! [this tapahtuma callback])
  (tarkkaile! [this tapahtuma] [this tapahtuma tyyppi]))

(defprotocol Julkaise
  (julkaise! [this tapahtuma payload host-name]))

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
          broadcast (async/pub tarkkailu-kanava ::tapahtuma (fn [topic] (async/sliding-buffer 1000)))
          this (assoc this ::tarkkailu-kanava tarkkailu-kanava
                      ::broadcast broadcast)]
      (log/info "Tapahtumat-komponentti käynnistyy")
      (reset! tarkkailijat {})
      (reset! kuuntelijat {})
      (reset! ajossa true)
      ;; kuuntelijat-mapin avaimina on notifikaatiojonon id, esim "sonjaping" tai "urakan_123_tapahtumat".
      ;; arvona kullakin avaimella on seq async-kanavia (?)
      (thread (loop [kaatui-virheeseen? false
                     timeout-arvo 0]
                (let [kaatui-virheeseen? (try (jdbc/with-db-connection [db (get-in this [:db :db-spec])]
                                                                       (let [connection (cast (Class/forName "org.postgresql.jdbc.PgConnection")
                                                                                              (jdbc/db-connection db))]
                                                                         (when kaatui-virheeseen?
                                                                           (doseq [{:keys [kanava]} (q-tapahtumat/kaikki-kanavat db)]
                                                                             (kuuntelu-fn kanava connection)))
                                                                         (loop [break? false]
                                                                           (when (and (not break?)
                                                                                      @ajossa)
                                                                             (let [f (async/poll! tapahtuma-loopin-ajot)]
                                                                               (when f
                                                                                 (println "------ --- -- - -- LÖYTY f")
                                                                                 (f connection)))
                                                                             (let [tapahtumien-haku-onnistui? (try
                                                                                                                (println "NOTIFIKAATIOT")
                                                                                                                (doseq [^PGNotification notification (seq (.getNotifications connection))]
                                                                                                                  (log/info "TAPAHTUI" (.getName notification) " => " (.getParameter notification))
                                                                                                                  (log/debug "kuuntelijat:" @kuuntelijat)
                                                                                                                  (let [tapahtumadatan-id (Integer/parseInt (.getParameter notification))
                                                                                                                        tapahtuman-nimi (.getName notification)
                                                                                                                        json-data (-> (q-tapahtumat/tapahtuman-arvot db {:idt #{tapahtumadatan-id}}) first :arvo)
                                                                                                                        data (transit/lue-transit-string json-data transit-read-handler)]
                                                                                                                    (tarkasta-tapahtuman-data! data tapahtuman-nimi)
                                                                                                                    (doseq [kasittelija (get @kuuntelijat tapahtuman-nimi)]
                                                                                                                      ;; Käsittelijä ei sitten saa blockata
                                                                                                                      (kasittelija data))
                                                                                                                    (async/put! tarkkailu-kanava {::tapahtuma tapahtuman-nimi ::data data})))
                                                                                                                true
                                                                                                                (catch PSQLException e
                                                                                                                  (log/debug "Tapahtumat-kuuntelijassa poikkeus, SQL state" (.getSQLState e))
                                                                                                                  (log/warn "Tapahtumat-kuuntelijassa tapahtui tietokantapoikkeus: " e)
                                                                                                                  false))]
                                                                               (Thread/sleep 5000)
                                                                               (recur (not tapahtumien-haku-onnistui?)))))))
                                              false
                                              (catch Throwable t
                                                (log/error "Tapahtuma loop kaatui: " (.getMessage t) ".\nStack: " (.printStackTrace t))
                                                true))]
                  (async/<!! (async/timeout (min (* 60 1000) timeout-arvo)))
                  (recur kaatui-virheeseen? (+ timeout-arvo (* 2 (max 500 timeout-arvo)))))))
      this))

  (stop [this]
    (async/put! tapahtuma-loopin-ajot
                (fn [tapahtumayhteys]
                  (run! #(u tapahtumayhteys (str "UNLISTEN " %))
                        (map first @kuuntelijat))
                  (doseq [[pos-kanava async-kanava] @tarkkailijat]
                    (u tapahtumayhteys (str "UNLISTEN " pos-kanava))
                    (async/close! async-kanava))))
    (reset! ajossa false)
    (async/close! (::tarkkailu-kanava this))
    (async/close! tapahtuma-loopin-ajot)
    this)

  Kuuntele
  ;; Kuutele! ja tarkkaile! ero on se, että eventin sattuessa kuuntele! kutsuu callback funktiota kun taas
  ;; tarkkaile! lisää eventin async/chan:iin, joka palautetaan kutsujalle.
  (kuuntele! [this tapahtuma callback]
    (let [tapahtuma (tapahtuman-nimi tapahtuma)
          kanava (kuuntele-klusterin-tapahtumaa! (get-in this [:db :db-spec]) tapahtuma)
          fn-tunnistin (str (gensym "callback"))]
      (swap! kuuntelijat update kanava conj (with-meta callback {:tunnistin fn-tunnistin}))
      (fn []
        (swap! kuuntelijat
               update
               kanava
               (fn [kanavan-callbackit]
                 (keep #(not= fn-tunnistin (-> % meta :tunnistin))
                       kanavan-callbackit))))))
  (tarkkaile! [this tapahtuma tyyppi]
    (let [kuuntelija-kanava (async/chan 1000
                                        (map (fn [v]
                                               (log/debug (str "[KOMPONENTTI-EVENT] Saatiin tiedot\n"
                                                               "  event: " tapahtuma "\n"
                                                               "  tiedot: " v))
                                               (::data v)))
                                        (fn [t]
                                          (log/error t (str "Kuuntelija kanavassa error eventille " tapahtuma))))
          tapahtuma (tapahtuman-nimi tapahtuma)
          possu-kanava (kuuntele-klusterin-tapahtumaa! (get-in this [:db :db-spec]) tapahtuma)]
      (case tyyppi
        :perus nil
        :viimeisin (when-let [arvo (q-tapahtumat/uusin-arvo (get-in this [:db :db-spec]) {:nimi tapahtuma})]
                     #_(println (str "arvo: " arvo))
                     #_(println (transit/lue-transit-string arvo transit-read-handler))
                     (async/put! kuuntelija-kanava {::data (transit/lue-transit-string arvo transit-read-handler)})))
      #_(println "----< [(::broadcast this) possu-kanava kuuntelija-kanava]")
      #_(clojure.pprint/pprint [(::broadcast this) possu-kanava kuuntelija-kanava])
      (async/sub (::broadcast this) possu-kanava kuuntelija-kanava)
      (swap! (:tarkkailijat this) update possu-kanava conj kuuntelija-kanava)
      kuuntelija-kanava))
  (tarkkaile! [this tapahtuma]
    (tarkkaile! this tapahtuma :perus))

  Julkaise
  (julkaise! [this tapahtuma payload host-name]
    (let [tapahtuma (tapahtuman-nimi tapahtuma)
          db (get-in this [:db :db-spec])
          kanava (tapahtuman-kanava db tapahtuma)
          julkaistava-data (merge {:payload payload
                                   :palvelin host-name
                                   :hash (konv/sha256 payload)}
                                  (when dev-tyokalut/dev-environment?
                                    {:lahetetty-data (dev-tyokalut/datan-tiedot payload {:type-string? true})}))
          julkaisu-onnistui? (q-tapahtumat/julkaise-tapahtuma db {:kanava kanava :data (transit/clj->transit julkaistava-data
                                                                                                             transit-write-handler)})]
      (when-not julkaisu-onnistui?
        (log/error (str "Tapahtuman " tapahtuma " julkaisu epäonnistui datalle:\n" payload)))
      julkaisu-onnistui?)))

(defn luo-tapahtumat []
  (->Tapahtumat (atom nil) (atom nil) (atom false)))

(defn tarkkailija []
  (if-let [tapahtumat (:klusterin-tapahtumat harja-tarkkailija)]
    tapahtumat
    (throw (Exception. "Harjatarkkailijaa ei vielä käynnistetty!"))))

(defn kaynnista! [{:keys [tietokanta]}]
  (alter-var-root #'harja-tarkkailija
                  (constantly
                    (component/start
                      (component/system-map
                        :db-event (event-tietokanta/luo-tietokanta tietokanta)
                        :klusterin-tapahtumat (component/using
                                                (luo-tapahtumat)
                                                {:db :db-event}))))))