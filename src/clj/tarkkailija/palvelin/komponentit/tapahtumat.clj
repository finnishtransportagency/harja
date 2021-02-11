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
            [harja.tyokalut.predikaatti :as predikaatti]

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
         (throw e))
       (catch Throwable t
         (log/error (str "Yritettiin saada possukanavaa tapahtumalle: " tapahtuma ", mutta se epäonnistui.\n"
                         "Virhe: " (.getMessage t) ".\nStack: " (.printStackTrace t)))
         (throw t))))

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
                     {::data (tapahtuman-tiedot-clj-dataksi (first tapahtuman-tiedot))
                      ::tapahtumadatan-id (:id (first tapahtuman-tiedot))})
        (:palvelimet-viimeisin :viimeisin-per-palvelin) (when (every? (fn [tapahtuman-tiedot]
                                                                        (tapahtuman-data-ok?! tapahtuman-tiedot tapahtuma))
                                                                      tapahtuman-tiedot)
                                                          (mapv (fn [tapahtuman-tiedot]
                                                                  {::data (with-meta (tapahtuman-tiedot-clj-dataksi tapahtuman-tiedot)
                                                                                     {:alustus? true})
                                                                   ::tapahtumadatan-id (:id tapahtuman-tiedot)})
                                                                tapahtuman-tiedot))))))

(defn- kasittele-viestintakanava! [viestintakanava]
  (let [[arvo _] (async/alts!! [viestintakanava
                                (async/timeout (* 1000 60))])
        arvo (if (nil? arvo)
               ;; Timeout
               (if (async/offer! viestintakanava ::timeout)
                 (async/close! viestintakanava)
                 ;; kanavassa onkin jotain
                 (async/poll! viestintakanava))
               arvo)]
    (if-not (nil? arvo)
      (loop [arvo arvo
             paluuarvo nil]
        (cond
          (= arvo ::aloitus) (recur (async/<!! viestintakanava)
                                    nil)
          (= arvo ::loppu) (do (async/close! viestintakanava)
                               paluuarvo)
          (contains? arvo ::virhe) (recur (async/<!! viestintakanava)
                                          arvo)))
      ::timeout)))

(defn- kuuntele-klusterin-tapahtumaa!
  [{:keys [db tapahtuma-loopin-ajot kuuntelija-kanava
           possukanava tapahtuma tyyppi ryhmatunnistin lkm]}]
  {:pre [(not (nil? db))
         (not (nil? tapahtuma))]}
  (let [viestintakanava (async/chan)]
    (async/put! tapahtuma-loopin-ajot {:viestintakanava viestintakanava
                                       :possukanava possukanava
                                       :tyyppi tyyppi
                                       :tapahtuma tapahtuma
                                       :kuuntelija-kanava kuuntelija-kanava
                                       :ryhmatunnistin ryhmatunnistin
                                       :yhta-aikaa? (boolean ryhmatunnistin)
                                       :lkm lkm})
    (kasittele-viestintakanava! viestintakanava)))

(defn- tapahtuman-nimi [kw]
  (-> kw
      name
      (.replace "-" "_")
      (.replace "!" "")
      (.replace "?" "")
      (.replace "<" "")
      (.replace ">" "")))

(defn pura-tapahtuma-loopin-ajot! [db tapahtuma-loopin-ajot yhta-aikaa-ajettavat]
  (let [ajot (loop [ajo (async/poll! tapahtuma-loopin-ajot)
                    ajot []]
               (if (nil? ajo)
                 ajot
                 (recur (async/poll! tapahtuma-loopin-ajot)
                        (conj ajot ajo))))]
    (reduce (fn [paluuarvot {:keys [possukanava viestintakanava tyyppi tapahtuma ryhmatunnistin yhta-aikaa? lkm] :as ajo}]
              (let [f (fn [db]
                        (try (if (async/offer! viestintakanava ::aloitus)
                               (do (kuuntelu-fn possukanava (jdbc/get-connection db))
                                   nil)
                               {:type ::tapahtumavirhe
                                :virhe [{:koodi ::viestintakanava-timeout
                                         :paikka ::tapahtumaloop}]})
                             (catch Throwable t
                               (log/error "Kuuntelun aloittaminen epäonnistui: " (.getMessage t))
                               (.printStackTrace t)
                               {:type ::tapahtumavirhe
                                :virhe [{:koodi ::kuuntelun-aloittaminen-epaonnistui
                                         :paikka ::tapahtumaloop}]})))
                    yhta-aikaa-ajettavat-kasassa? (and ryhmatunnistin (-> @yhta-aikaa-ajettavat (get ryhmatunnistin) count (= (dec (or lkm 0)))))]
                (vec (concat paluuarvot
                             (cond
                               (and yhta-aikaa?
                                    yhta-aikaa-ajettavat-kasassa?)
                               (jdbc/with-db-transaction [db db]
                                 (let [paluuarvot (mapv (fn [{:keys [f] :as ajo}]
                                                          (if-let [virhe (f db)]
                                                            (merge {::virhe virhe} ajo)
                                                            ajo))
                                                        (conj (get @yhta-aikaa-ajettavat ryhmatunnistin)
                                                              (merge {:f f}
                                                                     ajo)))]
                                   (swap! yhta-aikaa-ajettavat dissoc ryhmatunnistin)
                                   paluuarvot))
                               yhta-aikaa?
                               (do (swap! yhta-aikaa-ajettavat update ryhmatunnistin conj (merge {:f f}
                                                                                                 ajo))
                                   [])

                               :else
                               [(if-let [virhe (f db)]
                                  (merge {::virhe virhe} ajo)
                                  ajo)])))))
            []
            ajot)))

(defn- laheta-viesti-virheryhmien-viestintakanavaan! [virheryhmat]
  (doseq [virheryhma virheryhmat
          :let [virhe (some #(::virhe %) virheryhma)]]
    (doseq [{:keys [viestintakanava]} virheryhma]
      (when viestintakanava
        (async/put! viestintakanava {::virhe virhe})))))

(defn- aloita-tapahtumaloop-kuuntelu!
  [broadcast possu-kanava kuuntelija-kanava]
  {:pre [(satisfies? async/Pub broadcast)
         (uuid? (UUID/fromString possu-kanava))
         (predikaatti/chan? kuuntelija-kanava)]}
  (async/sub broadcast possu-kanava kuuntelija-kanava))

(defn- julkaise-tapahtumat! [tapahtumat kuuntelijat tarkkailu-kanava]
  (doseq [{:keys [possukanava tiedot tapahtumadatan-id]} tapahtumat]
    (doseq [kasittelija (get @kuuntelijat possukanava)]
      ;; Käsittelijä ei sitten saa blockata
      (try (kasittelija tiedot)
           (catch Throwable t
             (log/error "Käsittelijässä tapahtui poikkeus: " (.getMessage t))
             (.printStackTrace t))))
    (async/>!! tarkkailu-kanava {::tapahtuma possukanava ::data tiedot ::tapahtumadatan-id tapahtumadatan-id})))

(defn- laheta-viimeisimmat-arvot! [ok-ajot-possukanavittain db]
  (doseq [[possukanava ajot] ok-ajot-possukanavittain]
    (doseq [{:keys [tyyppi tapahtuma kuuntelija-kanava] :as ajo} ajot]
      (let [paluuarvo (paluuarvo tyyppi tapahtuma db)]
        (when-not (nil? paluuarvo)
          (case tyyppi
            (:palvelimet-perus :perus) nil
            :viimeisin (async/put! kuuntelija-kanava paluuarvo)
            (:viimeisin-per-palvelin :palvelimet-viimeisin) (doseq [arvo paluuarvo]
                                                              (async/put! kuuntelija-kanava arvo))))))))


(defn- tapahtuma-loop-sisalto [{:keys [ajossa db connection tapahtuma-loopin-ajot yhta-aikaa-ajettavat
                                       kuuntelijat tarkkailu-kanava loop-odotus broadcast]}]
  (let [ajot (pura-tapahtuma-loopin-ajot! db tapahtuma-loopin-ajot yhta-aikaa-ajettavat)
        ajoryhmat-ryhmittain (group-by :ryhmatunnistin ajot)
        ajot-ryhmittain (concat (vals (dissoc ajoryhmat-ryhmittain nil))
                                (map (fn [ajo]
                                       [ajo])
                                     (get ajoryhmat-ryhmittain nil)))
        [virheryhmat ok-ryhmat] (reduce (fn [[virheryhmat ok-ryhmat] ryhma]
                                          (if (some #(::virhe %)
                                                    ryhma)
                                            [(conj virheryhmat ryhma) ok-ryhmat]
                                            [virheryhmat (conj ok-ryhmat ryhma)]))
                                        [#{} #{}]
                                        ajot-ryhmittain)]
    (laheta-viesti-virheryhmien-viestintakanavaan! virheryhmat)
    (let [ok-ajot-possukanavittain (group-by :possukanava (apply concat ok-ryhmat))
          tapahtumien-haku-onnistui? (try
                                       (let [kierroksen-tapahtumat (sort-by :tapahtumadatan-id
                                                                            (keep (fn [^PGNotification notifikaatio]
                                                                                    (let [tapahtumadatan-id (Integer/parseInt (.getParameter notifikaatio))
                                                                                          tapahtuman-nimi (.getName notifikaatio)
                                                                                          tapahtuman-tiedot (try (-> (q-tapahtumat/hae-tapahtuman-tiedot db {:idt #{tapahtumadatan-id}}) first)
                                                                                                                 (catch PSQLException e
                                                                                                                   (log/error "Virhe tapahtuman tietojen haussa: " (.getMessage e))
                                                                                                                   (.printStackTrace e)
                                                                                                                   nil))]
                                                                                      (when (tapahtuman-data-ok?! tapahtuman-tiedot tapahtuman-nimi)
                                                                                        (let [data (tapahtuman-tiedot-clj-dataksi tapahtuman-tiedot)]
                                                                                          {:possukanava tapahtuman-nimi
                                                                                           :tapahtumadatan-id tapahtumadatan-id
                                                                                           :tiedot data}))))
                                                                                  (seq (.getNotifications connection))))]
                                         (doseq [[possukanava ajot] ok-ajot-possukanavittain]
                                           (doseq [{:keys [kuuntelija-kanava]} ajot]
                                             (when kuuntelija-kanava
                                               (aloita-tapahtumaloop-kuuntelu! broadcast possukanava kuuntelija-kanava))))
                                         (julkaise-tapahtumat! kierroksen-tapahtumat kuuntelijat tarkkailu-kanava)
                                         (laheta-viimeisimmat-arvot! (apply dissoc ok-ajot-possukanavittain (distinct (map :possukanava kierroksen-tapahtumat)))
                                                                     db))
                                       true
                                       (catch PSQLException e
                                         (log/debug "Tapahtumat-kuuntelijassa poikkeus, SQL state" (.getSQLState e))
                                         (log/error "Tapahtumat-kuuntelijassa tapahtui tietokantapoikkeus: " e)
                                         false))]
      (doseq [{:keys [viestintakanava]} ajot]
        (when viestintakanava
          (async/put! viestintakanava ::loppu)))
      (when tapahtumien-haku-onnistui?
        (async/<!! (async/timeout (or loop-odotus 100))))
      (when @ajossa
        (not tapahtumien-haku-onnistui?)))))

(defn poista-vanhat-tapahtumat
  "Halutaan, että tapahtumat tulevat oikeassa järjestyksessä kanavaan. Kun käytetään
  'viimeisin' tyyppisiä tarkkailijoita, on mahdollista, että kanavaan yritetään alussa tunkea
  tavaraa väärässä järjestykseeä. Tämä johtuu kakutuksesta, joten otetaan vaan nuo vanhat/dublikaatti
  arvot pois."
  [xf]
  (let [edellinen-id (volatile! 0)]
    (fn
      ([] (xf))
      ([tulos] (xf tulos))
      ([tulos {::keys [tapahtumadatan-id data] :as td}]
       (let [id @edellinen-id]
         (cond
           (< id tapahtumadatan-id) (do
             (vreset! edellinen-id tapahtumadatan-id)
             (xf tulos td))
           (-> data meta :alustus?) (xf tulos td)
           :else tulos))))))

(defonce start-lukko (Object.))

(defrecord Tapahtumat [asetukset kuuntelijat tarkkailijat ajossa]
  component/Lifecycle
  (start [this]
    (locking start-lukko
      (when-not (::tapahtuma-loop this)
        (let [buffer-koko 10
              tapahtuma-loopin-ajot (async/chan buffer-koko)
              tarkkailu-kanava (async/chan (async/sliding-buffer 1000)
                                           (map (fn [arvo]
                                                  (log/debug (str "[KOMPONENTTI-EVENT] Lähetetään tiedot broadcast jonosta\n"
                                                                  "  tiedot: " arvo))
                                                  arvo))
                                           (fn [t]
                                             (log/error t "broadcast jonossa tapahtui virhe")))
              broadcast (async/pub tarkkailu-kanava ::tapahtuma (fn [topic] (async/sliding-buffer 1000)))
              tapahtuma-loop-kaynnissa? (atom false)
              this (assoc this ::tarkkailu-kanava tarkkailu-kanava
                               ::tapahtuma-loop-kaynnissa? tapahtuma-loop-kaynnissa?
                               ::broadcast broadcast
                               ::tapahtuma-loopin-ajot tapahtuma-loopin-ajot
                               ::tarkkailija->tapahtuma (atom {}))
              kuuntelu-valmis (async/chan)
              _ (reset! tarkkailijat {})
              _ (reset! kuuntelijat {})
              _ (reset! ajossa true)
              ;; kuuntelijat-mapin avaimina on notifikaatiojonon id, esim "sonjaping" tai "urakan_123_tapahtumat".
              ;; arvona kullakin avaimella on seq async-kanavia (?)
              tapahtuma-loop (thread (loop [kaatui-virheeseen? false
                                            timeout-arvo 0]
                                       (let [kaatui-virheeseen? (try (jdbc/with-db-connection [db (get-in this [:db :db-spec])]
                                                                                              (when-not (predikaatti/chan-closed? kuuntelu-valmis)
                                                                                                (async/put! kuuntelu-valmis true))
                                                                                              (let [connection (cast (Class/forName "org.postgresql.jdbc.PgConnection")
                                                                                                                     (jdbc/db-connection db))
                                                                                                    yhta-aikaa-ajettavat (atom {})]
                                                                                                (when kaatui-virheeseen?
                                                                                                  (doseq [{:keys [kanava]} (q-tapahtumat/hae-kaikki-kanavat db)]
                                                                                                    (kuuntelu-fn kanava connection)))
                                                                                                (reset! tapahtuma-loop-kaynnissa? true)
                                                                                                (loop []
                                                                                                  (if (or (not @ajossa)
                                                                                                          (tapahtuma-loop-sisalto {:ajossa ajossa :db db :connection connection :tapahtuma-loopin-ajot tapahtuma-loopin-ajot
                                                                                                                                   :loop-odotus (get-in asetukset [:tarkkailija :loop-odotus])
                                                                                                                                   :yhta-aikaa-ajettavat yhta-aikaa-ajettavat :broadcast broadcast
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
                                                                                (max 500 timeout-arvo)))))))))]
          (async/<!! kuuntelu-valmis)
          (async/close! kuuntelu-valmis)
          (assoc this ::tapahtuma-loop tapahtuma-loop)))))

  (stop [this]
    (let [kaynnissa-oleva-kanava? (fn [x]
                                    (and (predikaatti/chan? x)
                                         (not (predikaatti/chan-closed? x))))]
      (when (kaynnissa-oleva-kanava? (::tapahtuma-loopin-ajot this))
        (async/put! (::tapahtuma-loopin-ajot this)
                    (fn [tapahtumayhteys]
                      (run! #(u tapahtumayhteys (str "UNLISTEN \"" % "\""))
                            (map first @kuuntelijat))
                      (doseq [[pos-kanava async-kanava] @tarkkailijat]
                        (u tapahtumayhteys (str "UNLISTEN \"" pos-kanava "\""))
                        (async/close! async-kanava)))))
      (when (::tapahtuma-loop-kaynnissa? this)
        (reset! (::tapahtuma-loop-kaynnissa? this) false))
      (reset! ajossa false)
      (when (kaynnissa-oleva-kanava? (::tapahtuma-loop this))
        (async/<!! (::tapahtuma-loop this)))
      (when (::tarkkailija->tapahtuma this)
        (doseq [[tarkkailija _] @(::tarkkailija->tapahtuma this)]
          (p/lopeta-tarkkailu! this tarkkailija)))
      (when (::broadcast this)
        (async/unsub-all (::broadcast this)))
      (when (kaynnissa-oleva-kanava? (::tarkkailu-kanava this))
        (async/close! (::tarkkailu-kanava this)))
      (when (kaynnissa-oleva-kanava? (::tapahtuma-loopin-ajot this))
        (async/close! (::tapahtuma-loopin-ajot this)))
      (dissoc this ::tapahtuma-loop ::broadcast ::tarkkailija->tapahtuma ::tarkkailu-kanava
              ::tapahtuma-loopin-ajot ::tapahtuma-loop-kaynnissa?)))

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
    (try
      (let [tapahtuma (tapahtuman-nimi tapahtuma)
            fn-tunnistin (str (gensym "callback"))
            db (get-in this [:db :db-spec])
            possukanava (kaytettava-kanava! db tapahtuma)
            _ (swap! kuuntelijat update possukanava conj (with-meta callback {:tunnistin fn-tunnistin}))
            kuuntelun-aloituksen-vastaus (kuuntele-klusterin-tapahtumaa! {:db (get-in this [:db :db-spec])
                                                                          :tapahtuma-loopin-ajot (::tapahtuma-loopin-ajot this)
                                                                          :possukanava possukanava
                                                                          :tapahtuma tapahtuma})]
        (cond
          (or
            ;; timeout
            (= ::timeout kuuntelun-aloituksen-vastaus)
            ;; virhe
            (contains? kuuntelun-aloituksen-vastaus ::virhe))
          false
          ;; kaik män kivasti
          (nil? kuuntelun-aloituksen-vastaus)
          (fn []
            (swap! kuuntelijat
                   update
                   possukanava
                   (fn [kanavan-callbackit]
                     (keep #(when-not (= fn-tunnistin (-> % meta :tunnistin))
                              %)
                           kanavan-callbackit)))
            true)))
      (catch Throwable t
        (log/error "Virhe tapahtuman kuuntelussa: " (.getMessage t))
        (.printStackTrace t)
        false)))
  (tarkkaile! [this tapahtuma tyyppi]
    (let [{:keys [tunnistin lkm]} *tarkkaile-yhta-aikaa*]
      (when (and *tarkkaile-yhta-aikaa*
                 (or (not (string? tunnistin))
                     (not (integer? lkm))))
        (throw (IllegalArgumentException. (str "Yhtäaikaa alkavien tarkkailujen pitää antaa string tunnistin "
                                               "ja lukumäärä, että moniko on aloittamassa yhtäaikaa. "
                                               "Nyt saatiin tyypeiksi - tunnistin: " (type tunnistin)
                                               " lkm: " (type lkm)))))
      (thread (try (let [normalisoitu-tyyppi (if (keyword? tyyppi)
                                               {tyyppi nil}
                                               tyyppi)
                         [tyyppi tyypin-asetukset] (first normalisoitu-tyyppi)
                         kuuntelijakanava-xf (cond-> (comp poista-vanhat-tapahtumat
                                                           (map (fn [v]
                                                                  (select-keys (::data v) #{:payload :palvelin :aika}))))
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
                         db (get-in this [:db :db-spec])
                         possukanava (kaytettava-kanava! db tapahtuma)
                         _ (swap! tarkkailijat update possukanava conj kuuntelija-kanava)
                         kuuntelun-aloituksen-vastaus (kuuntele-klusterin-tapahtumaa! {:db db
                                                                                       :tapahtuma-loopin-ajot (::tapahtuma-loopin-ajot this)
                                                                                       :kuuntelija-kanava kuuntelija-kanava
                                                                                       :possukanava possukanava
                                                                                       :tapahtuma tapahtuma
                                                                                       :tyyppi tyyppi
                                                                                       :ryhmatunnistin tunnistin
                                                                                       :lkm lkm})]
                     (cond
                       (or
                         ;; timeout
                         (= ::timeout kuuntelun-aloituksen-vastaus)
                         ;; virhe
                         (contains? kuuntelun-aloituksen-vastaus ::virhe))
                       (do
                         (async/close! kuuntelija-kanava)
                         nil)
                       ;; kaik män kivasti
                       (nil? kuuntelun-aloituksen-vastaus)
                       (do
                         (swap! (::tarkkailija->tapahtuma this) merge {kuuntelija-kanava possukanava})
                         kuuntelija-kanava)))
                   (catch Throwable t
                     nil)))))
  (tarkkaile! [this tapahtuma]
    (p/tarkkaile! this tapahtuma :perus))

  p/Kuuroudu
  (lopeta-tarkkailu! [this kuuntelija]
    (let [broadcast (::broadcast this)
          tapahtuma (get @(::tarkkailija->tapahtuma this) kuuntelija)]
      (when (and broadcast tapahtuma))
      (async/unsub broadcast tapahtuma kuuntelija))
    (swap! (::tarkkailija->tapahtuma this) dissoc kuuntelija)
    nil)

  p/Julkaise
  (julkaise! [this tapahtuma payload host-name]
    (try (let [tapahtuma (tapahtuman-nimi tapahtuma)
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
               julkaisu-onnistui?)))
         (catch Throwable t
           false))))

(defn luo-tapahtumat [asetukset]
  (->Tapahtumat asetukset (atom nil) (atom nil) (atom false)))