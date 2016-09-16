(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.paallystyspalvelusopimukset
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [clj-time.periodic :refer [periodic-seq]]
            [chime :refer [chime-at]]
            [harja.kyselyt.urakat :as u]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]))

(defn tuo-urakka [db alueurakkanro geometria]
  (if alueurakkanro
    (if geometria
     (let [alueurakkanro (str alueurakkanro)
           geometria (.toString geometria)]
       (u/luo-valaistusurakka<! db alueurakkanro geometria))
     (log/warn (format "Palvelusopimusta (alueurakkanro: %s ei voida tuoda geometriaa, sillä se on tyhjä" alueurakkanro)))
    (log/warn "Geometriaa ei voida tuoda ilman alueurakkanumeroa")))

(defn vie-urakat-kantaan [db shapefile]
  (if shapefile
    (do
      (log/debug (str "Tuodaan päällystyksen palvelusopimukset kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [db db]
        (u/tuhoa-valaistusurakkadata! db)
        (let [urakat (shapefile/tuo shapefile)]
          (doseq [urakka urakat]
            (tuo-urakka db (:ualue urakka) (:the_geom urakka)))))
      (log/debug "Päällystyksen palvelusopimusten tuonti kantaan valmis."))
    (log/debug "Päällystyksen palvelusopimusten tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))
