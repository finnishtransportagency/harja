(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.paallystyspalvelusopimukset
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.urakat :as u]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]
            [clojure.string :as str]))

(defn tuo-urakka [db alueurakkanro geometria paallystyssopimusnro]
  (if (and paallystyssopimusnro (not (empty? paallystyssopimusnro)))
    (if geometria
      (let [alueurakkanro (when (and alueurakkanro (not (str/blank? alueurakkanro)))
                            (str (int (Double/parseDouble alueurakkanro))))
            paallystyssopimusnro (str (int (Double/parseDouble paallystyssopimusnro)))
            geometria (.toString geometria)]
        (u/luo-paallystyspalvelusopimus<! db alueurakkanro geometria paallystyssopimusnro))
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
            (tuo-urakka db (str (:ualue urakka)) (:the_geom urakka) (str (:paalurak urakka))))))
      (log/debug "Päällystyksen palvelusopimusten tuonti kantaan valmis."))
    (log/debug "Päällystyksen palvelusopimusten tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))
