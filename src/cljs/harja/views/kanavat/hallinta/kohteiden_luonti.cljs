(ns harja.views.kanavat.hallinta.kohteiden-luonti
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :refer [tuck]]

            [harja.tiedot.kanavat.hallinta.kohteiden-luonti :as tiedot]
            [harja.loki :refer [tarkkaile! log]]
            [harja.pvm :as pvm]
            [harja.id :refer [id-olemassa?]]

            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.ui.lomake :as lomake]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni tietoja]]
            [harja.ui.debug :refer [debug]]
            [harja.ui.modal :as modal]

            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.kanavat.kanavan-kohde :as kohde]
            [harja.domain.kanavat.kanava :as kanava]
            [harja.ui.napit :as napit]
            [harja.ui.debug :as debug])
  (:require-macros
    [cljs.core.async.macros :refer [go]]
    [harja.makrot :refer [defc fnc]]
    [harja.tyokalut.ui :refer [for*]]))

(defn kohteet-grid [e! kanavan-kohteet]
  [grid/muokkaus-grid
   {:tyhja "Lisää kohteita oikeasta yläkulmasta"
    :tunniste ::kohde/id}
   [{:otsikko "Kohteen nimi"
     :tyyppi :string
     :nimi ::kohde/nimi}
    {:otsikko "Kohteen tyyppi"
     :tyyppi :valinta
     :nimi ::kohde/tyyppi
     :valinnat #{:silta :sulku :sulku-ja-silta}
     :valinta-nayta kohde/tyyppi->str
     :pakollinen? true
     :validoi [[:ei-tyhja "Anna kohteelle tyyppi"]]}]
   (r/wrap
     (into {}
           (map-indexed
             (fn [i k] [i k])
             kanavan-kohteet))
     #(e! (tiedot/->LisaaKohteita (vals %))))])

(defn luontilomake [e! {tallennus-kaynnissa? :kohteiden-tallennus-kaynnissa?
                        kanavat :kanavat
                        {muokattava-kanava :kanava
                         :as uudet-tiedot} :lomakkeen-tiedot
                        :as app}]
  [:div
   [debug/debug app]
   [napit/takaisin #(e! (tiedot/->SuljeKohdeLomake))]
   [lomake/lomake
    {:otsikko "Lisää kanavalle kohteita"
     ;; muokkaa! on nykyään pakollinen lomakkeelle, mutta tässä lomakeessa
     ;; tietojen päivittämienn tehdään suoraan riville asetetuilla funktioilla
     :muokkaa! (constantly true)
     :footer-fn (fn [kohteet]
                  [napit/tallenna
                   "Tallenna kohteet"
                   #(e! (tiedot/->TallennaKohteet))
                   {:tallennus-kaynnissa? tallennus-kaynnissa?
                    :disabled (or (not (lomake/voi-tallentaa? kohteet))
                                  (not (tiedot/kohteet-voi-tallentaa? kohteet))
                                  (not (oikeudet/voi-kirjoittaa? oikeudet/hallinta-vesivaylat)))}])}
    [{:otsikko "Kanava"
      :tyyppi :valinta
      :nimi :kanava
      :valinnat (sort-by ::kanava/nimi kanavat)
      :valinta-nayta #(or (::kanava/nimi %) "Valitse kanava")
      :aseta (fn [_ arvo]
               ;; kentat/atomina funktiossa katsotaan, onko :aseta ehto annettu
               ;; jos on, oletetaan, että funktio palauttaa lomakkeen käyttämän datan
               (:lomakkeen-tiedot (e! (tiedot/->ValitseKanava arvo))))}
     (when muokattava-kanava
       {:otsikko "Kohteet"
        :nimi :kohteet
        :tyyppi :komponentti
        :palstoja 2
        :komponentti (fn [_]
                       [kohteet-grid
                        e!
                        (tiedot/muokattavat-kohteet app)])})]
    uudet-tiedot]])

(defn kohteiden-luonti* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(do (e! (tiedot/->Nakymassa? true))
                           (e! (tiedot/->HaeKohteet)))
                      #(e! (tiedot/->Nakymassa? false)))

    (fn [e! {:keys [kohderivit kohteiden-haku-kaynnissa? kohdelomake-auki?] :as app}]
      (if-not kohdelomake-auki?
        [:div
         [debug/debug app]
         [:div
          {:style {:width "50%"
                   :display "inline-block"
                   :padding-right "30px"}}
          [grid/grid
           {:tunniste ::kohde/id
            :tyhja (if kohteiden-haku-kaynnissa?
                     [ajax-loader "Haetaan kohteita"]
                     "Ei perustettuja kohteita")}
           [{:otsikko "Kanava, kohde, ja kohteen tyyppi" :nimi :rivin-teksti}]
           (sort-by :rivin-teksti kohderivit)]]
         [:div
          {:style {:width "50%"
                   :display "inline-block"
                   :vertical-align "top"}}
          [grid/grid
           {:tyhja "Placeholder"}
           [{:otsikko "Tämä grid on vain placeholder" :nimi :rivin-teksti}]
           []]]
         [napit/uusi
          "Lisää uusi kohde"
          #(e! (tiedot/->AvaaKohdeLomake))
          {:disabled (not (oikeudet/voi-kirjoittaa? oikeudet/hallinta-vesivaylat))}]]

        [luontilomake e! app]))))

(defc kohteiden-luonti []
  [tuck tiedot/tila kohteiden-luonti*])
