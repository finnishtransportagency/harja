(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.sillat
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [clojure.set :as clj-set]
            [harja.pvm :as pvm]
            [harja.kyselyt.sillat :as q-sillat]
            [harja.kyselyt.urakat :as q-urakka]
            [harja.kyselyt.integraatioloki :as q-integraatioloki]
            [harja.tyokalut.functor :as functor]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]
            [clj-time.coerce :as c])
  (:import [java.sql BatchUpdateException]
           [org.postgresql.util PSQLException]))

(defn string-intiksi [str]
  (if (string? str)
    (let [ei-numeeriset-poistettu (re-find #"\d+" str)]
      (if (nil? ei-numeeriset-poistettu)
        nil
        (Integer. ei-numeeriset-poistettu)))
    str))

(defn float-intiksi [v]
  (if (float? v)
    (int v)
    v))

(defn mapin-floatit-inteiksi [m]
  (into {} (map (fn [[k v]]
                  [k (float-intiksi v)])
                m)))

(def elytunnuksen-laani
  {"Uud" "U"                                                ;; uusimaa
   "Var" "T"                                                ;; varsinais-suomi
   "Kas" "KaS"                                              ;; kaakkois-suomi
   "Pir" "H"                                                ;; pirkanmaa
   "Pos" "SK"                                               ;; pohjois-savo
   "Kes" "KeS"                                              ;; keskis-suomi
   "Epo" "V"                                                ;; etelä-pohjanmaa
   "Pop" "O"                                                ;; pohjois-pohjanmaa
   "Lap" "L"                                                ;; lappi
   })

(defn logita-virhe-sillan-tuonnissa [db jira-viesti tekstikentta]
  (let [tapahtuma-id (q-integraatioloki/hae-uusin-integraatiotapahtuma-id db {:jarjestelma "ptj"
                                                                              :nimi "sillat-haku"})
        integraatio-log-params {:tapahtuma-id tapahtuma-id
                                :alkanut (pvm/pvm->iso-8601 (pvm/nyt-suomessa))
                                :valittu-jarjestelma "ptj"
                                :valittu-integraatio "sillat-haku"}]
    (log/error :ei-logiteta_
               {:fields [{:title "Linkit"
                          :value (str "<|||ilog" integraatio-log-params "ilog||||Harja integraatioloki> | "
                                      "<|||glogglog||||Graylog> | "
                                      "<|||jira" jira-viesti "jira||||JIRA>")}]
                :tekstikentta tekstikentta})))

(defn kasittele-kunnalle-kuuluva-silta
  "HARJA:n kantaan on tallennettu kunnalle kuuluvia siltoja.
   Pyritään niistä pois, niin tulevaisuudessa tätä funktiota ei edes välttämättä
   tarvi."
  [db urakkatiedot sql-parametrit]
  (let [silta-kannassa? (not (empty? urakkatiedot))
        silta-taulun-id  (:silta-taulun-id (first urakkatiedot))]
    (when silta-kannassa?
      (if (some :siltatarkastuksia? urakkatiedot)
        ;; Jos on tarkastuksia, annetaan olla kannassa ja logitetaan
        (do (q-sillat/paivita-silta! db (assoc sql-parametrit :kunnan-vastuulla true))
            (logita-virhe-sillan-tuonnissa db
                                           "Kunnan hoitamalle sillalle merkattu tarkastuksia"
                                           (str "Silta " silta-taulun-id
                                                " on merkattu kunnan hoitamaksi, mutta sillä on siltatarkastuksia.")))
        ;; Merkataan silta poistetuksi, jos ei ole tarkastuksia
        (q-sillat/merkkaa-kunnan-silta-poistetuksi! db {:silta-id silta-taulun-id})))))


(defn luo-tai-paivita-silta [db silta-floateilla]
  (let [silta (mapin-floatit-inteiksi silta-floateilla)
        aineistovirhe (:loc_error silta)
        tyyppi (:rakennety silta)
        siltanumero (:siltanro silta)
        nimi (:siltanimi silta)
        geometria (if (= aineistovirhe "NO ERROR") (.toString (:the_geom silta)) nil) ;; Jos sillan geometria ei ole validi, jätetään se tallentamatta, mutta tallennetaan muut tiedot sillasta. Poistetuilta silloilta puuttuu sijainti, mutta tiedon poistosta täytyy siirtyä.
        tie (:tie silta)
        alkuosa (:aosa silta)
        alkuetaisyys (:aet silta)
        ely-lyhenne (:ely_lyhenn silta)
        ely-numero (:ely silta)
        loppupvm_str (:loppupvm silta)
        loppupvm (when (> (count loppupvm_str) 0) (c/to-date (str (subs loppupvm_str 0 4) "-" (subs loppupvm_str 4 6) "-" (subs loppupvm_str 6 8)))) ;; päivämäärä saadaan avasta muodossa 19940411000000, voi olla tyhjä
        lakkautuspvm_str (:lakkautpvm silta)
        lakkautuspvm (when (> (count lakkautuspvm_str) 0) (c/to-date (str (subs lakkautuspvm_str 0 4) "-" (subs lakkautuspvm_str 4 6) "-" (subs lakkautuspvm_str 6 8)))) ;; päivämäärä saadaan avasta muodossa 19940411000000, voi olla tyhjä'
        muutospvm_str (:muutospvm silta)
        muutospvm (when (> (count muutospvm_str) 0) (c/to-date (str (subs muutospvm_str 0 4) "-" (subs muutospvm_str 4 6) "-" (subs muutospvm_str 6 8)))) ;; päivämäärä saadaan avasta muodossa 19940411000000, voi olla tyhjä'
        status (int (:status silta))
        laani-lyhenne (get elytunnuksen-laani ely-lyhenne)
        tunnus (when (not-empty laani-lyhenne)
                 (str laani-lyhenne "-" siltanumero))
        kunnan-numerot ["400" "481"]
        alueurakka (str (:ualue silta))
        urakka-id (when-not (or (empty? alueurakka)
                                (some #(= % alueurakka) kunnan-numerot))
                    (q-urakka/hae-urakka-id-alueurakkanumerolla db {:alueurakka alueurakka}))
        _ (when (and (nil? urakka-id) (not (some #(= % alueurakka) kunnan-numerot)) (= aineistovirhe "NO ERROR"))
            (log/debug :ei-logiteta_ "---> e: " silta-floateilla)
            (logita-virhe-sillan-tuonnissa db "Virhe shapefilessä: Sillalle ei ole merkattu urakkaa"
                                           (str "Sillalle " (:siltanimi silta)
                                                " (" siltanumero ") "
                                                " ei ole merkattu alueurakkaa. Tai on tullut uusi kuntanumero.")))
        trex-oid (when-not (empty? (:trex_oid silta))
                   (:trex_oid silta))
        siltaid (string-intiksi (:silta_id silta))
        urakkatiedot (q-sillat/hae-sillan-tiedot db {:trex-oid trex-oid :siltaid siltaid :siltatunnus tunnus :siltanimi nimi})
        urakat (into []
                     ;; Poistetaan mahdolliset nil arvot vektorista
                     (keep identity)
                     (cond
                       ;; Ei löydetty urakkaa tai kunta hoitaa, palautetaan ne urakat, jotka jo merkattu.
                       (nil? urakka-id) (mapv :urakka-id urakkatiedot)
                       ;; Siltaa ei ole kannassa
                       (empty? urakkatiedot) [urakka-id]
                       ;; Silta on kannassa ja urakka on jo merkattu sillalle
                       (some #(= (:urakka-id %) urakka-id) urakkatiedot)(mapv :urakka-id urakkatiedot)
                       ;; Urakkaa ei ole merkattu sillalle, joten täytyy tarkistaa onko silta jo toisessa aktiivisessa
                       ;; urakassa. Varmaankin mahdollista vain, jos esim. tr:ssä ollaan päivitetty urakkatietoja.
                       (some #(pvm/ennen? (pvm/nyt) (:loppupvm %)) urakkatiedot)
                       (let [vaarat-urakat (filter #(pvm/ennen? (pvm/nyt) (:loppupvm %)) urakkatiedot)
                             ;; Jos väärissä urakoissa on jo siltatarkastuksia, ei tehdä muuta kuin logitetaan virhe.
                             ;; Jos taasen väärään urakkaan ei olla merkattu siltatarkastuksia, otetaan väärä urakka pois ja
                             ;; laitetaan silta oikeaan urakkaan.
                             urakat-vaarassa-sillassa (into #{}
                                                            (keep identity)
                                                            (for [vaara-urakka vaarat-urakat]
                                                              (if (:siltatarkastuksia? vaara-urakka)
                                                                (do
                                                                  (logita-virhe-sillan-tuonnissa db "Silta väärässä urakassa"
                                                                                                 (str "Siltaan " (:silta-taulun-id  vaara-urakka) " (trex-oid: " trex-oid ")"
                                                                                                      " on merkattu väärä urakka " (:urakka-id vaara-urakka) " (alueurakka: " alueurakka ")"
                                                                                                      "! :bridge_at_night:|||"
                                                                                                      "Silta on urakan " urakka-id " vastuulla"))
                                                                  (:urakka-id vaara-urakka))
                                                                (do (q-sillat/poista-urakka-sillalta! db (select-keys vaara-urakka #{:urakka-id :silta-taulun-id})) nil))))
                             poistetut-urakat (clj-set/difference (into #{} (map :urakka-id vaarat-urakat))
                                                                  urakat-vaarassa-sillassa)]
                         (if (empty? urakat-vaarassa-sillassa)
                           (do
                             (log/debug "Kaikki virheellisesti siltaan ( trex-oid:" trex-oid ", silta-id: " siltaid ") liitetyt urakat (" (mapv :urakka-id vaarat-urakat) ") saatiin poistettua sillan tiedoista.")
                             (conj (reduce (fn [urakat {urakka-id :urakka-id}]
                                             (if (poistetut-urakat urakka-id)
                                               urakat (conj urakat urakka-id)))
                                           [] urakkatiedot)
                                   urakka-id))
                           (do
                             (log/debug "Kaikkia käsiteltyyn siltaan ( trex-oid:" trex-oid ", silta-id: " siltaid ") virheellisesti liitettyjä urakoita (" urakat-vaarassa-sillassa ") ei saatu poistettua sillan tiedoista. "
                                        "Ei lisätä oikeaa urakkaa ( alueurakka:" alueurakka ", urakka: " urakka-id ") listaan. Siltaan virheellisesti liitetyt urakat olivat poistetut mukaan lukien: " (mapv :urakka-id vaarat-urakat))
                             (reduce (fn [urakat {urakka-id :urakka-id}]
                                       (if (poistetut-urakat urakka-id)
                                         urakat (conj urakat urakka-id)))
                                     [] urakkatiedot))))
                       ;; Muuten silta on kannassa, mutta urakkaa ei ole merkattu sille
                       :else (conj (mapv :urakka-id urakkatiedot) urakka-id)))
        sql-parametrit {:tyyppi tyyppi
                        :siltanro siltanumero
                        :siltanimi nimi
                        :geometria geometria
                        :numero tie
                        :aosa alkuosa
                        :aet alkuetaisyys
                        :tunnus tunnus
                        :siltaid siltaid
                        :trex-oid trex-oid
                        :loppupvm loppupvm
                        :lakkautuspvm lakkautuspvm
                        :muutospvm muutospvm
                        :urakat urakat
                        :status status
                        :poistettu false
                        :kunnan-vastuulla false}]

    ;; AINEISTOON LIITTYVÄT HUOMIOT

    ; Ensimmäinen Harjassa tehty siltatarkastus: 2016-09-28 => Suodatetaan aineistosta mukaan vain ne sillat,
    ; joissa ei ole loppupäivämäärää tai joiden loppupäivämäärä on Harjan käyttöönoton jälkeen.
    ; Päivitettävä silta etsitään vanhalla Siltarekisterin siltaid:llä tai siltatunnuksella tai uudella Taitorakennerekisterin tunnuksella

    ; Loppupäivämäärä: Tietty ominaisuus, päätös tai asian tila päättyy tieverkolla sen loppupäivämääränä.   
    ; Lakkautuspäivämäärä: Lakkautuspäivämäärä on vain ja ainoastaan ajorataa ja tietä koskeva termi. Ajorataosuus on poistunut lakkautuspäivänä yleiseltä tieverkolta. Tämänkin hetken jälkeen tie saattaaa jäädä olemaan ja sille voidaan myöhemmin muuttaa ainakin teoriassa ominaisuuksia.  
    ; Kun ajorataosuus lakkautetaan, geometria poistuu tierekisteristä (=> aineistovirhe). Jotta geometria säilyy Harjassa, päivitetään lakkautetuista tieosuuksista ainoastaan lakkautuspäivämäärä.

    ; Silta_id on vanhan Siltarekisterin tunniste, nykyinen Taitorakennerekisteri yksilöi trex-oid-tiedolla. Silta_id voi siis jatkossa puuttua sillalta.
    ; Siltanumero on molempien id:n kanssa relevantti => Otetaan aineistoon mukaan vain sillat, joissa siltanumero on annettu.
    ; Jos sekä siltaid että trex-oid puuttuvat, ei viedä siltaa kantaan.

    (when-not (or (nil? siltanumero)
                  (and (nil? siltaid) (nil? trex-oid)))
      (if (some #(= % alueurakka) kunnan-numerot)
        (kasittele-kunnalle-kuuluva-silta db urakkatiedot sql-parametrit)
        (if-not (empty? urakkatiedot)
          (q-sillat/paivita-silta! db sql-parametrit)
          (if (or (nil? loppupvm)
                  (pvm/ennen? (pvm/->pvm "28.9.2016") loppupvm))
            (q-sillat/luo-silta<! db sql-parametrit)))))))

(defn vie-silta-entry [db silta]
  (luo-tai-paivita-silta db silta))

(defn voimassaolevat-sillat
  "Silta on voimassa, kun sillä on osuus, jossa ei ole asetettu lopetus- eikä lakkautuspäivämäärää.
  Jos silta ei ole voimassa, se on purettu, sillä liikennöinti on lakkautettu tai sen ylläpito on siirretty pois valtiolta.
  Harjan kannalta ei-voimassaoleva silta on sama kuin poistettu.
  Funktio palauttaa vektorin, jossa poistettavien siltojen trex-oid (yksilöivä tunnus Taitorakennerekisterissä)."
  [kaikki-sillat]
        (map #(:trex_oid %)
             (filter #(and
                        (= "" (:loppupvm %))
                        (= "" (:lakkautpvm %))) kaikki-sillat)))

(defn merkitse-siltojen-voimassaolostatus
  [voimassaolevat-sillat kaikki-sillat]
  (map (fn[silta]
         (if (some (fn[id](= (:trex_oid silta) id)) (vec voimassaolevat-sillat))
           (assoc silta :voimassa true)
           (assoc silta :voimassa false))) kaikki-sillat))


; Aineistossa tulee joskus koulutuksessa käytettyjä siltoja, poistetaan ne ennen käsittelyä.
(defn karsi-testisillat [sillat]
  (remove #(= true
              (boolean
                (re-find #"TENTTI_" (:siltanimi %))))
          sillat))

(defn karsi-voimassaolevien-siltojen-poistetut-osuudet
  "Ei viedä tietoja poistetuista osuuksista Harjaan, jos silta on vielä voimassa. Karsitaan ne aineistosta.
  Voimassa olevista osuuksista tallentuu yhden siltatietueen tiedot, vaikka osuuksia olisi useampia.
  Kokonaan poistetuista silloista samoin. Viimeisen käsiteltävän tietueen tiedot jäävät silloin Harjaan."
  [sillat-aineistosta]
    (remove #(and (= true (:voimassa %))
                  (or (not= "" (:loppupvm %)) (not= "" (:lakkautpvm %))))
            (sort-by (juxt :trex_oid :muutospvm)
                     (merkitse-siltojen-voimassaolostatus
                       (voimassaolevat-sillat sillat-aineistosta)
                       sillat-aineistosta))))

; Jotta ei tule tallennuksessa ongelmia yksilöivien avainten kanssa + suorituskyvyn parantamiseksi, jätetään tallennettavaan
; aineistoon ainoastaan yksi rivi per voimassaoleva silta.
(defn jarjesta-voimassaolevat-sillat-yksittaisille-riveille
  ([kaikki-siltarivit]
   (let [erottele (fn erottele [kaikki-siltarivit valitut-siltarivit]
                (lazy-seq
                  ((fn [[f :as kaikki-siltarivit] valitut-siltarivit]
                     (when-let [sillat (seq kaikki-siltarivit)]
                       (if (contains? valitut-siltarivit (:trex_oid f))
                         (recur (rest sillat) valitut-siltarivit)
                         (cons f (erottele (rest sillat) (conj valitut-siltarivit (:trex_oid f)))))))
                    kaikki-siltarivit valitut-siltarivit)))]
     (erottele kaikki-siltarivit #{}))))

(defn vie-sillat-kantaan [db shapefile]
  (if shapefile
    (let [siltatietueet-shapefilesta (shapefile/tuo shapefile)
          tallennettavat-siltatietueet (jarjesta-voimassaolevat-sillat-yksittaisille-riveille
                                         (karsi-testisillat (karsi-voimassaolevien-siltojen-poistetut-osuudet siltatietueet-shapefilesta)))]
      (log/debug (str "Tuodaan sillat kantaan tiedostosta " shapefile))
      (try (jdbc/with-db-transaction [db db]
             (doseq [silta tallennettavat-siltatietueet]
               (vie-silta-entry db silta)))
           (log/debug "siltojen tuonti kantaan valmis.")
           (catch PSQLException e
             (log/error "Siltojen tuonnissa kantaan tapahtui virhe: " e)
             (throw e))
           (catch Exception e
             (log/error "Siltojen tuonnissa tapahtui virhe: " e)
             (throw e))))
    (log/debug "Siltojen tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))
