(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.tieverkko
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [clj-time.periodic :refer [periodic-seq]]
            [chime :refer [chime-at]]
            [harja.kyselyt.tieverkko :as k]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]))

(defn vie-tieverkko-entry [db tv]
  ;; todo: Onko ok, jos rivejä missä tätä tietoa ei ole ei tuoda?
  (when (:osoite3 tv)
    (k/vie-tieverkkotauluun! db (:osoite3 tv) (:tie tv) (:ajorata tv) (:osa tv) (:tiepiiri tv) (:tr_pituus tv) (.toString (:the_geom tv)))))

(defn vie-tieverkko-kantaan [db shapefile]
  (if shapefile
    (do
      (log/debug (str "Tuodaan tieosoiteverkkoa kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [transaktio db]
        (k/tuhoa-tieverkkodata! transaktio)
        (doseq [tv (shapefile/tuo shapefile)]
          (vie-tieverkko-entry transaktio tv)))
      (k/paivita-paloiteltu-tieverkko! db)
      (log/debug "Tieosoiteverkon tuonti kantaan valmis."))
    (log/debug "Tieosoiteverkon tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))
