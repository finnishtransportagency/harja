(ns harja.views.urakka.laadunseuranta.sanktiot
  "Sanktioiden listaus"
  (:require [reagent.core :refer [atom] :as r]
            [harja.tiedot.navigaatio :as nav]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.tiedot.urakka.laadunseuranta :as laadunseuranta]
            [harja.tiedot.urakka :as urakka]
            [harja.ui.grid :as grid])
  (:require-macros [harja.atom :refer [reaction<!]]))


(def urakan-sanktiot
  (reaction<! [urakka (:id @nav/valittu-urakka)
               hoitokausi @urakka/valittu-hoitokausi
               laadunseurannassa? @laadunseuranta/laadunseurannassa?
               valilehti @laadunseuranta/valittu-valilehti]
              ;; Jos urakka ja hoitokausi on valittu ja käyttäjä on laadunseurannassa tällä välilehdellä,
              ;; haetaan urakalle sanktiot
              (when (and laadunseurannassa? (= :sanktiot valilehti)
                         hoitokausi urakka)
                (laadunseuranta/hae-urakan-sanktiot urakka hoitokausi))))
                  
                
(defn sanktiot
  "Päätason sanktiot välilehti: listaus urakassa annetuista sanktioista."
  []
  [:div.sanktiot
   [urakka-valinnat/urakan-hoitokausi @nav/valittu-urakka]

   [grid/grid
    {:otsikko "Sanktiot"
     :tyhja "Ei sanktioita"}

    [] ;;laadunseuranta/+sanktio-skeema+
     
    
    @urakan-sanktiot
    ]])


  

  
