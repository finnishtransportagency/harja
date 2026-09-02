(ns harja.kyselyt.tehtavat-maarat-kyselyt
  (:require [harja.kyselyt.konversio :as konversio]
            [harja.tyokalut.big :as big]
            [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/tehtavat_maarat_kyselyt.sql"
  {:positional? true})

(declare hae-maaramitattavat-tehtavat
  hae-tarjous-tehtava-idlla
  hae-tarjouksen-tehtavamaarien-viimeisin-muokkaaja
  paivita-tarjous-tehtava<! lisaa-tarjous-tehtava<!)

(defn tallenna-tarjouksen-tehtavat-ja-maarat [db urakka-id kayttaja-id hk-alkuvuosi tehtavat]
  (doseq [{:keys [tehtava_id tarjous_maara]} (remove :valiotsikko tehtavat)]
    ;; Tallennetaan vain tehtävät, joille on annettu arvo.
    ;; Tämä estää tilanteen, jossa nil päätyy urakka_tehtavamaara.maara-kenttään.
    (when-some [tarjous-maara-arvo tarjous_maara]
      (let [tarjous-maara (bigdec tarjous-maara-arvo)
            dbtehtava (first (hae-tarjous-tehtava-idlla db {:tehtavaid tehtava_id
                                                            :urakkaid urakka-id
                                                            :hoitokauden-alkuvuosi hk-alkuvuosi}))
            db-maara (:maara dbtehtava)
            sama-maara? (big/bigdecimal-arvot-samat? tarjous-maara db-maara)]
        (cond
          ;; Ei päivitetä turhaan, jotta muokattu/muokkaaja eivät vääristy.
          sama-maara?
          nil

          ;; Tehtävä löytyy kannasta
          dbtehtava
          (paivita-tarjous-tehtava<! db {:tarjous_tehtava_id (:id dbtehtava)
                                         :urakkaid urakka-id
                                         :maara tarjous-maara
                                         :muokkaaja kayttaja-id})

          ;; Lisätään uutena
          :else
          (lisaa-tarjous-tehtava<! db {:tehtavaid tehtava_id
                                       :urakkaid urakka-id
                                       :maara tarjous-maara
                                       :luoja kayttaja-id
                                       :hoitokauden-alkuvuosi hk-alkuvuosi}))))))

(defn hae-tehtavat-ja-maarat
  [db urakka-id hoitokauden-alkuvuosi]
  (let [tehtavat (hae-maaramitattavat-tehtavat db {:urakkaid urakka-id
                                                   :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})
        ;; Mäppää tehtavan tietokantarivit clojure-mapeiksi.
        tehtavat (mapv
                   (fn [tehtava]
                     (-> tehtava
                       (assoc :muutokset
                         (if (:muutokset tehtava)
                           (mapv
                             (fn [k]
                               (konversio/pgobject->map k :id :long :edellinen_maara :double :maaramuutos :double
                                 :uusi_maara :double :tehtavaid :long :voimassa_alkaen :date :syy :string))
                             (konversio/pgarray->vector (:muutokset tehtava)))
                           []))))
                   tehtavat)

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
                                 ;; Lisää tehtävälle tieto, että mille väliotsikolle se kuuluu
                                 tehtavat (map #(assoc % :kuuluu (:valiotsikko tehtavaryhma)) tehtavat)
                                 uudet (concat [t-rivi] tehtavat)
                                 lopulliset (concat lopulliset uudet)]
                             lopulliset))
                   [] tehtavaryhman-tehtavat)
        tehtavat (map-indexed (fn [idx rivi]
                                (assoc rivi :jarjestys (inc idx)))
                   tehtavat)
        viimeisin-muokkaus (first (hae-tarjouksen-tehtavamaarien-viimeisin-muokkaaja db {:urakkaid urakka-id
                                                                                         :hoitokauden-alkuvuosi hoitokauden-alkuvuosi}))]
    {:tehtavat tehtavat
     :viimeisin-muokkaus (:viimeisin_muokkaus viimeisin-muokkaus)
     :viimeisin-muokkaaja (:viimeisin_muokkaaja viimeisin-muokkaus)}))
