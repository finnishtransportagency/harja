(ns harja.kyselyt.rahavaraukset
  (:require [harja.tyokalut.yleiset :as yleiset]
            [harja.kyselyt.muutos-kyselyt :as muutos-kyselyt]
            [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/rahavaraukset.sql"
  {:positional? true})

(declare hae-urakan-rahavaraukset-ja-tehtavaryhmat hae-rahavarauksen-tehtavaryhmat hae-urakan-rahavaraukset
  hae-rahavarauksen-toimenpideinstanssi hae-rahavaraukset hae-urakoiden-rahavaraukset hae-rahavaraukset-tehtavineen
  kuuluuko-tehtava-rahavaraukselle? onko-tehtava-olemassa? onko-rahavaraus-olemassa?
  hae-rahavaraukselle-mahdolliset-tehtavat hae-urakan-rahavaraus paivita-urakan-rahavaraus<!
  lisaa-urakan-rahavaraus<! poista-urakan-rahavaraus<! lisaa-uusi-rahavaraus<! lisaa-rahavaraukselle-tehtava<!
  poista-rahavaraukselta-tehtava! onko-rahavaraus-kaytossa? poista-rahavaraus-urakoilta!
  poista-rahavarauksen-tehtavat! poista-rahavaraus! listaa-rahavaraukset-analytiikalle
  hae-urakan-suunnitellut-rahavarausten-kustannukset)

(defn- rahavarausten-summarivi [rahavaraukset]
  (let [{:keys [summa-indeksikorjattu toteumat]}
        (reduce (fn [acc {:keys [summa-indeksikorjattu toteumat]}]
                  {:summa-indeksikorjattu (+ (:summa-indeksikorjattu acc 0)
                                            (or summa-indeksikorjattu 0))
                   :toteumat (+ (:toteumat acc 0)
                               (or toteumat 0))})
          {}
          rahavaraukset)
        ;; Jos rahavaraukset vektori on tyhjä, nil arvoja voi olla
        summa-indeksikorjattu (or summa-indeksikorjattu 0)
        toteumat (or toteumat 0)]
    {:id :yhteenveto
     :summa-indeksikorjattu summa-indeksikorjattu
     :toteumat toteumat
     :tavoitehinnan-muutos (- toteumat summa-indeksikorjattu)}))

(defn muutosten-rahavaraukset [db urakka-id hoitokauden-alkuvuosi]
  (let [rahavarausten-suunnitelmat (map
                                     #(select-keys % [:id :nimi :summa-indeksikorjattu])
                                     (hae-urakan-suunnitellut-rahavarausten-kustannukset db {:urakka_id urakka-id
                                                                                                                ;; haetaan vain valitulle hoitovuodelle
                                                                                                                :alkuvuosi hoitokauden-alkuvuosi
                                                                                                                :loppuvuosi (inc hoitokauden-alkuvuosi)}))
        rahavarausten-toteumat (muutos-kyselyt/rahavarausten-toteumat db {:urakka urakka-id
                                                                          :hoitokauden_alkuvuosi hoitokauden-alkuvuosi})
        rahavaraukset (yleiset/yhdista-mapit-avaimella rahavarausten-suunnitelmat rahavarausten-toteumat :id)
        rahavarausmuutosten-syyt (muutos-kyselyt/rahavarausmuutosten-syyt db {:urakka urakka-id
                                                                              :hoitokauden_alkuvuosi hoitokauden-alkuvuosi})
        rahavaraukset (yleiset/yhdista-mapit-avaimella rahavaraukset rahavarausmuutosten-syyt :id)
        rahavaraukset (mapv
                        ;; lasketaan suunnitellun ja toteutuneen määrän erotus, vaikka toteutunut määrä olisi null
                        #(cond
                           (and (:summa-indeksikorjattu %) (:toteumat %))
                           (assoc % :tavoitehinnan-muutos (- (:toteumat %) (:summa-indeksikorjattu %)))

                           (:summa-indeksikorjattu %)
                           (assoc % :tavoitehinnan-muutos (- (:summa-indeksikorjattu %)))
                           :else %)
                        rahavaraukset)
        rahavaraukset-yhteensa (rahavarausten-summarivi rahavaraukset)
        rahavaraukset (conj rahavaraukset rahavaraukset-yhteensa)]
    rahavaraukset))
