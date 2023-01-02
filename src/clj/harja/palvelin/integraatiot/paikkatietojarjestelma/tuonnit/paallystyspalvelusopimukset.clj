(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.paallystyspalvelusopimukset
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.urakat :as u]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]
            [clojure.string :as str])
   (:use [slingshot.slingshot :only [throw+]]))

(defn tuo-urakka [db geometria paallystyssopimusnro]
  (if (and paallystyssopimusnro (not (empty? (str/trim paallystyssopimusnro))))
    (if geometria
      (let [paallystyssopimusnro (when (and paallystyssopimusnro (not (empty? (str/trim paallystyssopimusnro))))
                                   (str (int (Double/parseDouble paallystyssopimusnro))))
            geometria (.toString geometria)]
        (u/luo-paallystyspalvelusopimus<! db geometria paallystyssopimusnro))
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
                                                (when (= (:teemat urakka) "Päällysteiden ylläpito") (tuo-urakka db (:the_geom urakka) (str (:urakkakood urakka))))))
                                    (when (= 0 (:lkm (first (u/tarkista-paallystyspalvelusopimusdata db))))
                                          (throw (Exception. "Yhtään päällystyspalvelusopimusta ei viety kantaan. Tarkista aineiston yhteensopivuus sisäänlukevan kooditoteutuksen kanssa.")))))
        (throw (Exception. (format "Päällystyksen palvelusopimusten geometrioiden tiedostopolkua % ei löydy konfiguraatiosta. Tuontia ei suoriteta." shapefile)))))
