(ns harja.ui.sivupalkki
  (:require [harja.loki :refer [log logt tarkkaile!]]))

(defn oikea
  "Leveys on sivupalkin leveys. Normaalisivupalkki on 600px ja normaalin päälle
  avattava palkki on 570px. Järjestys numero on väliltä 1-2. Järejestys 1 on leveä normaali
  sivupalkki ja järjestys 2 on kapeampi normaalin päälle avattava sivupalkki.
  Anna komponentti -parametrisa koko sivupalkin sisältö."
  [{:keys [leveys jarjestys luokka] :as opts} komponentti]
  [:div {:class (or luokka "overlay-oikealla")
         :style {:width leveys :overflow "auto" "zIndex" (+ jarjestys 999)}}
   komponentti])