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
            [harja.palvelin.tyokalut.tyokalut :as tyokalut]

            [harja.kyselyt.konversio :as konv]
            [harja.transit :as transit]

            [harja.kyselyt.tapahtumat :as q-tapahtumat]
            [taoensso.timbre :as log])
  (:import [org.postgresql PGNotification]
           [org.postgresql.util PSQLException]
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

(defonce harja-tarkkailija nil)

(def transit-write-handler {})

(def transit-read-handler {})


(s/def ::tapahtuma (s/or :keyword keyword?
                         :string string?))
(s/def ::tyyppi #{:perus :viimeisin :viimeisin-per-palvelin})

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
         (uuid? (UUID/fromString (-> kaytettava-kanava (clj-str/replace-first #"k_" "") (clj-str/replace #"_" "-"))))]}
  (u tapahtumayhteys (str "LISTEN " kaytettava-kanava)))

(defn tapahtuman-data-ok?!
  [{:keys [payload palvelin hash lahetetty-data]} tapahtuma]
  (if-not (= hash (konv/sha256 payload))
    (do (log/error (str "Saatiin tapahtumasta " tapahtuma " erillainen data kuin lähetettiin."))
        (when dev-tyokalut/dev-environment?
          (log/info (str "[KOMPONENTTI-EVENT] Saatu payload: " payload))
          (log/info (str "[KOMPONENTTI-EVENT] palvelin: " palvelin))
          (log/info (str "[KOMPONENTTI-EVENT] Lahetetyn datan tiedot: " lahetetty-data))
          (log/info (str "[KOMPONENTTI-EVENT] Saadun datan tiedot: " (dev-tyokalut/datan-tiedot payload {:type-string? true})))
          #_(dev-tyokalut/etsi-epakohta-datasta payload lahetetty-data (dev-tyokalut/datan-tiedot payload {:type-string? true})))
        false)
    true))

(defn paluuarvo [palautettava-arvo tapahtuma db]
  (let [json-data (case palautettava-arvo
                    :viimeisin (q-tapahtumat/uusin-arvo db {:nimi tapahtuma})
                    :viimeisin-per-palvelin (q-tapahtumat/uusin-arvo-per-palvelin db {:nimi tapahtuma})
                    nil)]
    (when json-data
      (let [data (transit/lue-transit-string json-data transit-read-handler)]
        (when (tapahtuman-data-ok?! data tapahtuma)
          data)))))

(defn- kuuntele-klusterin-tapahtumaa!
  [{:keys [db tapahtuma-loopin-ajot kuuntelu-aloitettu-broadcast kuuntelu-aloitettu-kuittaus
           tapahtuma palautettava-arvo kuuntelun-jalkeen ryhmatunnistin lkm]}]
  {:pre [(not (nil? db))
         (not (nil? tapahtuma))]}
  (thread (let [possukanava (kaytettava-kanava! db tapahtuma)]
            (if (= ::virhe possukanava)
              [::virhe nil]
              (let [kuuntelu-aloitettu-tunnistin (str (gensym "kuuntelu"))
                    kuuntelu-aloitettu-huomauttaja (async/chan)]
                (println "--- kuuntele-klusterin-tapahtumaa! 1 ---")
                (println "---> KÄYTETTÄVÄ KANAVA: " possukanava)
                (async/sub kuuntelu-aloitettu-broadcast kuuntelu-aloitettu-tunnistin kuuntelu-aloitettu-huomauttaja)
                (async/put! tapahtuma-loopin-ajot {:f (fn [db]
                                                        (jdbc/with-db-transaction [db db]
                                                                                  (kuuntelu-fn possukanava (jdbc/get-connection db))
                                                                                  (paluuarvo palautettava-arvo tapahtuma db)))
                                                   :tunnistin kuuntelu-aloitettu-tunnistin
                                                   :ryhmatunnistin ryhmatunnistin
                                                   :yhta-aikaa? (boolean ryhmatunnistin)
                                                   :lkm lkm})
                (let [{::keys [paluuarvo]} (async/<!! kuuntelu-aloitettu-huomauttaja)]
                  (println "--- kuuntele-klusterin-tapahtumaa! 2 ---")
                  (println "---> paluuarvo: " paluuarvo)
                  (kuuntelun-jalkeen possukanava paluuarvo)
                  (async/put! kuuntelu-aloitettu-kuittaus true)
                  [possukanava paluuarvo]))))))

(defn- tapahtuman-nimi [kw]
  (-> kw
      name
      (.replace "-" "_")
      (.replace "!" "")
      (.replace "?" "")
      (.replace "<" "")
      (.replace ">" "")))

(defn- julkaistavan-datan-muunnos
  "Käytetään kerran transition koneiston läpi, jotta laskettu hash olisi aina sama, samalle datalle"
  [data]
  (let [muunnos (transit/lue-transit-string (transit/clj->transit data
                                                                  {})
                                            {})]
    (if (= muunnos data)
      muunnos
      (do (log/error "Datan muuttaminen julkaisua varten ei onnistunut!")
          ::muunnos-epaonnistui))))

(defn pura-tapahtuma-loopin-ajot! [db tapahtuma-loopin-ajot kuuntelu-aloitettu-kuittaus kuuntelu-aloitettu yhta-aikaa-ajettavat]
  (println "--- pura-tapahtuma-loopin-ajot! 1 ----")
  (try (let [ajot (loop [ajo (async/poll! tapahtuma-loopin-ajot)
                         ajot []]
                    (if (nil? ajo)
                      ajot
                      (recur (async/poll! tapahtuma-loopin-ajot)
                             (conj ajot ajo))))
             ajojen-lkm (volatile! 0)]
         (println (str "---> ajot: " ajot))
         (doseq [{:keys [f tunnistin ryhmatunnistin yhta-aikaa? lkm]} ajot]
           (when ryhmatunnistin
             (println "### RYHMÄTUNNISTIN ##")
             (println "(get @yhta-aikaa-ajettavat ryhmatunnistin): " (get @yhta-aikaa-ajettavat ryhmatunnistin)))
           (cond
             (not (and f tunnistin)) (log/error (str "Tapahtuma looppiin annettu käsky ilman tunnistinta tai funktiota. Tunnistin: " tunnistin " funktio: " f))
             (and yhta-aikaa?
                  (-> @yhta-aikaa-ajettavat (get ryhmatunnistin) count (= (dec lkm))))
             (jdbc/with-db-transaction [db db]
                                       (doseq [{:keys [f tunnistin]} (get @yhta-aikaa-ajettavat ryhmatunnistin)]
                                         (let [paluuarvo (f db)]
                                           (println "---> " {::kuuntelu tunnistin
                                                             ::paluuarvo paluuarvo})
                                           (async/put! kuuntelu-aloitettu {::kuuntelu tunnistin
                                                                           ::paluuarvo paluuarvo})
                                           (vswap! ajojen-lkm inc)))
                                       (let [paluuarvo (f db)]
                                         (println "---> " {::kuuntelu tunnistin
                                                           ::paluuarvo paluuarvo})
                                         (async/put! kuuntelu-aloitettu {::kuuntelu tunnistin
                                                                         ::paluuarvo paluuarvo})
                                         (vswap! ajojen-lkm inc)
                                         (swap! yhta-aikaa-ajettavat dissoc ryhmatunnistin)))
             yhta-aikaa?
             (swap! yhta-aikaa-ajettavat update ryhmatunnistin conj {:f f :tunnistin tunnistin})
             :else
             (when f
               (let [paluuarvo (f db)]
                 (when tunnistin
                   (async/put! kuuntelu-aloitettu {::kuuntelu tunnistin
                                                   ::paluuarvo paluuarvo}))
                 (vswap! ajojen-lkm inc))
               #_(when tunnistin
                   (async/put! kuuntelu-aloitettu {::kuuntelu tunnistin})))))
         (async/<!! (async/go-loop [kuittaukset []]
                      (when-not (= @ajojen-lkm (count kuittaukset))
                        (let [kuittaus (async/<! kuuntelu-aloitettu-kuittaus)]
                          (println "--- pura-tapahtuma-loopin-ajot! 2 ----")
                          (println (str "---> kuittaukset: " (conj kuittaukset kuittaus)))
                          (recur (conj kuittaukset kuittaus)))))))
       (catch Throwable t
         (log/error (str "Tapahtuma loopin ajot kaatui virheeseen: " (.getMessage t) "\nStack trace: " (.printStackTrace t))))))

(defn- tapahtuma-loop-sisalto [{:keys [ajossa db connection tapahtuma-loopin-ajot kuuntelu-aloitettu-kuittaus
                                       kuuntelu-aloitettu yhta-aikaa-ajettavat kuuntelijat tarkkailu-kanava]}]
  (pura-tapahtuma-loopin-ajot! db tapahtuma-loopin-ajot kuuntelu-aloitettu-kuittaus kuuntelu-aloitettu yhta-aikaa-ajettavat)
  ;(println "1")
  (let [tapahtumien-haku-onnistui? (try
                                     #_(println "2")
                                     (doseq [^PGNotification notification (seq (.getNotifications connection))]
                                       #_(println "3")
                                       (let [tapahtumadatan-id (Integer/parseInt (.getParameter notification))
                                             #_#__ (println "4")
                                             tapahtuman-nimi (.getName notification)
                                             #_#__ (println "5")
                                             json-data (-> (q-tapahtumat/tapahtuman-arvot db {:idt #{tapahtumadatan-id}}) first :arvo)
                                             #_#__ (println "6")
                                             data (transit/lue-transit-string json-data transit-read-handler)]
                                         #_(println "7")
                                         (when (tapahtuman-data-ok?! data tapahtuman-nimi)
                                           #_(println "8")
                                           (doseq [kasittelija (get @kuuntelijat tapahtuman-nimi)]
                                             #_(println "--- kasittelija meta ---")
                                             #_(println "---> meta: " (meta kasittelija))
                                             ;; Käsittelijä ei sitten saa blockata
                                             (kasittelija data))
                                           #_(println "9")
                                           #_(println {::tapahtuma tapahtuman-nimi ::data data})
                                           (async/>!! tarkkailu-kanava {::tapahtuma tapahtuman-nimi ::data data})))
                                       #_(println "10"))
                                     #_(println "11")
                                     true
                                     (catch PSQLException e
                                       (log/debug "Tapahtumat-kuuntelijassa poikkeus, SQL state" (.getSQLState e))
                                       (log/error "Tapahtumat-kuuntelijassa tapahtui tietokantapoikkeus: " e)
                                       false))]
    #_(println "tapahtuman-haku-onnistui? " tapahtumien-haku-onnistui?)
    #_(println "-- @ajossa " @ajossa)
    #_(println "-- " (when @ajossa
                    (not tapahtumien-haku-onnistui?)))
    (when tapahtumien-haku-onnistui?
      (async/<!! (async/timeout 100)))
    #_(println "12")
    (when @ajossa
      (not tapahtumien-haku-onnistui?))))

(defn- tarkkailija-kuuntelun-jalkeen [tyyppi kuuntelija-kanava broadcast tarkkailijat]
  (fn [possu-kanava paluu-arvo]
    (when-not (nil? paluu-arvo)
      (case tyyppi
        :perus nil
        :viimeisin (async/put! kuuntelija-kanava {::data paluu-arvo})
        :viimeisin-per-palvelin (doseq [[_ arvo] paluu-arvo]
                                  (async/put! kuuntelija-kanava {::data arvo}))))
    (async/sub broadcast possu-kanava kuuntelija-kanava)
    (swap! tarkkailijat update possu-kanava conj kuuntelija-kanava)))

(defprotocol Kuuntele
  (kuuntele! [this tapahtuma callback])
  (tarkkaile! [this tapahtuma] [this tapahtuma tyyppi]))

(defprotocol Julkaise
  (julkaise! [this tapahtuma payload host-name]))

(defrecord Tapahtumat [kuuntelijat tarkkailijat ajossa]
  component/Lifecycle
  (start [this]
    (let [buffer-koko 10
          tapahtuma-loopin-ajot (async/chan buffer-koko)
          kuuntelu-aloitettu (async/chan)
          kuuntelu-aloitettu-broadcast (async/pub kuuntelu-aloitettu ::kuuntelu)
          kuuntelu-aloitettu-kuittaus (async/chan buffer-koko)
          tarkkailu-kanava (async/chan (async/sliding-buffer 1000)
                                       (map (fn [arvo]
                                              (log/debug (str "[KOMPONENTTI-EVENT] Lähetetään tiedot perus-broadcast jonosta\n"
                                                              "  tiedot: " arvo))
                                              arvo))
                                       (fn [t]
                                         (log/error t "perus-broadcast jonossa tapahtui virhe")))
          broadcast (async/pub tarkkailu-kanava ::tapahtuma (fn [topic] (async/sliding-buffer 1000)))
          this (assoc this ::tarkkailu-kanava tarkkailu-kanava
                      ::broadcast broadcast
                           ::kuuntelu-aloitettu-broadcast kuuntelu-aloitettu-broadcast
                           ::tapahtuma-loopin-ajot tapahtuma-loopin-ajot
                           ::kuuntelu-aloitettu kuuntelu-aloitettu
                           ::kuuntelu-aloitettu-kuittaus kuuntelu-aloitettu-kuittaus)
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
                                                                       (println "Yhteys olio....")
                                                                       (async/put! kuuntelu-valmis true)
                                                                       (let [connection (cast (Class/forName "org.postgresql.jdbc.PgConnection")
                                                                                              (jdbc/db-connection db))
                                                                             yhta-aikaa-ajettavat (atom {})]
                                                                         (when kaatui-virheeseen?
                                                                           (doseq [{:keys [kanava]} (q-tapahtumat/kaikki-kanavat db)]
                                                                             (println "kanava: " kanava)
                                                                             (kuuntelu-fn kanava connection))
                                                                           (reset! tapahtuma-loop-kaynnissa? true))
                                                                         (println "Kaikkea pitäs taasen kuunnella..")
                                                                         (loop []
                                                                           (if (or (not @ajossa)
                                                                                   (tapahtuma-loop-sisalto {:ajossa ajossa :db db :connection connection :tapahtuma-loopin-ajot tapahtuma-loopin-ajot
                                                                                                            :kuuntelu-aloitettu-kuittaus kuuntelu-aloitettu-kuittaus
                                                                                                            :kuuntelu-aloitettu kuuntelu-aloitettu :yhta-aikaa-ajettavat yhta-aikaa-ajettavat
                                                                                                            :kuuntelijat kuuntelijat :tarkkailu-kanava tarkkailu-kanava}))
                                                                             true
                                                                             (recur)))))
                                              (catch Throwable t
                                                (log/error "Tapahtuma loop kaatui: " (.getMessage t) ".\nStack: " (.printStackTrace t))
                                                true))]
                  (reset! tapahtuma-loop-kaynnissa? false)
                  (println "kaatui-virheeseen?: " kaatui-virheeseen?)
                  (println "@ajossa " @ajossa)
                  (println "timeout-arvo" timeout-arvo)
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
                  (run! #(u tapahtumayhteys (str "UNLISTEN " %))
                        (map first @kuuntelijat))
                  (doseq [[pos-kanava async-kanava] @tarkkailijat]
                    (u tapahtumayhteys (str "UNLISTEN " pos-kanava))
                    (async/close! async-kanava))))
    (reset! tapahtuma-loop-kaynnissa? false)
    (reset! ajossa false)
    (async/close! (::tarkkailu-kanava this))
    (async/close! (::tapahtuma-loopin-ajot this))
    (async/close! (::kuuntelu-aloitettu this))
    (async/unsub-all (::kuuntelu-aloitettu-broadcast this))
    (async/close! (::kuuntelu-aloitettu-kuittaus this))
    this)

  Kuuntele
  ;; Kuutele! ja tarkkaile! ero on se, että eventin sattuessa kuuntele! kutsuu callback funktiota kun taas
  ;; tarkkaile! lisää eventin async/chan:iin, joka palautetaan kutsujalle.
  (kuuntele! [this tapahtuma callback]
    (when-not (ifn? callback)
      (throw (IllegalArgumentException. "Tapahtuman kuuntelija callbackin pitää toteuttaa IFn protokolla")))
    (let [arityjen-maara (tyokalut/arityt callback)]
      (println "ARITYJEN MÄÄRÄT: " arityjen-maara)
      (when-not (contains? arityjen-maara 1)
        (throw (ArityException. (first arityjen-maara) "Callback funktio tapahtumalle pitää sisältää ainakin arity 1"))))
    (let [tapahtuma (tapahtuman-nimi tapahtuma)
          fn-tunnistin (str (gensym "callback"))
          kuuntelun-jalkeen (fn [possukanava _]
                              (swap! kuuntelijat update possukanava conj (with-meta callback {:tunnistin fn-tunnistin})))
          _ (println "---- kuuntele! 1 ----")
          _ (println "---> KUUNNELLAAN TAPAHTUMAA: " tapahtuma)
          kuuntelu-sekvenssi (kuuntele-klusterin-tapahtumaa! {:db (get-in this [:db :db-spec])
                                                              :kuuntelu-aloitettu-broadcast (::kuuntelu-aloitettu-broadcast this)
                                                              :tapahtuma-loopin-ajot (::tapahtuma-loopin-ajot this)
                                                              :kuuntelu-aloitettu-kuittaus (::kuuntelu-aloitettu-kuittaus this)
                                                              :tapahtuma tapahtuma
                                                              :kuuntelun-jalkeen kuuntelun-jalkeen})
          [possukanava _] (async/<!! kuuntelu-sekvenssi)]
      (println "---- kuuntele! 2 ----")
      (println "---> kuuntelu-sekvenssi done")
      (if (= possukanava ::virhe)
        false
        (fn []
          (println "---- lopetus-fn ---")
          (swap! kuuntelijat
                 update
                 possukanava
                 (fn [kanavan-callbackit]
                   (keep #(when-not (= fn-tunnistin (-> % meta :tunnistin))
                            %)
                         kanavan-callbackit)))))))
  (tarkkaile! [this tapahtuma tyyppi]
    (let [{:keys [tunnistin lkm]} *tarkkaile-yhta-aikaa*]
      (when (and *tarkkaile-yhta-aikaa*
                 (or (not (string? tunnistin))
                     (not (integer? lkm))))
        (throw (IllegalArgumentException. (str "Yhtäaikaa alkavien tarkkailujen pitää antaa string tunnistin "
                                               "ja lukumäärä, että moniko on aloittamassa yhtäaikaa"))))
      (thread (let [kuuntelija-kanava (async/chan 1000
                                                  (map (fn [v]
                                                         (log/debug (str "[KOMPONENTTI-EVENT] Saatiin tiedot\n"
                                                                         "  event: " tapahtuma "\n"
                                                                         "  tiedot: " v))
                                                         (select-keys (::data v) #{:payload :palvelin})))
                                                  (fn [t]
                                                    (log/error t (str "Kuuntelija kanavassa error eventille " tapahtuma))))
                    tapahtuma (tapahtuman-nimi tapahtuma)
                    kuuntelun-jalkeen (tarkkailija-kuuntelun-jalkeen tyyppi kuuntelija-kanava (::broadcast this) (:tarkkailijat this))
                    kuuntelu-sekvenssi (kuuntele-klusterin-tapahtumaa! {:db (get-in this [:db :db-spec])
                                                                        :kuuntelu-aloitettu-broadcast (::kuuntelu-aloitettu-broadcast this)
                                                                        :tapahtuma-loopin-ajot (::tapahtuma-loopin-ajot this)
                                                                        :kuuntelu-aloitettu-kuittaus (::kuuntelu-aloitettu-kuittaus this)
                                                                        :tapahtuma tapahtuma
                                                                        :palautettava-arvo (if (= :perus tyyppi)
                                                                                             nil
                                                                                             tyyppi)
                                                                        :kuuntelun-jalkeen kuuntelun-jalkeen
                                                                        :ryhmatunnistin tunnistin
                                                                        :lkm lkm})
                    [possukanava _] (async/<!! kuuntelu-sekvenssi)]
                (println (str "KUUNTELUSEKVENSSI DONE: " possukanava))
                (if (= ::virhe possukanava)
                  false
                  kuuntelija-kanava)))))
  (tarkkaile! [this tapahtuma]
    (tarkkaile! this tapahtuma :perus))

  Julkaise
  (julkaise! [this tapahtuma payload host-name]
    (let [tapahtuma (tapahtuman-nimi tapahtuma)
          db (get-in this [:db :db-spec])
          kanava (kaytettava-kanava! db tapahtuma)
          muunnettu-payload (julkaistavan-datan-muunnos payload)]
      (if (or (= ::muunnos-epaonnistui muunnettu-payload)
              (= ::virhe kanava))
        false
        (let [julkaistava-data (merge {:payload muunnettu-payload
                                       :palvelin host-name
                                       :hash (konv/sha256 muunnettu-payload)}
                                      (when dev-tyokalut/dev-environment?
                                        {:lahetetty-data (dev-tyokalut/datan-tiedot muunnettu-payload {:type-string? true})}))
              julkaisu-onnistui? (q-tapahtumat/julkaise-tapahtuma db {:kanava kanava :data (transit/clj->transit julkaistava-data
                                                                                                                 transit-write-handler)})]
          (when-not julkaisu-onnistui?
            (log/error (str "Tapahtuman " tapahtuma " julkaisu epäonnistui datalle:\n" muunnettu-payload)))
          julkaisu-onnistui?)))))

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

(defn sammuta! []
  (alter-var-root #'harja-tarkkailija (fn [s]
                                        (component/stop s)
                                        nil)))