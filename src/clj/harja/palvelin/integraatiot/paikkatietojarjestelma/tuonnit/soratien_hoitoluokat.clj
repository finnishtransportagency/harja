(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.soratien-hoitoluokat
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.hoitoluokat :as hoitoluokat]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]))

(defn vie-hoitoluokka-entry [db soratie]
  (if (:the_geom soratie)
    (let [kokonaisluvuksi #(when % (int %))]
      (hoitoluokat/vie-hoitoluokkatauluun!
        db
        (kokonaisluvuksi (:ajorata soratie))
        (kokonaisluvuksi (:aosa soratie))
        (kokonaisluvuksi (:tie soratie))
        (kokonaisluvuksi (:piirinro soratie))
        (kokonaisluvuksi (:let soratie))
        (kokonaisluvuksi (:losa soratie))
        (kokonaisluvuksi (:aet soratie))
        (kokonaisluvuksi (:osa soratie))
        (kokonaisluvuksi (:soratielk soratie))
        (str (:the_geom soratie))
        "soratie"))
    (log/warn "Soratiehoitoluokkaa ei voida tuoda ilman geometriaa. Virheviesti: " (:loc_error soratie))))

(defn vie-hoitoluokat-kantaan [db shapefile]
  (if shapefile
    (do
      (log/debug (str "Tuodaan soratiehoitoluokkatietoja kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [db db]
        (hoitoluokat/tuhoa-hoitoluokkadata! db "soratie")
        (doseq [soratie (shapefile/tuo shapefile)]
          (vie-hoitoluokka-entry db soratie))
        (log/debug "Soratiehoitoluokkatietojen tuonti kantaan valmis")))
    (log/debug "Soratiehoitoluokkatietojen tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))
