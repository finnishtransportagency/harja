(ns harja.kyselyt.tehtavat-maarat-kyselyt
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/tehtavat_maarat_kyselyt.sql"
  {:positional? true})

(declare hae-maaramitattavat-tehtavat
  hae-tarjous-tehtava-idlla
  hae-tarjouksen-tehtavamaarien-viimeisin-muokkaaja
  paivita-tarjous-tehtava<! lisaa-tarjous-tehtava<!)

(defn tallenna-tarjouksen-tehtavat-ja-maarat [db urakka-id kayttaja-id tehtavat]
  ;; Filtteröidään valiotsikot pois
  (doseq [{:keys [tehtava_id tarjous_maara nimi] :as tehtava} (filter #(nil? (:valiotsikko %)) tehtavat)]
    (let [dbtehtava (first (hae-tarjous-tehtava-idlla db {:tehtavaid tehtava_id
                                                          :urakkaid urakka-id}))
          dbvastaus (if dbtehtava
                      ;; Tehtävä löytyy kannasta
                      (paivita-tarjous-tehtava<! db {:tarjous_tehtava_id (:id dbtehtava)
                                                     :urakkaid urakka-id
                                                     :maara tarjous_maara
                                                     :muokkaaja kayttaja-id})
                      ;; Lisätään uutena
                      (lisaa-tarjous-tehtava<! db {:tehtavaid tehtava_id
                                                   :urakkaid urakka-id
                                                   :maara tarjous_maara
                                                   :luoja kayttaja-id}))])))

(defn hae-tehtavat-ja-maarat
  [db urakka-id]
  (let [tehtavat (hae-maaramitattavat-tehtavat db {:urakkaid urakka-id})
        ;; Jaotellaan tehtävät tehtävryhmäotsikon alle
        tehtavaryhman-tehtavat (group-by :tehtavaryhmaotsikko tehtavat)
        tehtavaryhman-tehtavat (sort-by :nimi
                                 (mapv (fn [rivi]
                                         {:nimi (first rivi)
                                          :valiotsikko (first rivi)
                                          :tehtavat (second rivi)})
                                   tehtavaryhman-tehtavat))
        tehtavaryhman-tehtavat (map-indexed (fn [idx rivi]
                                              (assoc rivi :jarjestys (inc idx)))
                                 tehtavaryhman-tehtavat)

        tehtavat (reduce (fn [lopulliset tehtavaryhma]
                           (let [t-rivi {:nimi (:nimi tehtavaryhma)
                                         :jarjestys (:jarjestys tehtavaryhma)
                                         :valiotsikko (:valiotsikko tehtavaryhma)}
                                 tehtavat (:tehtavat tehtavaryhma)
                                 uudet (concat [t-rivi] tehtavat)
                                 lopulliset (concat lopulliset uudet)]
                             lopulliset))
                   [] tehtavaryhman-tehtavat)
        tehtavat (map-indexed (fn [idx rivi]
                                (assoc rivi :jarjestys (inc idx)))
                   tehtavat)
        viimeisin-muokkaus (first (hae-tarjouksen-tehtavamaarien-viimeisin-muokkaaja db {:urakkaid urakka-id}))]
    {:tehtavat tehtavat
     :tehtavaryhman-tehtavat tehtavaryhman-tehtavat
     :viimeisin-muokkaus (:viimeisin_muokkaus viimeisin-muokkaus)
     :viimeisin-muokkaaja (:viimeisin_muokkaaja viimeisin-muokkaus)}))
