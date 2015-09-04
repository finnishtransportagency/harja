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
  ; HUOM: Hardcoodattu testidata vectori mappeja
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

(defn tee-lomakekentta [kentta lomakkeen-tiedot]
  (log "[RAPORTTI] Kenttä: " (pr-str kentta))
  (log "[RAPORTTI] Lomakkeen tiedot: " (pr-str lomakkeen-tiedot))
  (if (= :valinta (:tyyppi kentta))
    (case (:valinnat kentta)
      :valitun-urakan-hoitokaudet
      (assoc kentta :valinnat @u/valitun-urakan-hoitokaudet
                    :valinta-nayta fmt/pvm-vali-opt)
      :valitun-aikavalin-kuukaudet
      (assoc kentta :valinnat (if-let [hk (:hoitokausi lomakkeen-tiedot)]
                                (pvm/hoitokauden-kuukausivalit hk) ; FIXME Näytä kuukaudet tekstinä "Tammikuu, Helmikuu jne."
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
                                :format-fn  #(if % (:otsikko %) "Valitse")
                                :valitse-fn #(reset! valittu-raporttityyppi %)
                                :class      "valitse-raportti-alasveto"}
           +raporttityypit+]]

         (when @valittu-raporttityyppi
           [lomake/lomake
            {:luokka   :horizontal
             :virheet  lomakkeen-virheet
             :muokkaa! (fn [uusi]
                         (reset! lomakkeen-tiedot uusi))}
            (let [lomake-tiedot @lomakkeen-tiedot
                  kentat (into []
                               (concat
                                 [{:otsikko "Kohde" :nimi :kohteen-nimi :hae #(:nimi @nav/valittu-urakka) :muokattava? (constantly false)}]
                                 (map
                                   (fn [kentta]
                                     (tee-lomakekentta kentta lomake-tiedot))
                                   (:parametrit @valittu-raporttityyppi))))]
              kentat)

            @lomakkeen-tiedot])]))))

(defn raportit []
  (komp/luo
    (fn [] ; FIXME Urakan oltava valittuna, muuten ei toimi. Valitse hallintayksikkö -komponentti voisi olla myös täällä geneerisenä komponenttina.
      (if @valittu-raportti
        [raporttinakyma]
        [raporttivalinnat]))))
