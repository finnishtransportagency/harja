(ns harja.ui.otsikkopaneeli
  "Geneerinen UI-elementti, joka piirtää avattavat/suljettavat otsikot.
   Otsikoiden alle voi sijoittaa vapaasti minkä tahansa komponentin"
  (:require [reagent.core :as r :refer [atom]]
            [harja.loki :refer [log]]
            [harja.ui.ikonit :as ikonit]
            [clojure.string :as str]))

(defn- toggle-paneeli! [index auki-index-atom]
  (if (= index @auki-index-atom)
    (reset! auki-index-atom nil)
    (reset! auki-index-atom index)))

(defn- maarita-alkutila [otsikot-ja-sisallot]
  (if (= (count (partition 3 otsikot-ja-sisallot)) 1)
    0 ;; Vain yksi otsikko, avaa se suoraan
    nil)) ;; Muuten käyttäjä saa avata itse :-)

(defn otsikkopaneeli
  "Optiot on map:
   paneelikomponentit             Vector mappeja, jossa avaimina sijainti ja sisalto.
                                  Sijainti annetaan prosentteina X-akselilla ja sisalto on funktio,
                                  joka palauttaa komponentin.

   otsikot-ja-sisallot            Tunniste, tekstiotsikko ja piirrettävä komponentti funktiona. Voi olla useita."
  [{:keys [paneelikomponentit otsikkoluokat] :as optiot} & otsikot-ja-sisallot]
  (r/with-let [auki-index-atom (atom (maarita-alkutila otsikot-ja-sisallot))]
    [:div.otsikkopaneeli.klikattava
     (doall
       (map-indexed
         (fn [index [tunniste otsikko sisalto]]
           (let [auki? (and auki-index-atom
                            (= index @auki-index-atom))]
             ^{:key tunniste}
             [:div
              [:div.otsikkopaneeli-otsikko {:on-click #(toggle-paneeli! index auki-index-atom)
                                            :class (str/join " " otsikkoluokat)}
               [:div.otsikkopaneeli-avausindikaattori (if auki?
                                                        (ikonit/livicon-minus)
                                                        (ikonit/livicon-plus))]
               [:div.otsikkopaneeli-otsikkoteksti otsikko]

               (map-indexed
                 (fn [i {:keys [sijainti sisalto]}]
                      ^{:key i}
                      [:div.otsikkopaneeli-custom-paneelikomponentti {:style {:top -2 :left sijainti}}
                       [sisalto {:index index
                                 :tunniste tunniste
                                 :otsikko otsikko
                                 :sisalto sisalto}]])
                 paneelikomponentit)]

              (when auki?
                [:div.otsikkopaneeli-sisalto sisalto])]))
         (partition 3 otsikot-ja-sisallot)))]))
