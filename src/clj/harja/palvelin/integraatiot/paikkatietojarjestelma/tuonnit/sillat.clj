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
        (do (q-sillat/paivita-silta! db sql-parametrit)
            (logita-virhe-sillan-tuonnissa db
                                           "Kunnan hoitamalle sillalle merkattu tarkastuksia"
                                           (str "Silta " silta-taulun-id
                                                " on merkattu kunnan hoitamaksi, mutta sillä on siltatarkastuksia.")))
        ;; Merkataan silta poistetuksi, jos ei ole tarkastuksia
        (q-sillat/merkkaa-silta-poistetuksi! db {:silta-id silta-taulun-id})))))


(defn luo-tai-paivita-silta [db silta-floateilla]
  (when (or (= 1 1) (= (:silta_id silta-floateilla) 246125))
  (let [silta (mapin-floatit-inteiksi silta-floateilla)
        aineistovirhe (:loc_error silta)
        tyyppi (:rakennety silta)
        siltanumero (:siltanro silta)
        nimi (:siltanimi silta)
        geometria nil; (if (= aineistovirhe "NO ERROR") (.toString (:the_geom silta)) nil) ;; Jos sillan geometria ei ole validi, jätetään se tallentamatta, mutta tallennetaan muut tiedot sillasta. Poistetuilta silloilta puuttuu sijainti, mutta tiedon poistosta täytyy siirtyä.
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
                                                                  (log/debug "Silta väärässä urakassa"
                                                                                                 (str "Silta " (:silta-taulun-id vaara-urakka)
                                                                                                      " on merkattu väärään urakkaan " (:urakka-id vaara-urakka)
                                                                                                      "! :bridge_at_night:|||"
                                                                                                      "Pitäisi olla urakassa: " urakka-id))
                                                                  (logita-virhe-sillan-tuonnissa db "Silta väärässä urakassa"
                                                                                                 (str "Silta " (:silta-taulun-id  vaara-urakka)
                                                                                                      " on merkattu väärään urakkaan " (:urakka-id vaara-urakka)
                                                                                                      "! :bridge_at_night:|||"
                                                                                                      "Pitäisi olla urakassa: " urakka-id))
                                                                  (:urakka-id vaara-urakka))
                                                                (do (q-sillat/poista-urakka-sillalta! db (select-keys vaara-urakka #{:urakka-id :silta-taulun-id})) nil))))
                             poistetut-urakat (clj-set/difference (into #{} (map :urakka-id vaarat-urakat))
                                                                  urakat-vaarassa-sillassa)]
                         (if (empty? urakat-vaarassa-sillassa)
                           (do
                             (log/debug "Kaikki väärässä sillassa olevat urakat (" (mapv :urakka-id vaarat-urakat) ") saatiin poistettua")
                             (conj (reduce (fn [urakat {urakka-id :urakka-id}]
                                             (if (poistetut-urakat urakka-id)
                                               urakat (conj urakat urakka-id)))
                                           [] urakkatiedot)
                                   urakka-id))
                           (do
                             (log/debug "Kaikkia väärässä sillassa olevat urakoita (" (mapv :urakka-id vaarat-urakat) ") ei saatu poistettua. "
                                        "Urakat väärässä sillassa on: " urakat-vaarassa-sillassa ". Ei lisätä oikeaa urakkaa listaan.")
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
                        :status status}]

    ;; AINEISTOON LIITTYVÄT HUOMIOT

    ; Ensimmäinen Harjassa tehty siltatarkastus: 2016-09-28 => Suodatetaan aineistosta mukaan vain ne sillat,
    ; joissa ei ole loppupäivämäärää tai joiden loppupäivämäärä on Harjan käyttöönoton jälkeen.
    ; Päivitettävä silta etsitään vanhalla Siltarekisterin siltaid:llä tai siltatunnuksella tai uudella Taitorakennerekisterin tunnuksella

    ; Loppupäivämäärä: Tietty ominaisuus, päätös tai asian tila päättyy tieverkolla sen loppupäivämääränä.   
    ; Lakkautuspäivämäärä: Lakkautuspäivämäärä on vain ja ainoastaan ajorataa ja tietä koskeva termi. Ajorataosuus on poistunut lakkautuspäivänä yleiseltä tieverkolta. Tämänkin hetken jälkeen tie saattaaa jäädä olemaan ja sille voidaan myöhemmin muuttaa ainakin teoriassa ominaisuuksia.  
    ; Kun ajorataosuus lakkautetaan, geometria poistuu tierekisteristä (=> aineistovirhe). Jotta geometria säilyy Harjassa, päivitetään lakkautetuista tieosuuksista ainoastaan lakkautuspäivämäärä.

    ; Silta_id on vanhan Siltarekisterin tunniste, nykyinen Taitorakennerekisteri yksilöi trex-oid-tiedolla. Silta_id voi siis jatkossa puuttua sillalta.
    ; Siltanumero on molempien id:n kanssa relevantti => Otetaan aineistoon mukaan vain sillat, joissa siltanumero on annettu.

    (when-not (nil? siltanumero)
      (if (some #(= % alueurakka) kunnan-numerot)
        (kasittele-kunnalle-kuuluva-silta db urakkatiedot sql-parametrit)
        (if-not (empty? urakkatiedot)
          (q-sillat/paivita-silta! db sql-parametrit)
          (if (or (nil? loppupvm)
                  (pvm/ennen? (pvm/->pvm "28.9.2016") loppupvm))
            (q-sillat/luo-silta<! db sql-parametrit))))))))

(defn vie-silta-entry [db silta]
  (luo-tai-paivita-silta db silta))


;
;
;(defn onko-silta-kokonaan-lakkautettu? [sillan-rivit]
;  (every? #(and
;              (not (= nil (:loppupvm %)))
;              (not (= "" (:loppupvm %)))
;              (not (= nil (:lakkautpvm %)))
;              (not (= "" (:lakkautpvm %))))
;          sillan-rivit))
;
;(defn merkitse-lakkautetetut-sillat
;  "Ottaa vastaan listan vektorit, joissa sillan trex-oid ja rivien määrä sekä shapefilestä koostetun silta-aineiston.
;  Palauttaa vekorilistan, johon on lisätty tieto siitä, onko silta poistunut käytöstä kokonaan."
;  [tarkasteltavat-sillat kaikki-sillat]
;  ;; 3. Jos vain osa sillan riveistä on loppupvm/lakkautuspvm is not null, suodata pois rivi(t), joissa loppupvm/lakkautuspvm is not null.
;  ;;    Jos kaikki ovat lopetettuja/lakkautettuja, jätä aineistoon rivi jossa on tuorein :muutospvm
;  (for [silta  tarkasteltavat-sillat]
;    (let [sillan-rivit (filter #(= (key silta) (:trex_oid %)) kaikki-sillat)]
;      (conj silta (onko-silta-kokonaan-lakkautettu? sillan-rivit)))))
;
;
;(defn suodata-tarkasteltavat-sillat
;  "Ottaa vastaan shapefilestä koostetun silta-aineiston.
;  Suodattaa ensin silloista pois ne, joissa trex_oid on nil tai tyhjä.
;  Valitsee sitten tarkasteltavaksi ne sillat, joista aineistossa on 2 tai useampi rivi.
;  Palauttaa listan vektoreita, joissa sillan trex-oid ja silta-aineistosta löytyvien rivien määrä."
;  [kaikki-sillat]
;
;  ;; Aineistossa :trex_ois on null tai tyhjä vain, kun myös :silta_id on tyhjä. Silta ei ole yksiselitteisesti tunnistettavissa.
;  ;; Nämä sillat ovat aina myös lopetettuja/lakkautettuja ja Harjan kannalta turhia.
;
;
;  (let [tunnistettavat-sillat (filter #(not (or
;                                              (= nil (key %))
;                                              (= "" (key %))))
;                                      (group-by :trex_oid kaikki-sillat))
;        tarkasteltavat-sillat (filter #(> (val %) 1)
;                                      (zipmap (keys tunnistettavat-sillat)
;                                              (map #(count (second %)) tunnistettavat-sillat)))]
;    tarkasteltavat-sillat))
;
;(defn jarjesta-sillat
;  "Ottaa vastaan listan vektorit, joissa sillan trex-oid ja rivien määrä sekä shapefilestä koostetun silta-aineiston.
;  Käsittelee kunkin sillan vektorilistassa ja poistaa silta-aineistosta ei-relevantit rivit."
;  [tarkasteltavat-sillat kaikki-sillat]
;  (let [sillat (merkitse-lakkautetetut-sillat tarkasteltavat-sillat kaikki-sillat)]
;    (for [silta  sillat]
;      (remove #(= (:id %) 2) (sort-by :muutospvm kaikki-sillat))
;    )
;  )
;
;  )


(defn distinct-consequtive [sequence] (map first (partition-by #(= :trex_oid (:trex_oid %)) sequence)))



(defn siivoa-duplikaatit-silloista [siltatietueet-shapefilesta]
  (let [sorted (sort-by (juxt :trex_oid :muutospvm :loppupvm)  siltatietueet-shapefilesta)
        ;filtered (medley.core/distinct-by #(% :trex_oid)  sorted)
         ]
    (distinct-consequtive sorted)
      )
  )

(defn vie-sillat-kantaan [db shapefile]
  (if shapefile
    (let [siltatietueet-shapefilesta (shapefile/tuo shapefile)
          ;relevantit-siltatietueet (siivoa-duplikaatit-silloista siltatietueet-shapefilesta)
          ]
      (log/debug (str "Tuodaan sillat kantaan tiedostosta " shapefile))
      (try (jdbc/with-db-transaction [db db]
             (doseq [silta siltatietueet-shapefilesta]
               (vie-silta-entry db silta)))
           (log/debug "siltojen tuonti kantaan valmis.")
           (catch PSQLException e
             (log/error "Siltojen tuonnissa kantaan tapahtui virhe: " e)
             (throw e))
           (catch Exception e
             (log/error "Siltojen tuonnissa tapahtui virhe: " e)
             (throw e))))
    (log/debug "Siltojen tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))
