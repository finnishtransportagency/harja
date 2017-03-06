(ns harja.kyselyt.tienakyma
  (:require [jeesql.core :refer [defqueries]]
            [harja.geo :as geo]
            [harja.kyselyt.konversio :as konv]))

(def muunna-tehtavat
  (comp (fn [rivi]
          (update rivi :tehtavat
                  (fn [tehtavat]
                    (mapv #(konv/pgobject->map %
                                               :toimenpidekoodi :long
                                               :maara :double
                                               :yksikko :string
                                               :toimenpide :string) tehtavat))))
        #(konv/array->vec % :tehtavat)))

(def muunna-toteuma
  (comp muunna-tehtavat geo/muunna-reitti konv/alaviiva->rakenne))

(def muunna-korjaavat-toimenpiteet
  (comp (fn [rivi]
          (update rivi :korjaavattoimenpiteet
                  (fn [korjaavat]
                    (mapv #(konv/pgobject->map %
                                               :id :long
                                               :kuvaus :string
                                               :suoritettu :date)
                          korjaavat))))
        #(konv/array->vec % :korjaavattoimenpiteet)))

(def muunna-turvallisuuspoikkeama
  (comp muunna-korjaavat-toimenpiteet
        konv/alaviiva->rakenne))

(defqueries "harja/kyselyt/tienakyma.sql")
