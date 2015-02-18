(ns harja.tiedot.istunto
  "Harjan istunnon tiedot"
  (:require [harja.asiakas.tapahtumat :as t]
            [harja.asiakas.kommunikaatio :as k]
            [reagent.core :refer [atom]]))
 
(def kayttaja (atom nil))

(def istunto-alkoi (atom nil))

(defn- aseta-kayttaja [k]
  (reset! kayttaja k)
  (t/julkaise! (merge {:aihe :kayttajatiedot} k)))

(t/kuuntele! :harja-ladattu (fn []
                              (go (aseta-kayttaja (<! (k/post! :kayttajatiedot
                                                               (reset! istunto-alkoi (js/Date.))))))))

