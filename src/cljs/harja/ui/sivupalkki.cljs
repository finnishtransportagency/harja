(ns harja.ui.sivupalkki
  (:require [harja.loki :refer [log logt tarkkaile!]]))

(defn oikea
  "Leveys on sivupalkin leveys. Normaalisivupalkki on 600px ja normaalin päälle
  avattava palkki on 570px. Järjestys numero on väliltä 1-2. Järejestys 1 on leveä normaali
  sivupalkki ja järjestys 2 on kapeampi normaalin päälle avattava sivupalkki.
  Anna komponentti -parametrisa koko sivupalkin sisältö."
  [{:keys [leveys jarjestys luokka sulku-fn] :as opts} komponentti]
  (let [body [:div {:class (or luokka "overlay-oikealla")
                       :style {:width leveys :overflow "auto" "zIndex" (+ jarjestys 999)}}
              komponentti]]
    (if sulku-fn
      [:div 
       [:div {:style {:position "fixed" :left "0" :top "0" :height "100%"
                      :width (str (- (-> js/window .-innerWidth) (js/parseInt leveys)) "px") }
              :on-click sulku-fn}]
       body]
      body)))
