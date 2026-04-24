(ns harja.palvelin.palvelut.muutos.muutos-apurit
  (:require [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.kulut :as kulu-kyselyt]
            [harja.palvelin.tyokalut.tyokalut :as tyokalut]
            [harja.kyselyt.urakat :as q-urakat]
            [harja.pvm :as pvm]))

(defn tavoitehinnan-muutos
  "Laskee rivin tavoitehinnan muutoksen. Sen sijainti vaihtelee tyyppikohtaisesti."
  ;; on hyvä saada tavoitehinnan muutos samaan avaimeen, niin summauslaskennat jne. toimivat myöhemmin suoraan
  [muutokset]
  (mapv (fn [rivi]
          (let [total (if (= (:tyyppi rivi)
                            "johto-ja-hallintokorvaus")
                        (or (:jjh-muutosten-summa rivi) 0)
                        (some->>
                          (:kustannusvaikutukset rivi)
                          (map :summa)
                          (reduce + 0)))]
            (assoc rivi :tavoitehinnan-muutos total)))
    muutokset))

(defn parsi-kirjatut-muutokset-vastaus [vastaus]
  (->> vastaus
    (mapv (fn [rivi]
            (-> rivi
              (update :alityyppi #(keyword %))
              (update :kustannusvaikutukset #(konv/jsonb->clojuremap %))
              (update :tehtavat_ja_maarat #(konv/jsonb->clojuremap %))
              (update :liitteet #(konv/jsonb->clojuremap %)))))
    (tavoitehinnan-muutos)))

(defn hae-laskutusrajan-tarkistukset
  [db urakka-id hoitokauden-alkuvuosi kirjatut-muutokset hoitovuoden-indeksikorjattu-tavoitehinta]
  (let [urakan-tiedot (first (q-urakat/hae-urakka db {:id urakka-id}))
        hoitokausinro (pvm/hoitokausivuosi->mhu-hoitovuosi-nro (:alkupvm urakan-tiedot) hoitokauden-alkuvuosi)
        laskutusraja-alkuperainen (:laskutusraja_alkuperainen
                                    (first (kulu-kyselyt/hae-urakan-alkuperainen_laskutusraja db {:urakka-id urakka-id :hoitokausinro hoitokausinro})))]
    (->> kirjatut-muutokset
      (sort-by :voimassa_alkaen)
      (filter (fn [m]
                (let [tyyppi    (:tyyppi m)
                      oma-summa (or (:tavoitehinnan-muutos m) 0)]
                  (or (= tyyppi "muutostyo")
                    (and (= tyyppi "pysyva") (pos? oma-summa))))))
      (reduce (fn [[tulos kertyma] m]
                (let [oma-summa     (or (:tavoitehinnan-muutos m) 0)
                      uusi-kertyma  (+ kertyma oma-summa)
                      prosenttiosuus (when (and hoitovuoden-indeksikorjattu-tavoitehinta
                                             (pos? hoitovuoden-indeksikorjattu-tavoitehinta))
                                       (tyokalut/pyorista-kahteen-decimaaliin
                                         (* 100.0 (/ (double uusi-kertyma)
                                                    (double hoitovuoden-indeksikorjattu-tavoitehinta)))))
                      laskutusrajan-tarkistus (if (and prosenttiosuus (>= prosenttiosuus 3.00))
                                                uusi-kertyma
                                                0)
                      tarkistettu-laskutusraja (when laskutusraja-alkuperainen
                                                 (+ laskutusraja-alkuperainen laskutusrajan-tarkistus))]
                  [(conj tulos
                     {:summa                    oma-summa
                      :yhteensa                 uusi-kertyma
                      :prosenttiosuus           prosenttiosuus
                      :laskutusrajan-tarkistus  laskutusrajan-tarkistus
                      :tarkistettu-laskutusraja tarkistettu-laskutusraja
                      :voimassa_alkaen          (:voimassa_alkaen m)
                      :tyyppi                   (:tyyppi m)
                      :id                       (:id m)})
                   uusi-kertyma]))
        [[] 0])
      first)))
