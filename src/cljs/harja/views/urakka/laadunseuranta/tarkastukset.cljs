(ns harja.views.urakka.laadunseuranta.tarkastukset
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<! >! chan]]

            [harja.ui.komponentti :as komp]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.navigaatio :as nav]
            
            [harja.views.urakka.valinnat :as valinnat]
            [harja.pvm :as pvm]
            [harja.ui.grid :as grid]
            [harja.ui.napit :as napit]
            [harja.ui.kentat :refer [tee-kentta]])
  (:require-macros [reagent.ratom :refer [reaction]]))

(defonce tarkastustyyppi (atom nil)) ;; nil = kaikki, :tiesto, :talvihoito, :soratie
(defonce tienumero (atom nil)) ;; tienumero, tai kaikki

(def valittu-tarkastus (atom nil))



(defn tarkastuslistaus
  "Tarkastuksien pääkomponentti"
  []
  (komp/luo
   (fn []
     (let [urakka @nav/valittu-urakka]
       [:div.tarkastukset
        [valinnat/urakan-hoitokausi urakka]
        [valinnat/hoitokauden-aikavali urakka]

        [:span.label-ja-kentta
         [:span.kentan-otsikko "Tienumero"]
         [:div.kentta
          [tee-kentta {:tyyppi :numero :placeholder "Rajaa tienumerolla" :kokonaisluku? true} tienumero]]]
         
        
        [grid/grid
         {:otsikko "Tarkastukset"
          :tyhja "Ei tarkastuksia"}
         
         [{:otsikko "Pvm ja aika"
           :tyyppi :pvm-aika
           :nimi :aika}
          {:otsikko "Tyyppi"
           :nimi :tyyppi}
          ]

         []]]))))

(defn tarkastus [tarkastus]
  [:div.tarkastus
   [napit/takaisin "Takaisin tarkastusluetteloon" #(reset! tarkastus nil)]])

(defn tarkastukset
  "Tarkastuksien pääkomponentti"
  []
  (if @valittu-tarkastus
    [tarkastus valittu-tarkastus]
    [tarkastuslistaus]))
