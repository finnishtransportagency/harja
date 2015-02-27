(ns harja.views.indeksit
  "Indeksien hallinta."
  (:require [reagent.core :refer [atom] :as reagent]
            [cljs.core.async :refer [<! >! chan close!]]
            
            [harja.ui.yleiset :as yleiset]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log]]
            [harja.ui.grid :as grid]
            )
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def indeksit (atom nil))
 
 (defn hae-indeksit []
   (if (empty? @indeksit)
           (go (reset! indeksit
               (<! (k/get! :indeksit))))))

 
 (defn indeksi-grid [indeksin-nimi]
   [grid/grid
        {:otsikko indeksin-nimi
         :tyhja (if (nil? @indeksit) [yleiset/ajax-loader "Indeksejä haetaan..."] "Ei indeksitietoja")
         :tallenna #(tallenna-indeksit ur yhteyshenkilot %)}
        [{:otsikko "Vuosi" :nimi :vuosi :tyyppi :valinta  :leveys "17%"
          :valinta-arvo identity
          :valinta-nayta #(if (nil? %) "- valitse -" %)
          
          :valinnat (vec (range 2015 2045))
          
          :validoi [[:ei-tyhja  "Anna indeksin vuosi"]]}
         
         {:otsikko "tammi" :nimi 1 :tyyppi :numero :leveys "7%"}
         {:otsikko "helmi" :nimi 2 :tyyppi :numero :leveys "7%"}
         {:otsikko "maalis" :nimi 3 :tyyppi :numero :leveys "7%"}
         {:otsikko "huhti" :nimi 4 :tyyppi :numero :leveys "7%"}
         {:otsikko "touko" :nimi 5 :tyyppi :numero :leveys "7%"}
         {:otsikko "kesä" :nimi 6 :tyyppi :numero :leveys "7%"}
         {:otsikko "heinä" :nimi 7 :tyyppi :numero :leveys "7%"}
         {:otsikko "elo" :nimi 8 :tyyppi :numero :leveys "7%"}
         {:otsikko "syys" :nimi 9 :tyyppi :numero :leveys "7%"}
         {:otsikko "loka" :nimi 10 :tyyppi :numero :leveys "7%"}
         {:otsikko "marras" :nimi 11 :tyyppi :numero :leveys "7%"}
         {:otsikko "joulu" :nimi 12 :tyyppi :numero :leveys "7%"}
         ]
        (map second (filter (fn [[[nimi _] _]]
                           (= nimi indeksin-nimi)
                           ) @indeksit))
        ])
 
(defn indeksit-elementti []
    (hae-indeksit)
      [:span.indeksit
       [indeksi-grid "MAKU 2005"]
       [:hr]
       [indeksi-grid "MAKU 2010"]
       
       ])

