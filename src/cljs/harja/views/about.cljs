(ns harja.views.about
  "Tietoja Harjasta, Liikennevirastosta ja mm. avoimen lähdekoodin tai ikonien
  attribuutiot"
  (:require [reagent.core :refer [atom] :as reagent]
            [harja.pvm :as pvm]
            [cljs-time.coerce :as tc]
            [harja.ui.grid :as grid])
  (:require-macros [harja.views.about :refer [lue-gitlog]]))

(defn pienenna-ja-laajenna-ikonien-attribuutio []
  [:li
   "Icons pienenna.svg, suurenna.svg, undo.svg and redo.svg made by "
   [:a {:href "http://catalinfertu.com" :title "Catalin Fertu"} "Catalin Fertu "]
   "from "
   [:a {:href "http://flaticon.com" :title "Flaticon"} "www.flaticon.com "]
   "are licensced by "
   [:a {:href "http://creativecommons.org/licenses/by/3.0/" :title "Creative Commons BY 3.0"} "CC BY 3.0"]])
                  
(defn attribuutiot []
  [pienenna-ja-laajenna-ikonien-attribuutio])

(defn harja-info []
  [:p "Tämä on Liikenneviraston Harja-palvelu, joka mahdollistaa alkuvaiheessa mm. teiden hoito- ja ylläpitourakoiden
      kustannusten suunnittelua ja toteutumien seurantaa."])

(defn gitlog []
  [grid/grid
   {:otsikko "Viimeisimmät muutokset" :voi-muokata? false}

   [{:otsikko "Pvm ja aika" :nimi :date :hae #(tc/from-long (tc/to-long (:date %))) :fmt pvm/pvm-aika :tyyppi :string :leveys "15%"}
    {:otsikko "Tiiviste" :nimi :hash  :tyyppi :string :leveys "15%"}
    {:otsikko "Tekijä" :nimi :author :tyyppi :string :leveys "20%"}
    {:otsikko "Otsikko" :nimi :title :tyyppi :string :leveys "50%"}]
 
   (lue-gitlog)])




(defn about []
  [:span
   [:div.section [:label "Yleistä"]
      [harja-info]]
   [:div.section [:label "Attribuutiot"]
   [:ul
    [attribuutiot]]]
   
    [gitlog]
   ])
