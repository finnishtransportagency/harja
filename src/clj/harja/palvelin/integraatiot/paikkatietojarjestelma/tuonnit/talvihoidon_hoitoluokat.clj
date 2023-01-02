(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.talvihoidon-hoitoluokat
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.hoitoluokat :as hoitoluokat]
            [harja.domain.hoitoluokat :as domain]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]))


(defn vie-hoitoluokka-entry [db talvihoito]
      (when (= nil (:numero (first (filter #(= (:nimi %) (:talvihoito talvihoito)) domain/talvihoitoluokat))))
            (println "HLNIMI" (:talvihoito talvihoito)))
  (if (:the_geom talvihoito)
    (hoitoluokat/vie-hoitoluokkatauluun! db
                                         (:alkusijain talvihoito) ;; tienumero
                                         (:alkusijai0 talvihoito) ;; aosa
                                         (:alkusijai1 talvihoito) ;; aet
                                         (:loppusija0 talvihoito) ;; losa
                                         (:loppusija1 talvihoito) ;; let
                                         (:numero (first (filter #(= (:nimi %) (:talvihoito talvihoito)) domain/talvihoitoluokat))) ;; hoitoluokka
                                         (.toString (:the_geom talvihoito))
                                         "talvihoito")
    (log/warn "Talvihoitoluokkaa ei voida tuoda ilman geometriaa.")))

(defn vie-hoitoluokat-kantaan [db shapefile]
  (if shapefile
    (do
      (log/debug (str "Tuodaan talvihoitoluokkatietoja kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [db db]
                                (hoitoluokat/tuhoa-hoitoluokkadata! db "talvihoito")
                                (doseq [soratie (shapefile/tuo shapefile)]
                                       (vie-hoitoluokka-entry db soratie))
                                (when (= 0 (:lkm (first (hoitoluokat/tarkista-hoitoluokkadata db "talvihoito"))))
                                      (throw (Exception. "Yhtään talvihoitoluokkageometriaa ei viety kantaan. Tarkista aineiston yhteensopivuus sisäänlukevan kooditoteutuksen kanssa.")))))
    (throw (Exception. (format "Talvihoitoluokkatietojen geometrioiden tiedostopolkua % ei löydy konfiguraatiosta. Tuontia ei suoriteta." shapefile)))))
