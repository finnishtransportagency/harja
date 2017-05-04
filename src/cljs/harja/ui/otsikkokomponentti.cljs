(ns harja.ui.otsikkokomponentti
  "Geneerinen UI-elementti, joka piirtää avattavat/suljettavat otsikot.
   Otsikoiden alle voi sijoittaa vapaasti minkä tahansa komponentin"
  (:require [reagent.core :as r :refer [atom]]))


(defn otsikot [otsikot-ja-sisallot optiot]
  (let [otsikko-ja-sisalto-parit (partition 2 otsikot-ja-sisallot)]
    [:div.otsikkokomponentti
     (for [[otsikko sisalto] otsikko-ja-sisalto-parit]
       ^{:key otsikko}
       [:div
        [:div.otsikkokomponentti-otsikko otsikko]
        [:div.otsikkokomponentti-sisalto sisalto]])]))
