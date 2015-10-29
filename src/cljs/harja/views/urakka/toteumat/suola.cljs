(ns harja.views.urakka.toteumat.suola
  "Suolankäytön toteumat hoidon alueurakoissa"
  (:require [reagent.core :refer [atom wrap]]
            [harja.tiedot.urakka.suola :as suola]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.komponentti :as komp])
  (:require-macros [reagent.ratom :refer [reaction]]))

(defn suolatoteumat []

  (komp/luo
   (komp/lippu suola/suolatoteumissa?)
   (fn []

     [:div.suolatoteumat
      "melkoista suolaamista"])))



