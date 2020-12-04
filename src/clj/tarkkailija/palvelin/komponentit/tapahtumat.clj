(ns tarkkailija.palvelin.komponentit.tapahtumat
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

            [harja.tyokalut.dev-tyokalut :as dev-tyokalut]
            [harja.palvelin.tyokalut.tyokalut :as tyokalut]

            [harja.palvelin.tapahtuma-protokollat :as p]

            [harja.kyselyt.konversio :as konv]
            [harja.transit :as transit]

            [harja.kyselyt.tapahtumat :as q-tapahtumat]
            [taoensso.timbre :as log]
            [harja.pvm :as pvm])
  (:import [org.postgresql PGNotification]
           [org.postgresql.util PSQLException]
           [org.joda.time DateTime]
           [java.util UUID]
           (clojure.lang ArityException)))

(defonce tapahtuma-loop-kaynnissa? (atom false))

(defonce ^{:dynamic true
           :doc "Kun jotain tapahtumaa aletaan tarkkailemaan, ei se tapahdu samantien.
                 Ensin pitää odottaa, että tapahtuma-loop lukee tapahtuma-loopin-ajot
                 kanavan kautta sisäänsä halutun tapahtuman kuuntelun. Jos halutaan, että
                 useampaa tapahtumaa aletaan kuntelemaan 'yhtäaikaa' (eli yhden loopin sisällä),
                 voidaan se varmistaa bindaamalla tämä var johonkin tagiin. Kaikki yhtäaikaa
                 aloittavat kuuntelijat pitää aloittaa saman bindingin aikana.

                 Tälle tulee antaa arvoksi map, jossa avaimet :tunnistin ja :lkm"}
         *tarkkaile-yhta-aikaa* nil)

(def transit-write-optiot {:handlers {DateTime (t/write-handler (constantly "dt")
                                                                #(-> % pvm/suomen-aikavyohykkeeseen pvm/suomen-aika->iso8601-basic))}})

(def transit-read-optiot {:handlers {"dt" (t/read-handler #(pvm/iso8601-basic->suomen-aika %))}})

(def tama-namespace (namespace ::this))


(s/def ::tapahtuma (s/or :keyword keyword?
                         :string string?))

(s/def ::palvelimet-perus-asetukset (s/and set?
                                           #(every? string? %)))
(s/def ::palvelimet-viimeisin-asetukset (s/and set?
                                               #(every? string? %)))

(s/def ::tyyppien-nimet #{:perus :viimeisin :viimeisin-per-palvelin :palvelimet-perus :palvelimet-viimeisin})
(s/def ::tyyppi-asetuksilla (s/and map?
                                   #(= (count %) 1)
                                   #(s/valid? ::tyyppien-nimet (key (first %)))
                                   #(s/valid? (keyword tama-namespace (str (name (key (first %))) "-asetukset")) (val (first %)))))
(s/def ::tyyppi (s/or :keyword ::tyyppien-nimet
                      :map ::tyyppi-asetuksilla))

(defn- uusi-tapahtuman-kanava []
  (str (UUID/randomUUID)))

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

(defn tapahtuman-tiedot-clj-dataksi [{:keys [arvo luotu palvelin]}]
  (assoc (transit/lue-transit-string arvo transit-read-optiot)
    :aika (pvm/suomen-aikavyohykkeeseen (pvm/joda-timeksi luotu))
    :palvelin palvelin))


(defn tapahtuman-kanava
  "Palauttaa PostgreSQL:n kanavan tekstinä annetulle tapahtumalle."
  [db tapahtuma]
  {:pre [(string? tapahtuma)]
   :post [(or (nil? %)
              (string? %))]}
  (q-tapahtumat/hae-tapahtuman-kanava db {:nimi tapahtuma}))

(defn- kaytettava-kanava!
  "Yrittää tallenntaa kantaan uuden kanava-tapahtuma parin kantaan ja plauttaa kanavan.
   Jos kannassa on jo tapahtumalle kanava, ei tallenta mitään vaan palautetaan olemassa oleva kanava."
  [db tapahtuma]
  {:pre [(string? tapahtuma)]
   :post [(or (string? %)
              (and (keyword? %)
                   (= (namespace ::this)
                      (namespace %))))]}
  (try (let [uusi-tapahtuman-kanava (uusi-tapahtuman-kanava)
             vastaus (q-tapahtumat/lisaa-tapahtuma db {:nimi tapahtuma :kanava uusi-tapahtuman-kanava})]
         (if (nil? vastaus)
           (tapahtuman-kanava db tapahtuma)
           vastaus))
       (catch PSQLException e
         (log/error (str "Yritettiin saada possukanavaa tapahtumalle: " tapahtuma
                         ", mutta se epäonnistui tietokantavirheen takia.\n"
                         "Virhe: " (.getMessage e) ".\nStack: " (.printStackTrace e)))
         ::virhe)
       (catch Throwable t
         (log/error (str "Yritettiin saada possukanavaa tapahtumalle: " tapahtuma ", mutta se epäonnistui.\n"
                         "Virhe: " (.getMessage t) ".\nStack: " (.printStackTrace t)))
         ::virhe)))

(defn- kuuntelu-fn
  [kaytettava-kanava tapahtumayhteys]
  {:pre [(string? kaytettava-kanava)
         (uuid? (UUID/fromString kaytettava-kanava))]}
  (u tapahtumayhteys (str "LISTEN \"" kaytettava-kanava "\"")))

(defn tapahtuman-data-ok?!
  [{:keys [arvo hash palvelin]} tapahtuma]
  (if-not (= hash (konv/sha256 arvo))
    (let [{:keys [payload lahetetty-data]} (transit/lue-transit-string arvo transit-read-optiot)]
      (log/error (str "Saatiin tapahtumasta " tapahtuma " erillainen data kuin lähetettiin."))
      (when dev-tyokalut/dev-environment?
        (log/info (str "[KOMPONENTTI-EVENT] Saatu payload: " payload))
        (log/info (str "[KOMPONENTTI-EVENT] palvelin: " palvelin))
        (log/info (str "[KOMPONENTTI-EVENT] hash: " hash))
        (log/info (str "[KOMPONENTTI-EVENT] Lahetetyn datan tiedot: " lahetetty-data))
        (log/info (str "[KOMPONENTTI-EVENT] Saadun datan tiedot: " (dev-tyokalut/datan-tiedot payload {:type-string? true})))
        #_(dev-tyokalut/etsi-epakohta-datasta payload lahetetty-data (dev-tyokalut/datan-tiedot payload {:type-string? true})))
      false)
    true))

(defn paluuarvo [tyyppi tapahtuma db]
  (let [tapahtuman-tiedot (case tyyppi
                            :viimeisin (q-tapahtumat/hae-uusin-arvo db {:nimi tapahtuma})
                            (:palvelimet-viimeisin :viimeisin-per-palvelin) (q-tapahtumat/hae-uusin-arvo-per-palvelin db {:nimi tapahtuma})
                            nil)
        uusin-arvo-loytynyt? (and (not (nil? tapahtuman-tiedot))
                                  (not (empty? tapahtuman-tiedot)))]
    ;(log/debug "[KOMPONENTTI-EVENT] paluuarvo - tapahtuma: " tapahtuma " tyyppi: " tyyppi " json-data nil? " (nil? json-data))
    (when uusin-arvo-loytynyt?
      (case tyyppi
        :viimeisin (when (tapahtuman-data-ok?! (first tapahtuman-tiedot) tapahtuma)
                     (tapahtuman-tiedot-clj-dataksi (first tapahtuman-tiedot)))
        (:palvelimet-viimeisin :viimeisin-per-palvelin) (when (every? (fn [tapahtuman-tiedot]
                                                                        (tapahtuman-data-ok?! tapahtuman-tiedot tapahtuma))
                                                                      tapahtuman-tiedot)
                                                          (mapv tapahtuman-tiedot-clj-dataksi tapahtuman-tiedot))))))

(defn- kuuntele-klusterin-tapahtumaa!
  [{:keys [db tapahtuma-loopin-ajot kuuntelu-aloitettu-broadcast kuuntelu-aloitettu-kuittaus
           tapahtuma tyyppi kuuntelun-jalkeen ryhmatunnistin lkm]}]
  {:pre [(not (nil? db))
         (not (nil? tapahtuma))]}
  (thread (let [possukanava (kaytettava-kanava! db tapahtuma)]
            ;(log/debug "[KOMPONENTTI-EVENT] kuuntele-klusterin-tapahtumaa! tapahtuma: " tapahtuma)
            ;(log/debug "[KOMPONENTTI-EVENT] kuuntele-klusterin-tapahtumaa! possukanava: " possukanava)
            (if (= ::virhe possukanava)
              [::virhe nil]
              (let [kuuntelu-aloitettu-tunnistin (str (gensym "kuuntelu"))
                    kuuntelu-aloitettu-huomauttaja (async/chan)]
                ;(log/debug "[KOMPONENTTI-EVENT] kuuntele-klusterin-tapahtumaa! tunnistin: " kuuntelu-aloitettu-tunnistin)
                (async/sub kuuntelu-aloitettu-broadcast kuuntelu-aloitettu-tunnistin kuuntelu-aloitettu-huomauttaja)
                (async/put! tapahtuma-loopin-ajot {:f (fn [db]
                                                        (jdbc/with-db-transaction [db db]
                                                                                  (kuuntelu-fn possukanava (jdbc/get-connection db))
                                                                                  (paluuarvo tyyppi tapahtuma db)))
                                                   :tunnistin kuuntelu-aloitettu-tunnistin
                                                   :ryhmatunnistin ryhmatunnistin
                                                   :yhta-aikaa? (boolean ryhmatunnistin)
                                                   :lkm lkm})
                (let [{::keys [paluuarvo]} (async/<!! kuuntelu-aloitettu-huomauttaja)]
                  ;(log/debug "[KOMPONENTTI-EVENT] AJETAAN JÄLKEEN FN TAPAHTUMALLE: " tapahtuma)
                  (if (= ::virhe paluuarvo)
                    [::virhe nil]
                    (do
                      (kuuntelun-jalkeen possukanava paluuarvo)
                      (async/put! kuuntelu-aloitettu-kuittaus true)
                      [possukanava paluuarvo]))))))))

(defn- tapahtuman-nimi [kw]
  (-> kw
      name
      (.replace "-" "_")
      (.replace "!" "")
      (.replace "?" "")
      (.replace "<" "")
      (.replace ">" "")))

(defn pura-tapahtuma-loopin-ajot! [db tapahtuma-loopin-ajot kuuntelu-aloitettu-kuittaus kuuntelu-aloitettu yhta-aikaa-ajettavat]
  (let [ajot (loop [ajo (async/poll! tapahtuma-loopin-ajot)
                    ajot []]
               (if (nil? ajo)
                 ajot
                 (recur (async/poll! tapahtuma-loopin-ajot)
                        (conj ajot ajo))))
        ajojen-lkm (volatile! 0)]
    (doseq [{:keys [f tunnistin ryhmatunnistin yhta-aikaa? lkm]} ajot]
      (try
        (cond
          (not (and f tunnistin)) (log/error (str "Tapahtuma looppiin annettu käsky ilman tunnistinta tai funktiota. Tunnistin: " tunnistin " funktio: " f))
          (and yhta-aikaa?
               (-> @yhta-aikaa-ajettavat (get ryhmatunnistin) count (= (dec lkm))))
          (jdbc/with-db-transaction [db db]
                                    (doseq [{:keys [f tunnistin]} (get @yhta-aikaa-ajettavat ryhmatunnistin)]
                                      ;(log/debug (str "[KOMPONENTTI-EVENT] ALOITETAAN KUUNTELU tunnisteelle: " tunnistin))
                                      (let [paluuarvo (f db)]
                                        (async/put! kuuntelu-aloitettu {::kuuntelu tunnistin
                                                                        ::paluuarvo paluuarvo})
                                        (vswap! ajojen-lkm inc)))
                                    ;(log/debug (str "[KOMPONENTTI-EVENT] ALOITETAAN KUUNTELU tunnisteelle: " tunnistin))
                                    (let [paluuarvo (f db)]
                                      (async/put! kuuntelu-aloitettu {::kuuntelu tunnistin
                                                                      ::paluuarvo paluuarvo})
                                      (vswap! ajojen-lkm inc)
                                      (swap! yhta-aikaa-ajettavat dissoc ryhmatunnistin)))
          yhta-aikaa?
          (swap! yhta-aikaa-ajettavat update ryhmatunnistin conj {:f f :tunnistin tunnistin})
          :else
          (when f
            ;(log/debug (str "[KOMPONENTTI-EVENT] ALOITETAAN KUUNTELU tunnisteelle: " tunnistin))
            (let [paluuarvo (f db)]
              (when tunnistin
                (async/put! kuuntelu-aloitettu {::kuuntelu tunnistin
                                                ::paluuarvo paluuarvo}))
              (vswap! ajojen-lkm inc))
            #_(when tunnistin
                (async/put! kuuntelu-aloitettu {::kuuntelu tunnistin}))))
        (catch Throwable t
          (log/error (str "Tapahtuma loopin ajot kaatui virheeseen: " (.getMessage t)))
          (async/put! kuuntelu-aloitettu {::kuuntelu tunnistin
                                          ::paluuarvo ::virhe})
          (binding [*out* *err*]
            (println "Stack trace:"))
          (.printStackTrace t)
          (throw t))))
    (async/<!! (async/go-loop [kuittaukset []]
                 (when-not (= @ajojen-lkm (count kuittaukset))
                   (let [kuittaus (async/<! kuuntelu-aloitettu-kuittaus)]
                     (recur (conj kuittaukset kuittaus))))))
    #_(log/debug (str "[KOMPONENTTI-EVENT] pura-tapahtuma-loopin-ajot! finito"))))

(defn- tapahtuma-loop-sisalto [{:keys [ajossa db connection tapahtuma-loopin-ajot kuuntelu-aloitettu-kuittaus
                                       kuuntelu-aloitettu yhta-aikaa-ajettavat kuuntelijat tarkkailu-kanava
                                       loop-odotus]}]
  (pura-tapahtuma-loopin-ajot! db tapahtuma-loopin-ajot kuuntelu-aloitettu-kuittaus kuuntelu-aloitettu yhta-aikaa-ajettavat)
  (let [tapahtumien-haku-onnistui? (try
                                     (doseq [^PGNotification notification (seq (.getNotifications connection))]
                                       (let [tapahtumadatan-id (Integer/parseInt (.getParameter notification))
                                             tapahtuman-nimi (.getName notification)
                                             tapahtuman-tiedot (-> (q-tapahtumat/hae-tapahtuman-tiedot db {:idt #{tapahtumadatan-id}}) first)]
                                         (log/debug (str "TAPAHTUMA LOOP - TAPAHTUMAN NIMI: " tapahtuman-nimi))
                                         (when (tapahtuman-data-ok?! tapahtuman-tiedot tapahtuman-nimi)
                                           (let [data (tapahtuman-tiedot-clj-dataksi tapahtuman-tiedot)]
                                             (doseq [kasittelija (get @kuuntelijat tapahtuman-nimi)]
                                               ;; Käsittelijä ei sitten saa blockata
                                               (kasittelija data))
                                             (async/>!! tarkkailu-kanava {::tapahtuma tapahtuman-nimi ::data data})))))
                                     true
                                     (catch PSQLException e
                                       (log/debug "Tapahtumat-kuuntelijassa poikkeus, SQL state" (.getSQLState e))
                                       (log/error "Tapahtumat-kuuntelijassa tapahtui tietokantapoikkeus: " e)
                                       false))]
    (when tapahtumien-haku-onnistui?
      (async/<!! (async/timeout (or loop-odotus 100))))
    (when @ajossa
      (not tapahtumien-haku-onnistui?))))

(defn- tarkkailija-kuuntelun-jalkeen [tyyppi kuuntelija-kanava broadcast tarkkailijat]
  (fn [possu-kanava paluu-arvo]
    ;(log/debug "[KOMPONENTTI-EVENT]tarkkailija-kuuntelun-jalkeen: " tyyppi)
    (when-not (nil? paluu-arvo)
      (case tyyppi
        (:palvelimet-perus :perus) nil
        :viimeisin (async/put! kuuntelija-kanava {::data paluu-arvo})
        (:viimeisin-per-palvelin :palvelimet-viimeisin) (doseq [arvo paluu-arvo]
                                                          (async/put! kuuntelija-kanava {::data arvo}))))
    (async/sub broadcast possu-kanava kuuntelija-kanava)
    (swap! tarkkailijat update possu-kanava conj kuuntelija-kanava)))

(defrecord Tapahtumat [asetukset kuuntelijat tarkkailijat ajossa]
  component/Lifecycle
  (start [this]
    (let [buffer-koko 10
          tapahtuma-loopin-ajot (async/chan buffer-koko)
          kuuntelu-aloitettu (async/chan)
          kuuntelu-aloitettu-broadcast (async/pub kuuntelu-aloitettu ::kuuntelu)
          kuuntelu-aloitettu-kuittaus (async/chan buffer-koko)
          tarkkailu-kanava (async/chan (async/sliding-buffer 1000)
                                       (map (fn [arvo]
                                              (log/debug (str "[KOMPONENTTI-EVENT] Lähetetään tiedot broadcast jonosta\n"
                                                              "  tiedot: " arvo))
                                              arvo))
                                       (fn [t]
                                         (log/error t "broadcast jonossa tapahtui virhe")))
          broadcast (async/pub tarkkailu-kanava ::tapahtuma (fn [topic] (async/sliding-buffer 1000)))
          this (assoc this ::tarkkailu-kanava tarkkailu-kanava
                      ::broadcast broadcast
                           ::kuuntelu-aloitettu-broadcast kuuntelu-aloitettu-broadcast
                           ::tapahtuma-loopin-ajot tapahtuma-loopin-ajot
                           ::kuuntelu-aloitettu kuuntelu-aloitettu
                           ::kuuntelu-aloitettu-kuittaus kuuntelu-aloitettu-kuittaus
                           ::tarkkailija->tapahtuma (atom {}))
          kuuntelu-valmis (async/chan)]
      (log/info "Tapahtumat-komponentti käynnistyy")
      (reset! tarkkailijat {})
      (reset! kuuntelijat {})
      (reset! ajossa true)
      ;; kuuntelijat-mapin avaimina on notifikaatiojonon id, esim "sonjaping" tai "urakan_123_tapahtumat".
      ;; arvona kullakin avaimella on seq async-kanavia (?)
      (thread (loop [kaatui-virheeseen? false
                     timeout-arvo 0]
                (let [kaatui-virheeseen? (try (jdbc/with-db-connection [db (get-in this [:db :db-spec])]
                                                                       (async/put! kuuntelu-valmis true)
                                                                       (let [connection (cast (Class/forName "org.postgresql.jdbc.PgConnection")
                                                                                              (jdbc/db-connection db))
                                                                             yhta-aikaa-ajettavat (atom {})]
                                                                         (when kaatui-virheeseen?
                                                                           (doseq [{:keys [kanava]} (q-tapahtumat/hae-kaikki-kanavat db)]
                                                                             (kuuntelu-fn kanava connection))
                                                                           (reset! tapahtuma-loop-kaynnissa? true))
                                                                         (loop []
                                                                           (if (or (not @ajossa)
                                                                                   (tapahtuma-loop-sisalto {:ajossa ajossa :db db :connection connection :tapahtuma-loopin-ajot tapahtuma-loopin-ajot
                                                                                                            :loop-odotus (get-in asetukset [:tarkkailija :loop-odotus])
                                                                                                            :kuuntelu-aloitettu-kuittaus kuuntelu-aloitettu-kuittaus
                                                                                                            :kuuntelu-aloitettu kuuntelu-aloitettu :yhta-aikaa-ajettavat yhta-aikaa-ajettavat
                                                                                                            :kuuntelijat kuuntelijat :tarkkailu-kanava tarkkailu-kanava}))
                                                                             true
                                                                             (recur)))))
                                              (catch Throwable t
                                                (log/error "Tapahtuma loop kaatui: " (.getMessage t) ".\nStack: " (.printStackTrace t))
                                                true))]
                  (reset! tapahtuma-loop-kaynnissa? false)
                  (when @ajossa
                    (async/<!! (async/timeout timeout-arvo))
                    (recur kaatui-virheeseen? (min (* 60 1000)
                                                   (+ timeout-arvo
                                                      (* 2
                                                         (max 500 timeout-arvo)))))))))
      (reset! tapahtuma-loop-kaynnissa? true)
      (async/<!! kuuntelu-valmis)
      this))

  (stop [this]
    (async/put! (::tapahtuma-loopin-ajot this)
                (fn [tapahtumayhteys]
                  (run! #(u tapahtumayhteys (str "UNLISTEN \"" % "\""))
                        (map first @kuuntelijat))
                  (doseq [[pos-kanava async-kanava] @tarkkailijat]
                    (u tapahtumayhteys (str "UNLISTEN \"" pos-kanava "\""))
                    (async/close! async-kanava))))
    (reset! tapahtuma-loop-kaynnissa? false)
    (reset! ajossa false)
    (doseq [[tarkkailija _] @(::tarkkailija->tapahtuma this)]
      (p/lopeta-tarkkailu! this tarkkailija))
    (async/close! (::tarkkailu-kanava this))
    (async/close! (::tapahtuma-loopin-ajot this))
    (async/close! (::kuuntelu-aloitettu this))
    (async/unsub-all (::kuuntelu-aloitettu-broadcast this))
    (async/close! (::kuuntelu-aloitettu-kuittaus this))
    this)

  p/Kuuntele
  ;; Kuutele! ja tarkkaile! ero on se, että eventin sattuessa kuuntele! kutsuu callback funktiota kun taas
  ;; tarkkaile! lisää eventin async/chan:iin, joka palautetaan kutsujalle. Lisäksi tarkkaile! käsittely
  ;; on async kun taasen kuuntele! ei ole
  (kuuntele! [this tapahtuma callback]
    (when-not (ifn? callback)
      (throw (IllegalArgumentException. "Tapahtuman kuuntelija callbackin pitää toteuttaa IFn protokolla")))
    (let [arityjen-maara (tyokalut/arityt callback)]
      (when-not (contains? arityjen-maara 1)
        (throw (ArityException. (first arityjen-maara) "Callback funktio tapahtumalle pitää sisältää ainakin arity 1"))))
    (log/debug (str "KUUNTELE TAPAHTUMAA: " tapahtuma))
    (let [tapahtuma (tapahtuman-nimi tapahtuma)
          fn-tunnistin (str (gensym "callback"))
          kuuntelun-jalkeen (fn [possukanava _]
                              (swap! kuuntelijat update possukanava conj (with-meta callback {:tunnistin fn-tunnistin})))
          kuuntelu-sekvenssi (kuuntele-klusterin-tapahtumaa! {:db (get-in this [:db :db-spec])
                                                              :kuuntelu-aloitettu-broadcast (::kuuntelu-aloitettu-broadcast this)
                                                              :tapahtuma-loopin-ajot (::tapahtuma-loopin-ajot this)
                                                              :kuuntelu-aloitettu-kuittaus (::kuuntelu-aloitettu-kuittaus this)
                                                              :tapahtuma tapahtuma
                                                              :kuuntelun-jalkeen kuuntelun-jalkeen})
          [possukanava _] (async/<!! kuuntelu-sekvenssi)]
      (if (= possukanava ::virhe)
        false
        (fn []
          (swap! kuuntelijat
                 update
                 possukanava
                 (fn [kanavan-callbackit]
                   (keep #(when-not (= fn-tunnistin (-> % meta :tunnistin))
                            %)
                         kanavan-callbackit)))
          true))))
  (tarkkaile! [this tapahtuma tyyppi]
    (let [{:keys [tunnistin lkm]} *tarkkaile-yhta-aikaa*]
      (when (and *tarkkaile-yhta-aikaa*
                 (or (not (string? tunnistin))
                     (not (integer? lkm))))
        (throw (IllegalArgumentException. (str "Yhtäaikaa alkavien tarkkailujen pitää antaa string tunnistin "
                                               "ja lukumäärä, että moniko on aloittamassa yhtäaikaa. "
                                               "Nyt saatiin tyypeiksi - tunnistin: " (type tunnistin)
                                               " lkm: " (type lkm)))))
      (thread (let [normalisoitu-tyyppi (if (keyword? tyyppi)
                                          {tyyppi nil}
                                          tyyppi)
                    [tyyppi tyypin-asetukset] (first normalisoitu-tyyppi)
                    kuuntelijakanava-xf (cond-> (map (fn [v]
                                                       (select-keys (::data v) #{:payload :palvelin :aika})))
                                                (or (= tyyppi :palvelimet-perus)
                                                    (= tyyppi :palvelimet-viimeisin)) (comp (filter (fn [{palvelin :palvelin}]
                                                                                             (contains? tyypin-asetukset palvelin))))
                                                (:kehitysmoodi asetukset) (comp (map (fn [v]
                                                                                       (log/debug (str "[KOMPONENTTI-EVENT] Saatiin tiedot\n"
                                                                                                       "  event: " tapahtuma "\n"
                                                                                                       "  tiedot: " v))
                                                                                       v))))
                    kuuntelija-kanava (async/chan 1000
                                                  kuuntelijakanava-xf
                                                  (fn [t]
                                                    (log/error t (str "Kuuntelija kanavassa error eventille " tapahtuma))))
                    tapahtuma (tapahtuman-nimi tapahtuma)
                    kuuntelun-jalkeen (tarkkailija-kuuntelun-jalkeen tyyppi kuuntelija-kanava (::broadcast this) (:tarkkailijat this))
                    kuuntelu-sekvenssi (kuuntele-klusterin-tapahtumaa! {:db (get-in this [:db :db-spec])
                                                                        :kuuntelu-aloitettu-broadcast (::kuuntelu-aloitettu-broadcast this)
                                                                        :tapahtuma-loopin-ajot (::tapahtuma-loopin-ajot this)
                                                                        :kuuntelu-aloitettu-kuittaus (::kuuntelu-aloitettu-kuittaus this)
                                                                        :tapahtuma tapahtuma
                                                                        :tyyppi tyyppi
                                                                        :kuuntelun-jalkeen kuuntelun-jalkeen
                                                                        :ryhmatunnistin tunnistin
                                                                        :lkm lkm})
                    [possukanava _] (async/<!! kuuntelu-sekvenssi)]
                (if (= ::virhe possukanava)
                  false
                  (do (swap! (::tarkkailija->tapahtuma this) merge {kuuntelija-kanava possukanava})
                      kuuntelija-kanava))))))
  (tarkkaile! [this tapahtuma]
    (p/tarkkaile! this tapahtuma :perus))

  p/Kuuroudu
  (lopeta-tarkkailu! [this kuuntelija]
    (async/unsub (::broadcast this) (get (::tarkkailija->tapahtuma this) kuuntelija) kuuntelija)
    (swap! (::tarkkailija->tapahtuma this) dissoc kuuntelija)
    nil)

  p/Julkaise
  (julkaise! [this tapahtuma payload host-name]
    (let [tapahtuma (tapahtuman-nimi tapahtuma)
          db (get-in this [:db :db-spec])
          kanava (kaytettava-kanava! db tapahtuma)]
      (if (= ::virhe kanava)
        false
        (let [julkaistava-data (merge {:payload payload}
                                      (when dev-tyokalut/dev-environment?
                                        {:lahetetty-data (dev-tyokalut/datan-tiedot payload {:type-string? true})}))
              data-transitina (transit/clj->transit julkaistava-data transit-write-optiot)
              transit-hash (konv/sha256 (str data-transitina))
              julkaisu-onnistui? (q-tapahtumat/julkaise-tapahtuma db {:kanava kanava
                                                                      :data data-transitina
                                                                      :hash transit-hash
                                                                      :palvelin host-name})]
          (when-not julkaisu-onnistui?
            (log/error (str "Tapahtuman " tapahtuma " julkaisu epäonnistui datalle:\n" payload)))
          julkaisu-onnistui?)))))

(defn luo-tapahtumat [asetukset]
  (->Tapahtumat asetukset (atom nil) (atom nil) (atom false)))