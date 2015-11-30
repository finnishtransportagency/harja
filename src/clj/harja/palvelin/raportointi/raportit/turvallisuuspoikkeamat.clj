(ns harja.palvelin.raportointi.raportit.turvallisuuspoikkeamat)

(defn suorita [db user {:keys [urakka-id hallintayksikko-id
                               aikavali-alkupvm aikavali-loppupvm toimenpide-id] :as parametrit}]
  [:raportti "Foo"
   [:teksti "BAR"]])
