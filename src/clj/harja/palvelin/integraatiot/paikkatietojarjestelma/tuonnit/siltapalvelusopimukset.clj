(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.siltapalvelusopimukset
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]
            [harja.kyselyt.urakat :as urakat]))

(defn vie-urakka-entry [db {:keys [the_geom siltapalve] :as urakka}]
  (if the_geom
    (do
      (urakat/luo-siltapalvelusopimus<! db siltapalve (str the_geom))
      (u/paivita-alue-urakalle! db geometria valaistusurakkanro))
    (log/warn "Siltapalvelusopimusta ei voida tuoda ilman geometriaa. Virheviesti: " (:loc_error urakka))))

(defn vie-siltojen-palvelusopimukset-kantaan [db shapefile]
  (if shapefile
    (do
      (log/debug (str "Tuodaan siltapalvelusopimukset kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [db db]
        (urakat/tuhoa-siltapalvelusopimukset! db)
        (doseq [urakka (shapefile/tuo shapefile)]
          (vie-urakka-entry db urakka))
        (log/debug "Siltapalvelusopimusten tuonti kantaan valmis")))
    (log/debug "Siltapalvelusopimusten tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))
