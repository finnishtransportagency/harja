(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.pohjavesialueet
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [clj-time.periodic :refer [periodic-seq]]
            [chime :refer [chime-at]]
            [harja.kyselyt.pohjavesialueet :as p]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]))

(defn vie-pohjavesialue [db pohjavesialue]
  (if (:the_geom pohjavesialue)
    (let [nimi (:urakka_lyh pohjavesialue)
          tunnus (:urakka_id pohjavesialue)
          ulkoinen-id (int (:id pohjavesialue))
          geometria (.toString (:the_geom pohjavesialue))]
      (if (p/onko-olemassa-ulkoisella-idlla? db ulkoinen-id)
        (p/paivita-pohjavesialue! db nimi tunnus geometria ulkoinen-id)
        (p/luo-pohjavesialue! db nimi tunnus ulkoinen-id geometria)))
    (log/warn "Pohjavesialuetta ei voida tuoda ilman geometriaa. Virheviesti: " (:loc_error pohjavesialue))))

(defn vie-pohjavesialue-kantaan [db shapefile]
  (if shapefile
    (do
      (log/debug (str "Tuodaan pohjavesialuetta kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [transaktio db]
        (doseq [pohjavesialue (shapefile/tuo shapefile)]
          (vie-pohjavesialue transaktio pohjavesialue)))
      (p/paivita-pohjavesialueet db)
      (log/debug "Pohjavesialueen tuonti kantaan valmis."))
    (log/debug "Pohjavesialueen tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))
