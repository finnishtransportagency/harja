(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.soratien-hoitoluokat
    (:require [taoensso.timbre :as log]
      [clojure.java.jdbc :as jdbc]
      [harja.kyselyt.hoitoluokat :as hoitoluokat]
      [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile] [harja.domain.hoitoluokat :as domain]))

(defn vie-hoitoluokka-entry [db soratie]
  (if (:the_geom soratie)
    (hoitoluokat/vie-hoitoluokkatauluun! db
                                         (:alkusijain soratie) ;; tienumero
                                         (:alkusijai0 soratie) ;; aosa
                                         (:alkusijai1 soratie) ;; aet
                                         (:loppusija0 soratie) ;; losa
                                         (:loppusija1 soratie) ;; let
                                         (:numero (first (filter #(= (:nimi %) (:soratieluo soratie)) domain/soratieluokat))) ;; hoitoluokka
                                         (.toString (:the_geom soratie))
                                         "soratie")
    (log/warn "Soratiehoitoluokkaa ei voida tuoda ilman geometriaa.")))

(defn vie-hoitoluokat-kantaan [db shapefile]
  (if shapefile
    (do
      (log/debug (str "Tuodaan soratiehoitoluokkatietoja kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [db db]
                                (hoitoluokat/tuhoa-hoitoluokkadata! db "soratie")
                                (doseq [soratie (shapefile/tuo shapefile)]
                                       (vie-hoitoluokka-entry db soratie))
                                (when (= 0 (:lkm (first (hoitoluokat/tarkista-hoitoluokkadata db "soratie"))))
                                      (throw (Exception. "Yhtään soratiehoitoluokkageometriaa ei viety kantaan. Tarkista aineiston yhteensopivuus sisäänlukevan kooditoteutuksen kanssa.")))))
    (throw (Exception. (format "Soratiehoitoluokkatietojen geometrioiden tiedostopolkua % ei löydy konfiguraatiosta. Tuontia ei suoriteta." shapefile)))))
