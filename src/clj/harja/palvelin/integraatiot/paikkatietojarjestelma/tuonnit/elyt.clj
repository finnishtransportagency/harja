(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.elyt
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.tyokalut.elyt :as ely-tyokalut]
            [harja.domain.ely :as ely]
            [harja.kyselyt.organisaatiot :as o]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]))

(defn luo-tai-paivita-ely [db ely]
  (let [nimi (:nimi ely)
        lyhenne (ely/elynumero->lyhenne (:numero ely))
        liikennemuoto "T"
        numero (:numero ely)
        geometria (.toString (:the_geom ely))]
    (if (first (o/hae-ely db (:numero ely)))
      (o/paivita-ely! db nimi lyhenne liikennemuoto numero geometria)
      (o/luo-ely<! db nimi lyhenne liikennemuoto numero geometria))))

(defn vie-ely-entry [db ely]
  (if (:the_geom ely)
    (luo-tai-paivita-ely db ely)
    (log/warn "ELY-aluetta ei voida tuoda ilman geometriaa. Virheviesti: " (:loc_error ely))))

(defn vie-elyt-kantaan [db shapefile]
  (if shapefile
    (do
      (log/debug (str "Tuodaan ELYt kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [db db]
        (doseq [ely (shapefile/tuo shapefile)]
          (vie-ely-entry db (-> ely (assoc :numero (Integer. (:numero ely))))))
        (log/debug "ELYjen tuonti kantaan valmis")))
    (log/debug "ELYjen tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))
