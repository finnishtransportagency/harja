(ns harja.palvelin.raportointi.raportit.tyomaakokous)

(defn suorita [db user {:keys [kuukausi urakka-id] :as tiedot}]
  [:raportti "Työmaakokousraportti"
   [:teksti "Foo"]])
