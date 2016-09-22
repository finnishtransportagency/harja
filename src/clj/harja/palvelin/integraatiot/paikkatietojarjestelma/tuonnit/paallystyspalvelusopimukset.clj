(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.paallystyspalvelusopimukset
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [clj-time.periodic :refer [periodic-seq]]
            [chime :refer [chime-at]]
            [harja.kyselyt.urakat :as u]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]))

(defn tuo-urakka [db alueurakkanro geometria paallystyssopimus]
  (if alueurakkanro
    (if geometria
      (let [alueurakkanro (str alueurakkanro)
            geometria (.toString geometria)]
        (u/luo-paallystyspalvelusopimus<! db alueurakkanro geometria paallystyssopimus))
      (log/warn (format "Palvelusopimusta (alueurakkanro: %s ei voida tuoda geometriaa, sillä se on tyhjä" alueurakkanro)))
    (log/warn "Geometriaa ei voida tuoda ilman alueurakkanumeroa")))

(defn vie-urakat-kantaan [db shapefile]
  (if shapefile
    (do
      (log/debug (str "Tuodaan päällystyksen palvelusopimukset kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [db db]
        (u/tuhoa-paallystyspalvelusopimusdata! db)
        (let [urakat (shapefile/tuo shapefile)]
          (doseq [urakka urakat]
            (tuo-urakka db (:ualue urakka) (:the_geom urakka) (str (:paalurakka urakka))))))
      (log/debug "Päällystyksen palvelusopimusten tuonti kantaan valmis."))
    (log/debug "Päällystyksen palvelusopimusten tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))
