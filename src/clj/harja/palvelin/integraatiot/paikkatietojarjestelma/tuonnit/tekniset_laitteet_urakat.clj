(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.tekniset-laitteet-urakat
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]
            [harja.kyselyt.urakat :as urakat]))

(defn vie-urakka-entry [db {:keys [the_geom urakkanro] :as urakka}]
  (if the_geom
    (do
      (urakat/luo-tekniset-laitteet-urakka<! db urakkanro (str the_geom))
      (urakat/paivita-alue-urakalle! db (str the_geom) urakkanro))
    (log/warn "Tekniset laitteet urakkaa ei voida tuoda ilman geometriaa. Virheviesti: " (:loc_error urakka))))

(defn vie-tekniset-laitteet-urakat-kantaan [db shapefile]
  (if shapefile
    (do
      (log/debug (str "Tuodaan tekniset laitteet urakat kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [db db]
        (urakat/tuhoa-tekniset-laitteet-urakkadata! db)
        (doseq [urakka (shapefile/tuo shapefile)]
          (vie-urakka-entry db urakka))
        (log/debug "Tekniset laitteet urakoiden tuonti kantaan valmis")))
    (log/debug "Tekniset laitteet urakoiden tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))
