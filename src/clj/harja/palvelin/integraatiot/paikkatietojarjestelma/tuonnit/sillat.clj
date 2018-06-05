(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.sillat
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.pvm :as pvm]
            [harja.kyselyt.sillat :as q-sillat]
            [harja.tyokalut.functor :as functor]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]
            [clj-time.coerce :as c]))

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
        trex_oid (:trex_oid silta)
        siltaid (string-intiksi (:silta_id silta))
        sql-parametrit {:tyyppi tyyppi
                        :siltanro siltanumero
                        :siltanimi nimi
                        :geometria geometria
                        :numero tie
                        :aosa alkuosa
                        :aet alkuetaisyys
                        :tunnus tunnus
                        :siltaid siltaid
                        :trex_oid trex_oid
                        :loppupvm loppupvm
                        :lakkautuspvm lakkautuspvm
                        :muutospvm muutospvm
                        :status status}]

    ;; AINEISTOON LIITTYVÄT HUOMIOT

    ; Ensimmäinen Harjassa tehty siltatarkastus: 2016-09-28 => Suodatetaan aineistosta mukaan vain ne sillat,
    ; joissa ei ole loppupäivämäärää tai joiden loppupäivämäärä on Harjan käyttöönoton jälkeen.
    ; Päivitettävä silta etsitään vanhalla Siltarekisterin siltaid:llä tai siltatunnuksella tai uudella Taitorakennerekisterin tunnuksella

    ; Loppupäivämäärä: Tietty ominaisuus, päätös tai asian tila päättyy tieverkolla sen loppupäivämääränä.   
    ; Lakkautuspäivämäärä: Lakkautuspäivämäärä on vain ja ainoastaan ajorataa ja tietä koskeva termi. Ajorataosuus on poistunut lakkautuspäivänä yleiseltä tieverkolta. Tämänkin hetken jälkeen tie saattaaa jäädä olemaan ja sille voidaan myöhemmin muuttaa ainakin teoriassa ominaisuuksia.  
    ; Kun ajorataosuus lakkautetaan, geometria poistuu tierekisteristä (=> aineistovirhe). Jotta geometria säilyy Harjassa, päivitetään lakkautetuista tieosuuksista ainoastaan lakkautuspäivämäärä.

    ; Silta_id on vanhan Siltarekisterin tunniste, nykyinen Taitorakennerekisteri yksilöi trex_oid-tiedolla. Silta_id voi siis jatkossa puuttua sillalta.
    ; Siltanumero on molempien id:n kanssa relevantti => Otetaan aineistoon mukaan vain sillat, joissa siltanumero on annettu.

    (if (not= nil siltanumero)
             (if (or (first (q-sillat/hae-silta-trex-idlla db {:trex_oid trex_oid}))
                     (first (q-sillat/hae-silta-idlla db {:siltaid siltaid :siltatunnus tunnus})))
               (q-sillat/paivita-silta! db sql-parametrit)
               (if (or (= loppupvm "")
                   (= loppupvm nil)
                   (pvm/ennen? (pvm/->pvm "28.9.2016") loppupvm))
               (q-sillat/luo-silta! db sql-parametrit))))))

  (defn vie-silta-entry [db silta]
    (luo-tai-paivita-silta db silta))

  (defn vie-sillat-kantaan [db shapefile]
    (if shapefile
      (let [kpl (atom 0)
            siltatietueet-shapefilesta (sort-by :objectid (shapefile/tuo shapefile))]
        (log/debug (str "Tuodaan sillat kantaan tiedostosta " shapefile))
        (jdbc/with-db-transaction [db db]
                                  (doseq [silta siltatietueet-shapefilesta]
                                    (when (true? (vie-silta-entry db silta))
                                      (swap! kpl inc))))
        (q-sillat/paivita-urakoiden-sillat db)
        (log/debug "Siltojen tuonti kantaan valmis, luettuja oli" (count siltatietueet-shapefilesta)))
      (log/debug "Siltojen tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))
