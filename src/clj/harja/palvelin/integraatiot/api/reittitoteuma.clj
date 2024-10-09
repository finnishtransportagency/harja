(ns harja.palvelin.integraatiot.api.reittitoteuma
  "Reittitoteuman kirjaaminen urakalle"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET DELETE]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu tee-kirjausvastauksen-body]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.kyselyt.materiaalit :as materiaalit]
            [harja.kyselyt.toteumat :as toteumat-q]
            [harja.palvelin.integraatiot.api.toteuma :as api-toteuma]
            [harja.palvelin.integraatiot.api.tyokalut.json
             :refer [aika-string->java-sql-date aika-string->java-util-date
                     pvm-string->joda-date aika-string->java-sql-timestamp]]
            [harja.kyselyt.tieverkko :as tieverkko]
            [harja.kyselyt.sopimukset :as sopimukset-q]
            [harja.kyselyt.konversio :as konversio]
            [harja.kyselyt.toimenpidekoodit :as toimenpidekoodit-q]
            [clojure.java.jdbc :as jdbc]
            [harja.geo :as geo]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.palvelin.integraatiot.api.validointi.toteumat :as toteuman-validointi]
            [clojure.string :as str]
            [clojure.core.async :as async]
            [harja.domain.reittipiste :as rp]
            [harja.pvm :as pvm])
  (:use [slingshot.slingshot :only [throw+]]))

(def ^{:const true} +yhdistamis-virhe+ :virhe-reitin-yhdistamisessa)

(def ^{:const true
       :doc "Etäisyys, jota lähempänä toisiaan olevat reittipisteet yhdistetään linnuntietä,
jos niille ei löydy yhteistä tietä tieverkolta."}
maksimi-linnuntien-etaisyys 200)

(def ^{:const true
       :doc "Toteuman oletusnopeusrajoitus, jos tehtäviä ei ole. Tehtäville määritellään kannassa nopeusrajoitus. Perustuu aiemmin kovakoodattuun arvoon."}
  toteuma-oletusnopeusrajoitus 108)

(def ^{:const true
       :doc "Toteuman reittipisteiden tallennuksen timeout 10 minuuttia"}
  reittipisteet-timeout (* 1000 60 10))

(defn yhdista-viivat [viivat]
  (if-not (empty? viivat)
    {:type :multiline
     :lines (mapcat
              (fn [viiva]
                (if (= :line (:type viiva))
                  (list viiva)
                  (:lines viiva)))
              viivat)}
    +yhdistamis-virhe+))

(defn- piste [{reittipiste :reittipiste}]
  [(get-in reittipiste [:koordinaatit :x])
   (get-in reittipiste [:koordinaatit :y])
   (:aika reittipiste)])

(def ^{:private true} piste-aika (juxt (comp :x :koordinaatit :reittipiste)
                                       (comp :y :koordinaatit :reittipiste)
                                       (comp :aika :reittipiste)))

(defn- valin-geometria
  ([reitti] (valin-geometria reitti maksimi-linnuntien-etaisyys))
  ([{:keys [alku loppu geometria] :as vali} maksimi-etaisyys]
   (or (and geometria (geo/pg->clj geometria))
       (let [[x1 y1 :as p1] (:coordinates (geo/pg->clj alku))
             [x2 y2 :as p2] (:coordinates (geo/pg->clj loppu))
             etaisyys (geo/etaisyys p1 p2)]
         (if (or (nil? maksimi-etaisyys) (< etaisyys maksimi-etaisyys))
           (do (log/debug "Reittitoteuman pisteillä"
                          " (x1:" x1 " y1: " y1
                          " & x2: " x2 " y2: " y2 " )"
                          " ei ole yhteistä tietä. Tehdään linnuntie, etäisyys: " etaisyys ", max: " maksimi-etaisyys)
               {:type :line
                :points [[x1 y1]
                         [x2 y2]]})
           (do (log/info "EI TEHDÄ linnuntietä, etäisyys: " etaisyys ", max: " maksimi-etaisyys)
               nil))))))

(defn hae-reitti
  ([db pisteet] (hae-reitti db maksimi-linnuntien-etaisyys pisteet))
  ([db maksimi-etaisyys pisteet] (hae-reitti db maksimi-etaisyys toteuma-oletusnopeusrajoitus pisteet))
  ([db maksimi-etaisyys nopeusrajoitus pisteet]
   (as-> pisteet p
         (map (fn [[x y aika]]
                (str "\"(" x "," y "," aika ")\"")) p)
         (str/join "," p)
         (str "{" p "}")
         (tieverkko/hae-tieviivat-pisteille-aika db p nopeusrajoitus)
         (keep #(valin-geometria % maksimi-etaisyys) p)
         (yhdista-viivat p))))

(defn luo-reitti-geometria [db reitti nopeusrajoitus]
  (let [reitti (->> reitti
                    (sort-by (comp :aika :reittipiste))
                    (map piste)
                    (hae-reitti db maksimi-linnuntien-etaisyys nopeusrajoitus))]
    (if (= reitti +yhdistamis-virhe+)
      +yhdistamis-virhe+
      (-> reitti
          geo/clj->pg
          geo/geometry))))

(defn paivita-toteuman-reitti
  "REPL testausta ja ajastettua tehtävää varten, laskee annetun toteuman reitin uudelleen reittipisteistä."
  ([db toteuma-id] (paivita-toteuman-reitti db toteuma-id maksimi-linnuntien-etaisyys))
  ([db toteuma-id maksimi-etaisyys]
   (let [reitti (->> toteuma-id
                     (toteumat-q/hae-toteuman-reittipisteet db)
                     (map (fn [{sijainti :sijainti aika :aika}]
                            [(.-x sijainti) (.-y sijainti) aika]))
                     (hae-reitti db maksimi-etaisyys))
         geometria (when-not (= reitti +yhdistamis-virhe+)
                     (-> reitti
                         geo/clj->pg
                         geo/geometry))]
     (if geometria
       (do
         (log/debug "Tallennetaan reitti toteumalle " toteuma-id)
         (toteumat-q/paivita-toteuman-reitti! db {:reitti geometria
                                                  :id toteuma-id}))

       (log/debug "Reittiä ei saatu kasattua toteumalle " toteuma-id)))))

(defn tee-onnistunut-vastaus []
  (tee-kirjausvastauksen-body {:ilmoitukset "Reittitoteuma kirjattu onnistuneesti"}))

(defn luo-reitti [db reitti toteuma-id]
  (log/debug "Luodaan uusi reittipiste")
  (toteumat-q/tallenna-toteuman-reittipisteet!
   db
    {::rp/toteuma-id toteuma-id
     ::rp/reittipisteet
     (for [{:keys [aika koordinaatit tehtavat materiaalit]} (map :reittipiste reitti)]
       (rp/reittipiste
         (aika-string->java-sql-date aika)
         koordinaatit
         (toteumat-q/pisteen-hoitoluokat db koordinaatit
           (map (comp :id :tehtava) tehtavat)
           (map :materiaali materiaalit))
         (for [t tehtavat]
           {::rp/toimenpidekoodi (get-in t [:tehtava :id])
            ::rp/maara (some-> (get-in t [:tehtava :maara :maara]) bigdec)})
         (for [m materiaalit]
           {::rp/materiaalikoodi (->> m :materiaali
                                   (api-toteuma/mat-apilta->mat-db)
                                   (materiaalit/hae-materiaalikoodin-id-nimella db)
                                   first :id)
            ::rp/maara (some-> (get-in m [:maara :maara]) bigdec)})))}))

(defn poista-toteuman-reitti [db toteuma-id]
  (log/debug "Poistetaan reittipisteet")
  ;; Poistetaan reittipistedata: pisteet, tehtävät ja materiaalit
  (toteumat-q/poista-reittipiste-toteuma-idlla! db toteuma-id))

(defn tallenna-yksittainen-reittitoteuma [db db-replica urakka-id kirjaaja {:keys [reitti toteuma tyokone]} jsonhash
                                          reittipisteet-tallennettu-chan]
  (let [toteuma (assoc toteuma
                  ;; Reitti liitetään lopuksi
                  :reitti nil)
        ;; Käytetään tehtävien nopeusrajoituksista pienintä geometrian muodostukseen
        tehtavat (:tehtavat toteuma)
        nopeusrajoitus (if (empty? tehtavat)
                         toteuma-oletusnopeusrajoitus
                         (apply min
                           (map #(toimenpidekoodit-q/hae-tehtavan-nopeusrajoitus db (get-in % [:tehtava :id]))
                             (:tehtavat toteuma))))
        toteuman-reitti (async/thread (luo-reitti-geometria db-replica reitti nopeusrajoitus))
        toteuma-id (jdbc/with-db-transaction [db db]
                     (let [toteuma-id (api-toteuma/paivita-tai-luo-uusi-toteuma db urakka-id kirjaaja toteuma tyokone)
                           _ (toteumat-q/lisaa-toteumalle-jsonhash! db {:id toteuma-id :hash jsonhash})]
                       (log/debug "Toteuman perustiedot tallennettu. id: " toteuma-id)
                       (log/debug "Aloitetaan toteuman tehtävien tallennus")
                       (api-toteuma/tallenna-tehtavat db kirjaaja toteuma toteuma-id urakka-id)
                       (log/debug "Aloitetaan toteuman materiaalien tallennus")
                       (api-toteuma/tallenna-materiaalit db kirjaaja toteuma toteuma-id urakka-id)
                       (log/debug "Aloitetaan toteuman vanhan reitin poistaminen, jos sellainen on")
                       (poista-toteuman-reitti db toteuma-id)
                       (log/debug "Liitetään toteuman reitti")
                       (let [reitti (async/<!! toteuman-reitti)]
                         (when (= reitti +yhdistamis-virhe+)
                           (log/warn (format "Reittitoteuman reitin geometriaa ei saatu luotua. Kirjaaja oli %s, ja toteuman aikaleimat olivat %s %s"
                                       kirjaaja
                                       (pr-str (:alkanut toteuma))
                                       (pr-str (:paattynyt toteuma)))))
                         (api-toteuma/paivita-toteuman-reitti db toteuma-id (if (= reitti +yhdistamis-virhe+) nil reitti)))
                       toteuma-id))]
    ;; Tehdään reittipisteet asynkronisesti, sillä niiden käsittelyssä voi kestää kauan.
    ;; Talvisuolauksen osalta pisteille tehdään työlästä laskentaa kun päätellään rajoitusalueelle kohdistumista.
    (log/debug "Aloitetaan reitin tallennus")
    (async/thread
      (try
        (luo-reitti db reitti toteuma-id)
        (async/put! reittipisteet-tallennettu-chan true)
        (catch Throwable t
          (log/warn t "Reittitoteuman tallennus epäonnistui"))))))

(defn- materiaalicachen-paivitys-ajettava?
  "Kertoo ajetaanko materiaalicachejen päivitys käsin. Kuluvan päivän toteumille menevät eräajoissa, muille kyllä."
  [toteuma-alkanut]
  (not (pvm/tanaan? toteuma-alkanut)))

(defn- paivita-materiaalicachet!
  "Päivittää materiaalicachetaulut sopimuksen_materiaalin_kaytto ja urakan_materiaalin_kaytto_hoitoluokittain"
  [db urakka-id data]
  (let [reittitoteumat (if (:reittitoteuma data)
                         [data]
                         (:reittitoteumat data))
        materiaaleja-hyotykuormassa? (some #(not-empty (get-in % [:reittitoteuma :toteuma :materiaalit]))
                                           reittitoteumat)
        suolauksen-toimenpidekoodi (:id (first (materiaalit/hae-suolauksen-toimenpidekoodi db)))
        tehtavissa-suolausta? (some #(some
                                       (fn [tehtava]
                                         (= (get-in tehtava [:tehtava :id]) suolauksen-toimenpidekoodi))
                                       (get-in % [:reittitoteuma :toteuma :tehtavat]))
                                    reittitoteumat)]
    (assert (integer? urakka-id) "Oltava urakka-id kun päivitetään materiaalicachet.")
    ;; Urakoitsijat joskus "poistavat" materiaalitoteumia lähettämällä toteuman uudestaan
    ;; tehtävänä suolaus mutta kokonaan ilman materiaalit-payloadia. Tämä siksi käsiteltävä erikseen
    ;; ja varmuuden vuoksi päivitettävä silloinkin materiaalicachet
    (when (or materiaaleja-hyotykuormassa? tehtavissa-suolausta?)
      (let [urakan-sopimus-idt (map :id (sopimukset-q/hae-urakan-sopimus-idt db {:urakka_id urakka-id}))
            ensimmainen-toteuma-alkanut-str (get-in (first reittitoteumat) [:reittitoteuma :toteuma :alkanut])
            viimeinen-toteuma (last reittitoteumat)
            viimeinen-toteuma-alkanut-str (get-in viimeinen-toteuma [:reittitoteuma :toteuma :alkanut])
            ensimmainen-toteuman-alkanut-pvm (pvm-string->joda-date ensimmainen-toteuma-alkanut-str)
            viimeinen-toteuman-paattynyt-pvm (pvm-string->joda-date (get-in viimeinen-toteuma [:reittitoteuma :toteuma :paattynyt]))
            toteumien-eri-pvmt (if (pvm/ennen? ensimmainen-toteuman-alkanut-pvm viimeinen-toteuman-paattynyt-pvm) ;; Poikkeustilanteissa toteumat tulevat ajallisesti väärässä järjestyksessä, huomioi se.
                                     (pvm/paivat-aikavalissa ensimmainen-toteuman-alkanut-pvm viimeinen-toteuman-paattynyt-pvm)
                                     (pvm/paivat-aikavalissa viimeinen-toteuman-paattynyt-pvm ensimmainen-toteuman-alkanut-pvm))]

        ;; Öinen eräajo päivittää cachet niille toteumille, joissa t.alkanut on kuluvan päivän aikana (ns. normaalitilanne)
        ;; Muille toteumille (esim. vanhan toteuman uudelleen lähetys, tai erittäin pitkän toteuman lähetys, joka alkaa klo 22 ja päätyy API:in aamulla klo 4) ajetaan yhä "käsin" cachejen päivitys

        (when (materiaalicachen-paivitys-ajettava? (aika-string->java-util-date ensimmainen-toteuma-alkanut-str))
          (doseq [sopimus-id urakan-sopimus-idt]
            (doseq [pvm toteumien-eri-pvmt]
              (materiaalit/paivita-sopimuksen-materiaalin-kaytto db {:sopimus sopimus-id
                                                                     :alkupvm (pvm/dateksi pvm)
                                                                     :urakkaid urakka-id})))
          (materiaalit/paivita-urakan-materiaalin-kaytto-hoitoluokittain db {:urakka urakka-id
                                                                             :alkupvm (aika-string->java-sql-timestamp ensimmainen-toteuma-alkanut-str)
                                                                             :loppupvm (aika-string->java-sql-timestamp viimeinen-toteuma-alkanut-str)}))))))

(defn tallenna-kaikki-pyynnon-reittitoteumat [db db-replica urakka-id kirjaaja data]
  (let [reittipisteet-tallennettu-chan (async/chan)
        reittitoteumien-maara (if (:reittitoteuma data)
                                1
                                (count (:reittitoteumat data)))]
    ;; Odotetaan, että kaikki reittipisteet on tallennettu. Jos on mennyt kymmenen minuuttia ilman tallennettuja
    ;; reittipisteitä, luovutetaan ja lokitetaan virhe.
    (async/thread
      (jdbc/with-db-transaction [db db]
        (loop [tallennettujen-maara 0]
          (if (= tallennettujen-maara reittitoteumien-maara)
            (paivita-materiaalicachet! db urakka-id data)
            (let [ [v _] (async/alts!! [reittipisteet-tallennettu-chan (async/timeout reittipisteet-timeout)])]
              (log/debug (format "Reittipisteet tallennettu! %s/%s" (inc tallennettujen-maara) reittitoteumien-maara))
              (if v
                (recur (inc tallennettujen-maara))
                (log/error "Reittipisteiden tallennuksessa kestänyt yli 10 minuuttia!")))))))

    (when (:reittitoteuma data)
      (let [jsonhash (konversio/string->md5 (pr-str (:reittitoteuma data)))]
        (if (toteumat-q/ei-ole-lahetetty-aiemmin? db-replica jsonhash (get-in data [:reittitoteuma :toteuma :tunniste :id]))
          (tallenna-yksittainen-reittitoteuma db db-replica urakka-id kirjaaja (:reittitoteuma data) jsonhash
            reittipisteet-tallennettu-chan)
          (async/put! reittipisteet-tallennettu-chan true))))

    (doseq [toteuma (:reittitoteumat data)]
      (let [jsonhash (konversio/string->md5 (pr-str toteuma))]
        (if (toteumat-q/ei-ole-lahetetty-aiemmin? db-replica jsonhash (get-in toteuma [:reittitoteuma :toteuma :tunniste :id]))
          (tallenna-yksittainen-reittitoteuma db db-replica urakka-id kirjaaja (:reittitoteuma toteuma) jsonhash
            reittipisteet-tallennettu-chan)
          (async/put! reittipisteet-tallennettu-chan true))))))

(defn tarkista-pyynto [db urakka-id kirjaaja data]
  (let [sopimus-idt (api-toteuma/hae-toteuman-kaikki-sopimus-idt :reittitoteuma :reittitoteumat data)]
    (doseq [sopimus-id sopimus-idt]
      (validointi/tarkista-urakka-sopimus-ja-kayttaja db urakka-id sopimus-id kirjaaja)))
  (when (:reittitoteuma data)
    (toteuman-validointi/tarkista-reittipisteet data)
    (toteuman-validointi/tarkista-tehtavat
      db
      urakka-id
      (get-in data [:reittitoteuma :toteuma :tehtavat])
      (get-in data [:reittitoteuma :toteuma :toteumatyyppi])))
  (doseq [reittitoteuma (:reittitoteumat data)]
    (toteuman-validointi/tarkista-reittipisteet reittitoteuma)
    (toteuman-validointi/tarkista-tehtavat
      db
      urakka-id
      (get-in reittitoteuma [:reittitoteuma :toteuma :tehtavat])
      (get-in reittitoteuma [:reittitoteuma :toteuma :toteumatyyppi]))))

(defn kirjaa-toteuma [db db-replica {id :id} data kirjaaja]
  (let [urakka-id (Integer/parseInt id)]
    (log/debug "Kirjataan uusi reittitoteuma urakalle id:" urakka-id " kayttäjän:" (:kayttajanimi kirjaaja)
               " (id:" (:id kirjaaja) ") tekemänä.")
    (tarkista-pyynto db urakka-id kirjaaja data)
    (tallenna-kaikki-pyynnon-reittitoteumat db db-replica urakka-id kirjaaja data)
    (tee-onnistunut-vastaus)))


(defn poista-toteuma [db _ {id :id} data kirjaaja]
  (let [urakka-id (Integer/parseInt id)
        ulkoiset-idt (-> data :toteumien-tunnisteet)]
    (log/debug "Poistetaan reittitoteumat id:lla:" ulkoiset-idt "urakalta id:" urakka-id " kayttäjän:" (:kayttajanimi kirjaaja)
               " (id:" (:id kirjaaja) " tekemänä")
    (tarkista-pyynto db urakka-id kirjaaja data)
    (api-toteuma/poista-toteumat db kirjaaja ulkoiset-idt urakka-id)))

(defrecord Reittitoteuma []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db
           db-replica :db-replica
           integraatioloki :integraatioloki
           :as this}]
    (julkaise-reitti
     http :lisaa-reittitoteuma
      (POST "/api/urakat/:id/toteumat/reitti" request
        (kasittele-kutsu db
                         integraatioloki
                         :lisaa-reittitoteuma
                         request
                         json-skeemat/reittitoteuman-kirjaus
                         json-skeemat/kirjausvastaus
                         (fn [parametit data kayttaja db]
                           (#'kirjaa-toteuma db db-replica
                             parametit data kayttaja))
          :kirjoitus)))

    (julkaise-reitti
     http :lisaa-reittitoteuma
    (DELETE "/api/urakat/:id/toteumat/reitti" request
        (kasittele-kutsu db
                         integraatioloki
                         :poista-reittitoteuma
                         request
                         json-skeemat/reittitoteuman-poisto
                         json-skeemat/kirjausvastaus
                         (fn [parametit data kayttaja db]
                           (#'poista-toteuma db db-replica
                                             parametit data kayttaja))
          :kirjoitus)))

    this)
  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :lisaa-reittitoteuma)
    this))

;; Reittitoteuman kirjaaminen tiedostosta (esim. payload integraatiolokista)
;;(def toteuma (cheshire.core/parse-string (slurp "reittitoteuma-urakka-125.json") keyword))
;;(def db (:db harja.palvelin.main/harja-jarjestelma))
;;(kirjaa-toteuma db db {:id "urakkaid"} toteuma {:id kayttajaid})

#_(defn vertaa-toteuman-suolamaara-vs-reittipisteiden-suolamaaran-summa
  "Ottaa sisään yo. toteuma deffissä tiedostosta parsitun toteuman, ja palauttaa paljonko siinä ilmoitetaan toteumamääräksi 1) otsikkotasolla ja
  2) reittipisteistä summattuna: Tavoite funktiolla on helpottaa debuggausta Ympäristöraportin hoitoluokittaisen erittelyn osalta."
  [toteuma]
  (let [toteumassa-raportoitu-maara (for [{:keys [materiaali maara]} (get-in toteuma [:reittitoteuma :toteuma :materiaalit])
                                          :let [yksikko (get maara :yksikko)
                                                maara (get maara :maara)]]
                                      {materiaali (str maara yksikko)})
        reittipisteiden-materiaalit-ryhmiteltyna (flatten (map (fn [reittipiste]
                                                                 (for [[nimi sisalto] (group-by :materiaali (get-in reittipiste [:reittipiste :materiaalit]))
                                                                       :let [maara (get-in (first sisalto) [:maara :maara])]]
                                                                   {nimi maara}))
                                                               (get-in toteuma [:reittitoteuma :reitti])))
        reittipisteiden-materiaalit-summattuna (apply merge-with + reittipisteiden-materiaalit-ryhmiteltyna)]

    {:toteumassa-ilmoitettu-maara toteumassa-raportoitu-maara
     :reittipisteista-laskettu-maara reittipisteiden-materiaalit-summattuna}))
