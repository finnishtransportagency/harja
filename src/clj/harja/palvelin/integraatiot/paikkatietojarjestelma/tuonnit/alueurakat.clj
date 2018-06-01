(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.alueurakat
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.urakat :as u]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]))

(defn luo-tai-paivita-urakka [db urakka]
  (let [urakkanumero (str (:gridcode urakka))
        geometria (.toString (:the_geom urakka))
        piirinumero (int (:piirinro urakka))
        elynimi (if (:elyn_nimi urakka) (:elyn_nimi urakka) "")
        nimi (if (:urakka_nim urakka) (:urakka_nim urakka) "") ]
    (if (first (u/hae-alueurakka-numerolla db (str (:gridcode urakka))))
      (u/paivita-alueurakka! db geometria piirinumero urakkanumero elynimi nimi)
      (u/luo-alueurakka<! db urakkanumero geometria piirinumero elynimi nimi))
    (u/paivita-alue-urakalle! db geometria urakkanumero)))

(defn vie-urakka-entry [db urakka]
  (if (and (:the_geom urakka) (:piirinro urakka))
    (luo-tai-paivita-urakka db urakka)
    (log/warn "Alueurakkaa ei voida tuoda ilman geometriaa ja piirinumeroa. Virheviesti: " (:loc_error urakka))))

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
