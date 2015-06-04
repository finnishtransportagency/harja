(ns harja.views.urakka.valinnat
  "Yleiset urakkaan liittyvät valintakomponentit."
  (:require [reagent.core :refer [atom] :as r]
            [bootstrap :as bs]
            
            [harja.tiedot.urakka :as u]
            [harja.pvm :as pvm]
            [harja.loki :refer [log]]
            [harja.ui.yleiset :refer [livi-pudotusvalikko]]))

(defn urakan-hoitokausi [ur]
  (let [hoitokaudet (u/hoitokaudet ur)]
    [:div.label-ja-alasveto
     [:span.alasvedon-otsikko (if (= :hoito (:tyyppi ur)) "Hoitokausi" "Sopimuskausi")]
     [livi-pudotusvalikko {:valinta @u/valittu-hoitokausi
                           ;;\u2014 on väliviivan unikoodi
                           :format-fn #(if % (str (pvm/pvm (first %))
                                                  " \u2014 " (pvm/pvm (second %))) "Valitse")
                           :valitse-fn u/valitse-hoitokausi!
                           :class "suunnittelu-alasveto"
                           }
      hoitokaudet]]))

(defn urakan-sopimus-ja-hoitokausi [ur]
  [:span
   [:div.label-ja-alasveto
    [:span.alasvedon-otsikko "Sopimusnumero"]
    [livi-pudotusvalikko {:valinta @u/valittu-sopimusnumero
                          :format-fn second
                          :valitse-fn u/valitse-sopimusnumero!
                          :class "suunnittelu-alasveto"
                          }
     (:sopimukset ur)]]
   [urakan-hoitokausi ur]])

(defn urakan-sopimus-ja-hoitokausi-ja-toimenpide [ur]
  [:span
  (urakan-sopimus-ja-hoitokausi ur)
  [:div.label-ja-alasveto
   [:span.alasvedon-otsikko "Toimenpide"]
   [livi-pudotusvalikko {:valinta    @u/valittu-toimenpideinstanssi
                         ;;\u2014 on väliviivan unikoodi
                         :format-fn  #(if % (str (:tpi_nimi %)) "Ei toimenpidettä")
                         :valitse-fn u/valitse-toimenpideinstanssi!}
    @u/urakan-toimenpideinstanssit]]]
  )

(defn urakan-sopimus-ja-toimenpide [ur]
  [:span
   [:div.label-ja-alasveto
    [:span.alasvedon-otsikko "Sopimusnumero"]
    [livi-pudotusvalikko {:valinta @u/valittu-sopimusnumero
                          :format-fn second
                          :valitse-fn u/valitse-sopimusnumero!
                          :class "suunnittelu-alasveto"
                          }
     (:sopimukset ur)]]
   [:div.label-ja-alasveto
    [:span.alasvedon-otsikko "Toimenpide"]
    [livi-pudotusvalikko {:valinta    @u/valittu-toimenpideinstanssi
                          ;;\u2014 on väliviivan unikoodi
                          :format-fn  #(if % (str (:tpi_nimi %)) "Ei toimenpidettä")
                          :valitse-fn u/valitse-toimenpideinstanssi!}
     @u/urakan-toimenpideinstanssit]]]
  )
