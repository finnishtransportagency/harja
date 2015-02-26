(ns harja.views.indeksit
  "Indeksien hallinta."
  (:require [reagent.core :refer [atom] :as reagent]

            [harja.ui.grid :as grid]
            ))


(defn indeksit []
  [:div.indeksit "Indeksien hallinta"
   [grid/grid
      {:otsikko "MAKU 2005"
       :tyhja "Ei indeksitietoja"
       :tallenna #(tallenna-yhteyshenkilot ur yhteyshenkilot %)}
      [{:otsikko "Vuosi" :nimi :vuosi :tyyppi :valinta  :leveys "17%"
        :valinta-arvo identity
        :valinta-nayta #(if (nil? %) "- valitse -" %)
        
        :valinnat (vec (range 2015 2045))
        
        :validoi [[:ei-tyhja  "Anna indeksin vuosi"]]}
       
       {:otsikko "tammi" :nimi :tammikuu :tyyppi :numero :leveys "5%"}
       {:otsikko "helmi" :nimi :helmikuu :tyyppi :numero :leveys "5%"}
       {:otsikko "maalis" :nimi :maaliskuu :tyyppi :numero :leveys "5%"}
       {:otsikko "huhti" :nimi :huhtikuu :tyyppi :numero :leveys "5%"}
       {:otsikko "touko" :nimi :toukokuu :tyyppi :numero :leveys "5%"}
       {:otsikko "kesä" :nimi :kesakuu :tyyppi :numero :leveys "5%"}
       {:otsikko "heinä" :nimi :heinakuu :tyyppi :numero :leveys "5%"}
       {:otsikko "elo" :nimi :elokuu :tyyppi :numero :leveys "5%"}
       {:otsikko "syys" :nimi :syyskuu :tyyppi :numero :leveys "5%"}
       {:otsikko "loka" :nimi :lokakuu :tyyppi :numero :leveys "5%"}
       {:otsikko "marras" :nimi :marraskuu :tyyppi :numero :leveys "5%"}
       {:otsikko "joulu" :nimi :joulukuu :tyyppi :numero :leveys "5%"}
      ]
      []
      ] 
   [:hr]
   ]
  )

