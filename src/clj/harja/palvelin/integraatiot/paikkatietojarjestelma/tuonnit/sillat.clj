(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.sillat
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.pvm :as pvm]
            [harja.kyselyt.sillat :as q-sillat]
            [harja.kyselyt.urakat :as q-urakka]
            [harja.kyselyt.integraatioloki :as q-integraatioloki]
            [harja.kyselyt.geometriapaivitykset :as q-geometria]
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



(defn luo-tai-paivita-silta [db silta-floateilla]
  (let [silta (mapin-floatit-inteiksi silta-floateilla)
        aineistovirhe (:loc_error silta)
        tyyppi (:rakennety silta)
        siltanumero (:siltanro silta)
        nimi (:siltanimi silta)
        geometria (if (= aineistovirhe "NO ERROR") (.toString (:the_geom silta)) nil) ;; Jos sillan geometria ei ole validi, jätetään se tallentamatta, mutta tallennetaan muut tiedot sillasta
        tie (:tie silta)
        alkuosa (:aosa silta)
        alkuetaisyys (:aet silta)
        ely-lyhenne (:ely_lyhenn silta)
        ely-numero (:ely silta)
        loppupvm_str (:loppupvm silta)
        loppupvm (if (> (count loppupvm_str) 0) (c/to-date (str (subs loppupvm_str 0 4) "-" (subs loppupvm_str 4 6) "-" (subs loppupvm_str 6 8))) nil) ;; päivämäärä saadaan avasta muodossa 19940411000000, voi olla tyhjä
        lakkautuspvm_str (:lakkautpvm silta)
        lakkautuspvm (if (> (count lakkautuspvm_str) 0) (c/to-date (str (subs lakkautuspvm_str 0 4) "-" (subs lakkautuspvm_str 4 6) "-" (subs lakkautuspvm_str 6 8))) nil) ;; päivämäärä saadaan avasta muodossa 19940411000000, voi olla tyhjä'
        muutospvm_str (:muutospvm silta)
        muutospvm (if (> (count muutospvm_str) 0) (c/to-date (str (subs muutospvm_str 0 4) "-" (subs muutospvm_str 4 6) "-" (subs muutospvm_str 6 8))) nil) ;; päivämäärä saadaan avasta muodossa 19940411000000, voi olla tyhjä'
        status (int (:status silta))
        laani-lyhenne (get elytunnuksen-laani ely-lyhenne)
        tunnus (when (not-empty laani-lyhenne)
                 (str laani-lyhenne "-" siltanumero))
        urakka-id (when-let [alueurakka (str (:ualue silta))]
                    (q-urakka/hae-urakka-id-alueurakkanumerolla db {:alueurakka alueurakka}))
        trex-oid (:trex_oid silta)
        siltaid (string-intiksi (:silta_id silta))
        urakkatiedot (q-sillat/hae-sillan-tiedot db {:siltaid siltaid :siltatunnus tunnus :trex-oid trex-oid})
        urakat (into []
                     ;; Poistetaan mahdolliset nil arvot vektorista
                     (keep identity)
                     (cond
                       ;; Siltaa ei ole kannassa
                       (empty? urakkatiedot) [urakka-id]
                       ;; Silta on kannassa ja urakka on jo merkattu sillalle
                       (some #(= (:urakka-id %) urakka-id) urakkatiedot) (mapv :urakka-id urakkatiedot)
                       ;; Urakkaa ei ole merkattu sillalle, joten täytyy tarkistaa onko silta jo toisessa aktiivisessa
                       ;; urakassa. Varmaankin mahdollista vain, jos esim. tr:ssä ollaan päivitetty urakkatietoja.
                       (some #(pvm/ennen? (pvm/nyt) (pvm/->pvm (:loppupvm %))) urakkatiedot)
                       (let [vaarat-urakat (filter #(pvm/ennen? (pvm/nyt) (pvm/->pvm (:loppupvm %))) urakkatiedot)
                             ;; Jos väärissä urakoissa on jo siltatarkastuksia, ei tehdä muuta kuin logitetaan virhe.
                             ;; Jos taasen väärään urakkaan ei olla merkattu siltatarkastuksia, otetaan väärä urakka pois ja
                             ;; laitetaan silta oikeaan urakkaan.
                             urakat-vaarassa-sillassa (into #{}
                                                            (keep identity)
                                                            (for [vaara-urakka vaarat-urakat]
                                                              (if (:siltatarkastuksia? vaara-urakka)
                                                                (let [tapahtuma-id (q-integraatioloki/hae-uusin-integraatiotapahtuma-id db {:jarjestelma "ptj"
                                                                                                                                            :nimi "sillat-haku"})
                                                                      integraatio-log-params {:tapahtuma-id tapahtuma-id
                                                                                              :alkanut (pvm/pvm->iso-8601 (pvm/nyt-suomessa))
                                                                                              :valittu-jarjestelma "ptj"
                                                                                              :valittu-integraatio "sillat-haku"}]
                                                                  (log/error {:fields [{:title "Linkit"
                                                                                        :value (str "<|||ilog" integraatio-log-params "ilog||||Harja integraatioloki> "
                                                                                                    "<|||jira Silta väärässä urakassa: jira||||JIRA>")}]
                                                                              :tekstikentta (str "Silta " siltaid " on merkattu väärään urakkaan " (:urakka-id vaara-urakka)
                                                                                                 "! :bridge_at_night:|||" "Pitäisi olla urakassa: " urakka-id)})
                                                                  (:urakka-id vaara-urakka))
                                                                (do (q-sillat/poista-urakka-sillalta! db {:urakka-id (:urakka-id vaara-urakka) :silta-id siltaid}) (println "---><<: 4") nil))))]
                         (if (empty? urakat-vaarassa-sillassa)
                           (do
                             (log/debug "Kaikki vaarassa sillassa olevat urakat (" (map :urakka-id vaarat-urakat) ") saatiin poistettua")
                             (conj (mapv :urakka-id urakkatiedot) urakka-id))
                           (do
                             (log/debug "Kaikkia vaarassa sillassa olevat urakoita (" (map :urakka-id vaarat-urakat) ") ei saatu poistettua. "
                                        "Urakat väärässä sillassa on: " urakat-vaarassa-sillassa ". Ei lisätä oikeaa urakkaa listaan.")
                             (mapv :urakka-id urakkatiedot))))
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

    (when (not= nil siltanumero)
      (if-not (empty? urakkatiedot)
        (q-sillat/paivita-silta! db sql-parametrit)
        (if (or (= loppupvm "")
                (= loppupvm nil)
                (pvm/ennen? (pvm/->pvm "28.9.2016") loppupvm))
          (q-sillat/luo-silta<! db sql-parametrit))))))

(defn vie-silta-entry [db silta]
  (luo-tai-paivita-silta db silta))

(defn vie-sillat-kantaan [db shapefile]
  (if shapefile
    (let [siltatietueet-shapefilesta (reverse (sort-by :muutospvm (shapefile/tuo shapefile)))]
      (log/debug (str "Tuodaan sillat kantaan tiedostosta " shapefile))
      (try (time (jdbc/with-db-transaction [db db]
                   (doseq [silta siltatietueet-shapefilesta]
                     (vie-silta-entry db silta))))
           (log/debug "siltojen tuonti kantaan valmis.")
           (catch PSQLException e
             (log/error "Siltojen tuonnissa kantaan tapahtui virhe: " e)
             (q-geometria/poista-viimeisin-paivitys! db {:nimi "sillat"}))
           (catch Exception e
             (log/error "Siltojen tuonnissa tapahtui virhe: " e)
             (q-geometria/poista-viimeisin-paivitys! db {:nimi "sillat"}))))
    (log/debug "Siltojen tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))
