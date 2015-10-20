(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.pohjavesialue
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [clj-time.periodic :refer [periodic-seq]]
            [chime :refer [chime-at]]
            [harja.kyselyt.pohjavesialueet :as p]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]))

(defn vie-pohjavesialue-entry [db pohjavesialue]
  (if (:the_geom pohjavesialue)
    (p/vie-pohjavesialuetauluun! db
                                 (:urakka_lyh pohjavesialue)
                                 (:urakka_id pohjavesialue)
                                 (.toString (:the_geom pohjavesialue)))
    (log/warn "Pohjavesialuetta ei voida tuoda ilman geometriaa. Virheviesti: " (:loc_error pohjavesialue))))

(defn vie-pohjavesialue-kantaan [db shapefile]
  (if shapefile
    (do
      (log/debug (str "Tuodaan pohjavesialuetta kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [transaktio db]
        (p/tuhoa-pohjavesialuedata! transaktio)
        (doseq [pohjavesialue (shapefile/tuo shapefile)]
          (vie-pohjavesialue-entry transaktio pohjavesialue)))
      (p/paivita-hallintayksikoiden-pohjavesialueet db)
      (log/debug "Pohjavesialueen tuonti kantaan valmis."))
    (log/debug "Pohjavesialueen tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))
