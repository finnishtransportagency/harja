(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.alueurakat
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.urakat :as u]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]))

(defn string-intiksi [str]
  (if (string? str)
    (let [ei-numeeriset-poistettu (re-find #"\d+" str)]
      (if (nil? ei-numeeriset-poistettu)
        nil
        (Integer. ei-numeeriset-poistettu)))
    str))

(defn luo-tai-paivita-urakka [db urakka]
  (let [urakkanumero (str (:gridcode urakka))
        geometria (.toString (:the_geom urakka))
        piirinumero (string-intiksi (:piirinro urakka))
        elynimi (or (:elyn_nimi urakka) "")
        nimi (or (:urakka_nim urakka) "") ]
    (if (first (u/hae-alueurakka-numerolla db urakkanumero))
      (u/paivita-alueurakka! db geometria piirinumero urakkanumero elynimi nimi)
      (u/luo-alueurakka<! db urakkanumero geometria piirinumero elynimi nimi))
    (u/paivita-alue-urakalle! db geometria urakkanumero)))

(defn vie-urakka-entry [db urakka]
  (if (:the_geom urakka)
    (luo-tai-paivita-urakka db urakka)
    (virheet/heita-poikkeus virheet/+puutteellinen-paikkatietoaineisto+
                    [{:koodi virheet/+puuttuva-geometria-alueurakassa+
                              :viesti (format "Alueurakasta (id: %s.) puuttuu geometria. Tarkista aineisto. Alueurakoita ei päivitetä lainkaan." (:urakka_nimi urakka))}])))

(defn vie-urakat-kantaan [db shapefile]
  (if shapefile
    (do
      (log/debug (str "Tuodaan urakat kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [db db]
                                (u/tuhoa-alueurakkadata! db)
                                (doseq [urakka (shapefile/tuo shapefile)]
                                  (vie-urakka-entry db urakka)))
      (u/paivita-urakka-alueiden-nakyma db)
      (log/debug "Alueurakoiden tuonti kantaan valmis."))
    (log/debug "Alueurakoiden tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))
