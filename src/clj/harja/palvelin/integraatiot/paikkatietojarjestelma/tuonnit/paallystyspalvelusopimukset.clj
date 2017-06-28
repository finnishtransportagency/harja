(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.paallystyspalvelusopimukset
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.urakat :as u]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]))

(defn tuo-urakka [db alueurakkanro geometria paallystyssopimusnro]
  (if paallystyssopimusnro
    (if geometria
      (let [alueurakkanro (str alueurakkanro)
            paallystyssopimusnro (str (int (Double/parseDouble paallystyssopimusnro)))
            geometria (.toString geometria)]
        (u/luo-paallystyspalvelusopimus<! db alueurakkanro geometria paallystyssopimusnro)
        (u/paivita-alue-urakalle! db geometria paallystyssopimusnro))
      (log/warn (format "Palvelusopimusta (paallystyssopimusnro: %s ei voida tuoda geometriaa, sillä se on tyhjä"
                        paallystyssopimusnro)))
    (log/warn "Geometriaa ei voida tuoda ilman päällystyssopimusnumeroa")))

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
