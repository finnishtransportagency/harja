(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.pohjavesialueet
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.pohjavesialueet :as p]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]))

(defn vie-pohjavesialue [db pohjavesialue]
  (if (:the_geom pohjavesialue)
    (let [nimi (:pvteksti pohjavesialue)
          tunnus (:pvnro pohjavesialue)
          suorarajoitus (= 1.0 (:pvsuola pohjavesialue))
          geometria (.toString (:the_geom pohjavesialue))]
      (p/luo-pohjavesialue! db nimi tunnus geometria suorarajoitus))
    (log/warn "Pohjavesialuetta ei voida tuoda ilman geometriaa. Virheviesti: " (:loc_error pohjavesialue))))

(defn vie-pohjavesialueet-kantaan [db shapefile]
  (if shapefile
    (do
      (log/debug (str "Tuodaan pohjavesialueet kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [db db]
        (log/debug "Poistetaan nykyiset pohjavesialueet")
        (p/poista-pohjavesialueet! db)
        (log/debug "Viedään kantaan uudet alueet")
        (doseq [pohjavesialue (shapefile/tuo shapefile)]
          (vie-pohjavesialue db pohjavesialue)))
      (p/paivita-pohjavesialueet db)
      (log/debug "Pohjavesialueiden tuonti kantaan valmis."))
    (log/debug "Pohjavesialueiden tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))
