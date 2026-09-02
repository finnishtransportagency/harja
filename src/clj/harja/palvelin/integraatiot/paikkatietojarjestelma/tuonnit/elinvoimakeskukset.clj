(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.elinvoimakeskukset
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.organisaatiot :as organisaatio-kyselyt]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]))

(defn vie-elinvoimakeskukset-kantaan
  "Lisää elinvoimakeskusten geometria kantaan olemassa olevalle elinvoimakeskukselle."
  [db shapefile]
  (if shapefile
    (do
      (log/info (str "Tuodaan elinvoimakeskusten geometria kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [db db]
        (doseq [evk (shapefile/tuo shapefile)]
          (log/info "Tuodaan elinvoimakeskus geometria :: Lyhenne" (:elinvoima0 evk) " nimi:" (:elinvoimak evk) " numero: " (:id evk))
          (organisaatio-kyselyt/paivita-elinvoimakeskus-geometria! db {:lyhenne (:elinvoima0 evk)
                                                                       :alue (.toString (:the_geom evk))}))
        (log/info "Elinvoimakeskusten geometrian tuonti kantaan valmis")))
    (log/warn "Elinvoimakeskusten tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))
