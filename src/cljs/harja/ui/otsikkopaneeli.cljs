(ns harja.ui.otsikkopaneeli
  "Geneerinen UI-elementti, joka piirtää avattavat/suljettavat otsikot.
   Otsikoiden alle voi sijoittaa vapaasti minkä tahansa komponentin"
  (:require [reagent.core :as r :refer [atom]]
            [harja.loki :refer [log]]))

(defn- avaa-paneeli! [index auki-index-atom]
  (reset! auki-index-atom index))

(defn otsikot [otsikot-ja-sisallot optiot]
  (r/with-let [otsikko-ja-sisalto-parit (partition 2 otsikot-ja-sisallot)
               auki-index-atom (atom 0)]
    [:div.otsikkopaneeli
     (doall
       (map-indexed
         (fn [index [otsikko sisalto]]
           (let [auki? (= index @auki-index-atom)]
             ^{:key otsikko}
             [:div
              [:div.otsikkopaneeli-otsikko {:on-click #(avaa-paneeli! index auki-index-atom)}
               otsikko]
              (when auki?
                [:div.otsikkopaneeli-sisalto [sisalto]])]))
         otsikko-ja-sisalto-parit))]))
