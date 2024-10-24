(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.paallystyspalvelusopimukset
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.urakat :as u]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]
            [clojure.string :as str])
  (:use [slingshot.slingshot :only [throw+]]))

(defn tuo-urakka [db geometria urakkakoodi]
  (if (and urakkakoodi (not (empty? (str/trim urakkakoodi))))
    (if geometria
      (let [geometria (.toString geometria)]
        (u/luo-paallystyspalvelusopimus<! db geometria urakkakoodi))
      (log/warn (format "Palvelusopimusta (urakkakoodi: %s ei voida tuoda geometriaa, sillä se on tyhjä"
                  urakkakoodi)))
    (log/warn "Geometriaa ei voida tuoda ilman päällystyssopimusnumeroa")))

;; Urakoilla on Velhossa teema, joka kuvastaa urakkaa. Tässä setissä kuvattu teemat, jotka kiinnostavat meitä tässä kontekstissa.
(def haettavat-teemat #{"Päällysteiden ylläpito" "Päällystepaikkaukset"})

(defn vie-urakat-kantaan [db shapefile]
  (if shapefile
    (do
      (log/debug (str "Tuodaan päällystyksen palvelusopimukset kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [db db]
        (u/tuhoa-paallystyspalvelusopimusdata! db)
        (let [urakat (shapefile/tuo shapefile)]
          (doseq [urakka urakat]
            (when (haettavat-teemat (:teemat urakka))
              (tuo-urakka db (:the_geom urakka) (str (:urakkakood urakka))))))
        (when (= 0 (:lkm (first (u/tarkista-paallystyspalvelusopimusdata db))))
          (throw (Exception. "Yhtään päällystyspalvelusopimusta ei viety kantaan. Tarkista aineiston yhteensopivuus sisäänlukevan kooditoteutuksen kanssa.")))))
    (throw (Exception. (format "Päällystyksen palvelusopimusten geometrioiden tiedostopolkua %s ei löydy konfiguraatiosta. Tuontia ei suoriteta." shapefile)))))
