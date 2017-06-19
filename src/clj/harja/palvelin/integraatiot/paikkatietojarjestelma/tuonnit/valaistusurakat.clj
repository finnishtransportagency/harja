(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.valaistusurakat
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.urakat :as u]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]
            [clojure.string :as str]))

(defn tuo-urakka [db alueurakkanro geometria valaistusurakkanro]
  (if (and valaistusurakkanro (not (empty? valaistusurakkanro)))
    (if geometria
      (let [alueurakkanro (when (and alueurakkanro (not (str/blank? alueurakkanro)))
                            (str (int (Double/parseDouble alueurakkanro))))
            valaistusurakkanro (str (int (Double/parseDouble valaistusurakkanro)))
            geometria (.toString geometria)]
        (u/luo-valaistusurakka<! db alueurakkanro geometria valaistusurakkanro))
      (log/warn (format "Urakkalle (valaistusurakkanro: %s ei voida tuoda geometriaa, sillä se on tyhjä"
                        valaistusurakkanro)))
    (log/warn "Geometriaa ei voida tuoda ilman valaistusurakkanumeroa")))

(defn vie-urakat-kantaan [db shapefile]
  (if shapefile
    (do
      (log/debug (str "Tuodaan valaistusurakat kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [db db]
        (u/tuhoa-valaistusurakkadata! db)
        (let [urakat (shapefile/tuo shapefile)]
          (doseq [urakka urakat]
            (tuo-urakka db (str (:ualue urakka)) (:the_geom urakka) (str (:valourak urakka))))))
      (log/debug "Valaistusurakoiden tuonti kantaan valmis."))
    (log/debug "Valaistusurakoiden tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))
