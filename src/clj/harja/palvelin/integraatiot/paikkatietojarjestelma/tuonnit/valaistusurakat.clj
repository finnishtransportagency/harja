(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.valaistusurakat
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.urakat :as u]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]
            [clojure.string :as str])
   (:use [slingshot.slingshot :only [throw+]]))

(defn tuo-urakka [db geometria valaistusurakkanro]
  (if (and valaistusurakkanro (not (empty? (str/trim valaistusurakkanro))))
    (if geometria
      (let [geometria (.toString geometria)]
        (u/luo-valaistusurakka<! db geometria valaistusurakkanro))
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
                                                (when (= (:teemat urakka) "Valaistus") (tuo-urakka db (:the_geom urakka) (str (:urakkakood urakka)))))
                                    (when (= 0 (:lkm (first (u/tarkista-valaistusurakkadata db))))
                                          (throw (Exception. "Yhtään valaistusurakkaa ei viety kantaan. Tarkista aineiston yhteensopivuus sisäänlukevan kooditoteutuksen kanssa."))))))
        (throw (Exception. (format "Valaistusurakoiden geometrioiden tiedostopolkua %s ei löydy konfiguraatiosta. Tuontia ei suoriteta." shapefile)))))

