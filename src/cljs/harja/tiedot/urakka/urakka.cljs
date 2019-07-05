(ns harja.tiedot.urakka.urakka
  "MHU-urakoiden tila täällä. Hyvä olisi joskus saada muutkin tänne, yhden atomin alle."
  (:refer-clojure :exclude [atom])
  (:require [reagent.core :refer [atom]]))

(defonce tila (atom {:suunnittelu {:tehtavat
                                   {:tehtava-ja-maaraluettelo [{:id "rivin-id-1" :nimi "Laajenna" :tehtavaryhmatyyppi "ylataso" :maara 100 :vanhempi nil :piillotettu? false}
                                                               {:id "rivin-id-2" :nimi "Laajenna-valitaso" :tehtavaryhmatyyppi "valitaso" :maara 50 :vanhempi "rivin-id-1" :piillotettu? true}
                                                               {:id "rivin-id-3" :nimi "Teksti" :tehtavaryhmatyyppi "alitaso" :maara 100 :vanhempi "rivin-id-2" :piillotettu? true}
                                                               {:id "rivin-id-4" :nimi "Teksti 2" :tehtavaryhmatyyppi "alitaso" :maara 100 :vanhempi "rivin-id-2" :piillotettu? true}]}}}))

