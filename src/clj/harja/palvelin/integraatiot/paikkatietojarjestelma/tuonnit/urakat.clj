(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.urakat
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [clj-time.periodic :refer [periodic-seq]]
            [chime :refer [chime-at]]
            [harja.kyselyt.urakat :as u]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]))

; FIXME Kesken...

#_(defn paivita-silta [db silta]
  (s/paivita-silta-siltanumerolla! db
                       (:siltatyy silta)
                       (int (:nro silta))
                       (:nimi silta)
                       (.toString (:the_geom silta))
                       (:tie silta)
                       (:aosa silta)
                       (:aet silta)))

#_(defn luo-silta [db silta]
  (s/vie-siltatauluun! db
                       (:siltatyy silta)
                       (int (:nro silta))
                       (:nimi silta)
                       (.toString (:the_geom silta))
                       (:tie silta)
                       (:aosa silta)
                       (:aet silta)))

#_(defn luo-tai-paivita-urakka [db urakka]
  (if-let [urakka-kannassa (first (s/hae-silta-numerolla db (:nro urakka)))]
    (paivita-silta db urakka)
    (luo-silta db urakka)))

#_(defn vie-urakka-entry [db urakka]
  (if (:the_geom urakka)
    (luo-tai-paivita-urakka db urakka)
    (log/warn "Urakka ei voida tuoda ilman geometriaa. Virheviesti: " (:loc_error urakka))))

(defn vie-urakat-kantaan [db shapefile]
  (if shapefile
    (do
      (log/debug (str "Tuodaan urakat kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [transaktio db]
                                (doseq [urakka (shapefile/tuo shapefile)]
                                  #_(vie-urakka-entry transaktio urakka)))
      (log/debug "Urakoiden tuonti kantaan valmis."))
    (log/debug "Urakoiden tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))
