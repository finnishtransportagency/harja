(ns harja.ui.oikea-sivupalkki
  (:require [harja.loki :refer [log logt tarkkaile!]]))

(defn piirra
  "Leveys on sivupalkin leveys. Normaalisivupalkki on 600px ja normaalin päälle
  avattava palkki on 500 px. Järjestys numero on väliltä 1-2. Järejestys 1 on leveä normaali
  sivupalkki ja järjestys 2 on kapeampi normaalin päälle avattava sivupalkki.
  Anna bodyyn koko sivupalkin sisältö."
  [leveys jarjestys body]
  [:div.overlay-oikealla {:style {:width leveys :overflow "auto" "zIndex" (+ jarjestys 999)}}
   body])