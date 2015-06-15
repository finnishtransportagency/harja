(ns harja.views.urakka.valinnat
  "Yleiset urakkaan liittyvät valintakomponentit."
  (:require [reagent.core :refer [atom] :as r]
            [bootstrap :as bs]
            
            [harja.tiedot.urakka :as u]
            [harja.pvm :as pvm]
            [harja.ui.pvm :as ui-pvm]
            [harja.loki :refer [log]]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.ui.yleiset :refer [livi-pudotusvalikko]]))

(defn urakan-sopimus [ur]
  [:div.label-ja-alasveto
   [:span.alasvedon-otsikko "Sopimusnumero"]
   [livi-pudotusvalikko {:valinta @u/valittu-sopimusnumero
                         :format-fn second
                         :valitse-fn u/valitse-sopimusnumero!
                         :class "suunnittelu-alasveto"
                         }
    (:sopimukset ur)]])

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

(defn hoitokauden-aikavali [ur]
                                        ; TODO Harjaa vastaava tyyli tälle
  (let [valittu-aikavali u/valittu-aikavali]
    [:span.label-ja-aikavali
     [:span.alasvedon-otsikko "Aikaväli"]
     [:div.aikavali-valinnat
      [tee-kentta {:tyyppi :pvm :lomake? true}
       (r/wrap (first @valittu-aikavali)
               (fn [uusi-arvo]
                 (reset! valittu-aikavali [uusi-arvo
                                           (second (pvm/kuukauden-aikavali uusi-arvo))])))]
      [:span " \u2014 "]
      [tee-kentta {:tyyppi :pvm :lomake? true}
       (r/wrap (second @valittu-aikavali)
               (fn [uusi-arvo]
                 (swap! valittu-aikavali (fn [[alku _]] [alku uusi-arvo]))))]
      ]]))

(defn urakan-toimenpide []
  [:div.label-ja-alasveto
   [:span.alasvedon-otsikko "Toimenpide"]
   [livi-pudotusvalikko {:valinta    @u/valittu-toimenpideinstanssi
                         ;;\u2014 on väliviivan unikoodi
                         :format-fn  #(if % (str (:tpi_nimi %)) "Ei toimenpidettä")
                         :valitse-fn u/valitse-toimenpideinstanssi!}
    @u/urakan-toimenpideinstanssit]])

(defn urakan-sopimus-ja-hoitokausi [ur]
  [:span
   [urakan-sopimus ur]
   [urakan-hoitokausi ur]])

(defn urakan-sopimus-ja-toimenpide [ur]
  [:span
   [urakan-sopimus ur]
   [urakan-toimenpide ur]])

(defn urakan-sopimus-ja-hoitokausi-ja-toimenpide [ur]
  [:span
   [urakan-sopimus-ja-hoitokausi ur]
   [urakan-toimenpide ur]])

(defn urakan-sopimus-ja-hoitokausi-ja-aikavali-ja-toimenpide [ur]
  [:span
   [urakan-sopimus-ja-hoitokausi ur]
   [hoitokauden-aikavali ur]
   [urakan-toimenpide ur]])
