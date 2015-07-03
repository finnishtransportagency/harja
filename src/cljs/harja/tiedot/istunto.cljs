(ns harja.tiedot.istunto
  "Harjan istunnon tiedot"
  (:require [harja.asiakas.tapahtumat :as t]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log]]
            
            [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))
 
(def kayttaja (atom nil))

(def kayttajan-nimi
  (reaction (when-let [k @kayttaja]
              (str (:etunimi k) " " (:sukunimi k)))))

(def istunto-alkoi (atom nil))

(defn- aseta-kayttaja [k]
  (reset! kayttaja k)
  (t/julkaise! (merge {:aihe :kayttajatiedot} k)))

(t/kuuntele! :harja-ladattu (fn []
                              (go
                                (aseta-kayttaja (<! (k/post! :kayttajatiedot
                                                             (reset! istunto-alkoi (js/Date.))))))))