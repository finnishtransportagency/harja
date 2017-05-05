(ns harja.ui.otsikkopaneeli
  "Geneerinen UI-elementti, joka piirtää avattavat/suljettavat otsikot.
   Otsikoiden alle voi sijoittaa vapaasti minkä tahansa komponentin"
  (:require [reagent.core :as r :refer [atom]]
            [harja.loki :refer [log]]))

(defn- avaa-paneeli! [index auki-index-atom]
  (reset! auki-index-atom index))

(defn otsikkopaneeli
  "Optiot on map:
   paneelikomponentit             Vector mappeja, jossa avaimina sijainti ja sisalto.
                                  Sijainti annetaan prosentteina X-akselilla ja sisalto on funktio,
                                  joka palauttaa komponentin.

   otsikot-ja-sisallot            Tunniste, tekstiotsikko ja piirrettävä komponentti funktiona. Voi olla useita."
  [{:keys [paneelikomponentit] :as optiot} & otsikot-ja-sisallot]
  (r/with-let [auki-index-atom (atom 0)]
    [:div.otsikkopaneeli
     (doall
       (map-indexed
         (fn [index [tunniste otsikko sisalto]]
           (let [auki? (= index @auki-index-atom)]
             ^{:key tunniste}
             [:div
              [:div.otsikkopaneeli-otsikko {:on-click #(avaa-paneeli! index auki-index-atom)}
               otsikko
               (for [{:keys [sijainti sisalto]} paneelikomponentit]
                 ^{:key (hash sisalto)}
                 [:div.otsikkopaneeli-custom-paneelikomponentti {:style {:top -2 :left sijainti}}
                  [sisalto {:index index
                            :tunniste tunniste
                            :otsikko otsikko
                            :sisalto sisalto}]])]
              (when auki?
                [:div.otsikkopaneeli-sisalto [sisalto]])]))
         (partition 3 otsikot-ja-sisallot)))]))