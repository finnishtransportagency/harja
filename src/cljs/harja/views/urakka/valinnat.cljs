(ns harja.views.urakka.valinnat
  "Yleiset urakkaan liittyvät valintakomponentit."
  (:require [reagent.core :refer [atom] :as r]
            [bootstrap :as bs]
            
            [harja.tiedot.urakka.suunnittelu :as s]
            
            [harja.pvm :as pvm]
            [harja.loki :refer [log]]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija linkki sisalla? livi-pudotusvalikko]]))


(defn urakan-sopimus-ja-hoitokausi [ur]
  (let [hoitokaudet (s/hoitokaudet ur)]
    [:span
     [:div.label-ja-alasveto
      [:span.alasvedon-otsikko "Sopimusnumero"]
      [livi-pudotusvalikko {:valinta @s/valittu-sopimusnumero
                            :format-fn second
                            :valitse-fn s/valitse-sopimusnumero!
                            :class "suunnittelu-alasveto"
                            }
       (:sopimukset ur)
       ]]
     [:div.label-ja-alasveto
      [:span.alasvedon-otsikko (if (= :hoito (:tyyppi ur)) "Hoitokausi" "Sopimuskausi")]
      [livi-pudotusvalikko {:valinta @s/valittu-hoitokausi
                            ;;\u2014 on väliviivan unikoodi
                            :format-fn #(if % (str (pvm/pvm (first %))
                                                   " \u2014 " (pvm/pvm (second %))) "Valitse")
                            :valitse-fn s/valitse-hoitokausi!
                            :class "suunnittelu-alasveto"
                            }
       hoitokaudet]]]))
