(ns harja.kyselyt.yllapitokohteet
  (:require [jeesql.core :refer [defqueries]]
            [harja.geo :as geo]
            [jeesql.postgres :as postgres]))

(defqueries "harja/kyselyt/yllapitokohteet.sql"
  ;; PENDING: ylläpitokohteen poiston päättely on edelleen melko hidas.
  ;; Nyt tehdään yksi kysely, joka hakee kaikki urakan kohteiden linkitykset.
  ;; Jätetään tämä jatkoa varten, ota raportointi käyttöön testauksessa.
  #_{:report-slow-queries postgres/report-slow-queries})

(def kohdeosa-xf (geo/muunna-pg-tulokset :sijainti))

(defn liita-kohdeosat-kohteisiin
  ([db kohteet kohde-id-avain]
   (liita-kohdeosat-kohteisiin db kohteet kohde-id-avain nil))
  ([db kohteet kohde-id-avain {:keys [alue toleranssi]}]
   (let [idt (map kohde-id-avain kohteet)
         kohdeosat (into []
                         kohdeosa-xf
                         (if alue
                           (hae-urakan-yllapitokohteiden-yllapitokohdeosat-alueelle
                            db (merge alue {:idt idt :toleranssi toleranssi}))
                           (hae-urakan-yllapitokohteiden-yllapitokohdeosat
                            db {:idt (map kohde-id-avain kohteet)})))]
     (mapv
      (fn [kohde]
        (let [kohteen-kohdeosat (filterv #(= (:yllapitokohde-id %) (kohde-id-avain kohde)) kohdeosat)]
          (assoc kohde :kohdeosat kohteen-kohdeosat)))
      kohteet))))
