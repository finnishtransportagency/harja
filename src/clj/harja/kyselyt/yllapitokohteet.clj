(ns harja.kyselyt.yllapitokohteet
  (:require [jeesql.core :refer [defqueries]]
            [harja.geo :as geo]
            [jeesql.postgres :as postgres]
            [harja.kyselyt.konversio :as konv]
            [clojure.set :as set]
            [specql.core :refer [fetch update! insert! upsert! delete!]]
            [harja.domain.paallystysilmoitus :as paallystysilmoitus]))

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
                            db {:idt idt})))]
     (mapv
      (fn [kohde]
        (let [kohteen-kohdeosat (filterv #(= (:yllapitokohde-id %) (kohde-id-avain kohde)) kohdeosat)]
          (assoc kohde :kohdeosat kohteen-kohdeosat)))
      kohteet))))

(defn liita-paikkaukset-paikkauskohteisiin
  [db kohteet kohde-id-avain {:keys [alue toleranssi alkupvm loppupvm]}]
  (let [idt (map kohde-id-avain kohteet)
        kohdeosat (into []
                        kohdeosa-xf
                        (if alue
                          (hae-paikkauskohteen-paikkaukset-alueelle
                            db (merge alue {:idt idt :toleranssi toleranssi
                                            :alkupvm alkupvm
                                            :loppupvm loppupvm}))
                          (hae-paikkauskohteen-paikkaukset
                            db {:idt idt
                                :alkupvm alkupvm
                                :loppupvm loppupvm})))]
    (mapv
      (fn [kohde]
        (let [kohteen-kohdeosat (filterv #(= (:yllapitokohde-id %) (kohde-id-avain kohde)) kohdeosat)]
          (assoc kohde :kohdeosat kohteen-kohdeosat)))
      kohteet)))

(defn yllapitokohteiden-tiedot-sahkopostilahetykseen [db kohde-idt]
  (let [tiedot (into []
                     (comp
                       (map #(konv/array->set % :sahkopostitiedot_muut-vastaanottajat))
                       (map konv/alaviiva->rakenne))
                     (hae-yllapitokohteiden-tiedot-sahkopostilahetykseen db {:idt kohde-idt}))]
    tiedot))


(defn hae-urakan-paallystysmassat
  [db hakuehdot]
  (fetch db
         ::paallystysilmoitus/paallystysmassa
         paallystysilmoitus/paallystysmassan-tiedot
         hakuehdot))

(defn tallenna-urakan-paallystysmassa
  [db hakuehdot]
  (insert! db
         ::paallystysilmoitus/paallystysmassa
         hakuehdot))