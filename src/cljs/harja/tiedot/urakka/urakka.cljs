(ns harja.tiedot.urakka.urakka
  "MHU-urakoiden tila täällä. Hyvä olisi joskus saada muutkin tänne, yhden atomin alle."
  (:refer-clojure :exclude [atom])
  (:require [reagent.core :refer [atom cursor]]))

(defonce tila (atom {:suunnittelu {:tehtavat
                                   {:tehtava-ja-maaraluettelo [{:id "rivin-id-1" :nimi "Laajenna" :tehtavaryhmatyyppi "ylataso" :maara 100 :vanhempi nil :piillotettu? false}
                                                               {:id "rivin-id-2" :nimi "Laajenna-valitaso" :tehtavaryhmatyyppi "valitaso" :maara 50 :vanhempi "rivin-id-1" :piillotettu? true}
                                                               {:id "rivin-id-3" :nimi "Teksti 2" :tehtavaryhmatyyppi "alitaso" :maara 100 :vanhempi "rivin-id-2" :piillotettu? true}
                                                               {:id "rivin-id-4" :nimi "Teksti 1" :tehtavaryhmatyyppi "alitaso" :maara 100 :vanhempi "rivin-id-2" :piillotettu? true}
                                                               {:id "rivin-id-5" :nimi "Laajenna-valitaso" :tehtavaryhmatyyppi "valitaso" :maara 150 :vanhempi "rivin-id-1" :piillotettu? true}
                                                               {:id "rivin-id-6" :nimi "Teksti 3" :tehtavaryhmatyyppi "alitaso" :maara 150 :vanhempi "rivin-id-5" :piillotettu? true}]}}}))

(defonce suunnittelu-tehtavat-tila (cursor tila [:suunnittelu :tehtavat]))
