(ns harja.views.urakka.turvallisuus
  (:require [reagent.core :refer [atom]]
            [bootstrap :as bs]
            [harja.tiedot.urakka.turvallisuus :as tiedot]
            [harja.ui.komponentti :as komp]

            [harja.views.urakka.turvallisuus.turvallisuuspoikkeamat :as turvallisuuspoikkeamat]))

(defn turvallisuus []
  (komp/luo
    (komp/lippu tiedot/turvallisuus-valilehdella?)
    (fn []
      [bs/tabs
       {:active tiedot/valittu-valilehti}

       "Turvallisuuspoikkeamat"
       :turvallisuuspoikkeamat
       [turvallisuuspoikkeamat/turvallisuuspoikkeamat]

       ])))

