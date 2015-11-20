(ns harja.kyselyt.raportit
  (:require [yesql.core :refer [defqueries]]
            [harja.kyselyt.konversio :as konv]))

(defqueries "harja/kyselyt/raportit.sql")


(defn raportit
  "Hakee kaikki raportit ja niiden parametrit, evaluoi raportin suorituskoodin.
   Tässä funktiossa ei ole virheenkäsittelyä, vaan raportin evaluointivirheet tulee
   catchata ylemmällä tasolla.
   Käytä tätä raa'an hae-raportit funktion sijasta."
  [db]
  (into {}
        (comp
         (map #(konv/array->set % :konteksti))
         (map (fn [raportti]
                (as-> raportti r
                  (dissoc r :id)
                  (assoc r
                         :nimi (keyword (:nimi r))
                         :urakkatyyppi (keyword (:urakkatyyppi r))
                         :parametrit (map #(dissoc % :id) (:parametrit r))))))
         (map (juxt (comp keyword :nimi)
                    (fn [raportti]
                      (assoc raportti
                             :suorita (eval (read-string (:koodi raportti))))))))

        (konv/sarakkeet-vektoriin
         (into []
               (comp (map konv/alaviiva->rakenne))
               (hae-raportit db))
         {:parametri :parametrit})))

