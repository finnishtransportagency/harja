(ns harja.tiedot.istunto
  "Harjan istunnon tiedot"
  (:require [harja.asiakas.tapahtumat :as t]
            [harja.asiakas.kommunikaatio :as k]
            [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))
 
(def kayttaja (atom nil))

(def istunto-alkoi (atom nil))

(defn- aseta-kayttaja [k]
  (reset! kayttaja k)
  (t/julkaise! (merge {:aihe :kayttajatiedot} k)))

(t/kuuntele! :harja-ladattu (fn []
                              (go
                                (aseta-kayttaja (<! (k/post! :kayttajatiedot
                                                             (reset! istunto-alkoi (js/Date.))))))))

