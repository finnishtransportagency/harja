(ns harja.views.raportit
  "Harjan raporttien pääsivu."
  (:require [reagent.core :refer [atom] :as reagent]
            [harja.ui.komponentti :as komp]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.lomake :as lomake]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.pvm :as pvm]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.yleiset :refer [livi-pudotusvalikko]]
            [harja.fmt :as fmt]))

(defonce valittu-raportti (atom nil))
(defonce valittu-raporttityyppi (atom nil))

(def +raporttityypit+
  ; HUOM: Hardcoodattu testidata vectori mappeja, tämä saadaan myöhemmin kannasta
  [{:nimi :laskutusyhteenveto
    :otsikko "Yks.hint. töiden toteumat -raportti"
    :konteksti #{:urakka}
    :parametrit
    [{:otsikko  "Hoitokausi"
      :nimi     :hoitokausi
      :tyyppi   :valinta
      :valinnat :valitun-urakan-hoitokaudet}
     {:otsikko  "Kuukausi"
      :nimi     :kuukausi
      :tyyppi   :valinta
      :valinnat :valitun-aikavalin-kuukaudet}]
    :suorita (constantly nil)}])

(tarkkaile! "[RAPORTTI] Valittu-raportti" valittu-raportti)
(tarkkaile! "[RAPORTTI] Valittu-raporttityyppi" valittu-raporttityyppi)

(defn lomake-kentta [kentta lomakkeen-tiedot]
  (if (= :valinta (:tyyppi kentta))
    (case (:valinnat kentta)
      :valitun-urakan-hoitokaudet
      (assoc kentta :valinnat @u/valitun-urakan-hoitokaudet
                    :valinta-nayta fmt/pvm-vali-opt)
      :valitun-aikavalin-kuukaudet
      (assoc kentta :valinnat (if-let [hk (:hoitokausi lomakkeen-tiedot)]
                                (pvm/hoitokauden-kuukausivalit hk)
                                [])
                    :valinta-nayta (comp fmt/pvm-opt first)))

    kentta))

(defn raporttinakyma []
  [:div "Tänne tulee myöhemmin raporttinäkymä..."])

(defn raporttivalinnat
  []
  (let [lomakkeen-tiedot (atom nil)
        lomakkeen-virheet (atom nil)]
    (komp/luo
      (fn []
        [:div.raportit
         [:div.label-ja-alasveto
          [:span.alasvedon-otsikko "Valitse raportti"]
          [livi-pudotusvalikko {:valinta    @valittu-raporttityyppi
                                ;;\u2014 on väliviivan unikoodi
                                :format-fn  #(if % (second %) "Valitse")
                                :valitse-fn #(reset! valittu-raporttityyppi %)
                                :class      "valitse-raportti-alasveto"}
           +raporttityypit+]]

         (when @valittu-raporttityyppi
           [lomake/lomake
            {:luokka   :horizontal
             :virheet  lomakkeen-virheet
             :muokkaa! (fn [uusi]
                         (reset! lomakkeen-tiedot uusi))}
            (let [tiedot @lomakkeen-tiedot
                  kentat (into []
                               (concat
                                 [{:otsikko "Kohde" :nimi :kohteen-nimi :hae #(:nimi @nav/valittu-urakka) :muokattava? (constantly false)}]
                                 (map (fn [kentta] (lomake-kentta kentta tiedot)) (:parametrit @valittu-raporttityyppi))))]
              kentat)

            @lomakkeen-tiedot])]))))

(defn raportit []
  (komp/luo
    (fn [] ; FIXME Urakan oltava valittuna, muuten ei toimi. Valitse hallintayksikkö -komponentti voisi olla myös täällä geneerisenä komponenttina.
      (if @valittu-raportti
        [raporttinakyma]
        [raporttivalinnat]))))
