(ns harja.kyselyt.raportit
  (:require [jeesql.core :refer [defqueries]]
            [harja.kyselyt.konversio :as konv]))

(defqueries "harja/kyselyt/raportit.sql"
  {:positional? true})


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
                       (konv/string->keyword r :nimi)
                       (assoc r :parametrit (map #(dissoc % :id) (:parametrit r)))
                       (konv/array->keyword-set r :urakkatyyppi))))
          (map (juxt (comp keyword :nimi)
                     (fn [raportti]
                       (assoc raportti
                         :suorita (eval (read-string (:koodi raportti))))))))

        (konv/sarakkeet-vektoriin
          (into []
                (comp (map konv/alaviiva->rakenne))
                (hae-raportit db))
          {:parametri :parametrit})))
