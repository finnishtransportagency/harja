(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.pohjavesialue
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [clj-time.periodic :refer [periodic-seq]]
            [chime :refer [chime-at]]
            [harja.kyselyt.pohjavesialueet :as p]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]))

(defn vie-pohjavesialue-entry [db pohjavesialue]
  (p/vie-pohjavesialuetauluun! db (:nimi pohjavesialue)
                               (:tunnus pohjavesialue)
                               (.toString (:the_geom pohjavesialue))))

(defn vie-pohjavesialue-kantaan [db shapefile]
  (if shapefile
    (do
      (log/debug (str "Tuodaan pohjavesialuetta kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [transaktio db]
        (p/tuhoa-pohjavesialuedata! transaktio)
        (doseq [tv (shapefile/tuo shapefile)]
          (vie-pohjavesialue-entry transaktio tv)))
      (log/debug "Pohjavesialueen tuonti kantaan valmis."))
    (log/debug "Pohjavesialueen tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))
