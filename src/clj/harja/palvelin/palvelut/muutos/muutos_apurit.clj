(ns harja.palvelin.palvelut.muutos.muutos-apurit
  (:require [harja.kyselyt.konversio :as konv]))

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
