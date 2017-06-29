(ns harja.ui.varmista-kayttajalta
  "Modaali jossa käyttäjältä varmistetaan tehdäänkö toiminto"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.napit :as napit]))

(defn varmista-kayttajalta [{:keys [otsikko sisalto toiminto-fn hyvaksy]}]
  "Suorittaa annetun toiminnon vain, jos käyttäjä hyväksyy sen.

  Parametrimap:
  :otsikko = dialogin otsikko
  :sisalto = dialogin sisältö
  :hyvaksy = hyväksyntäpainikkeen teksti tai elementti
  :toiminto-fn = varsinainen toiminto, joka ajetaan käyttäjän hyväksyessä"
  (nayta! {:otsikko otsikko
           :footer [:span
                    [napit/peruuta "Peruuta" #(piilota!)]
                    [napit/hyvaksy hyvaksy #(do
                                              (piilota!)
                                              (toiminto-fn))]]}
          sisalto))