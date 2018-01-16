(ns harja.views.kanavat.hallinta.kohteiden-luonti
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :refer [tuck]]

            [harja.tiedot.kanavat.hallinta.kohteiden-luonti :as tiedot]
            [harja.loki :refer [tarkkaile! log]]
            [harja.id :refer [id-olemassa?]]

            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.ui.lomake :as lomake]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni tietoja]]

            [harja.domain.urakka :as urakka]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.kanavat.kohde :as kohde]
            [harja.domain.kanavat.kohdekokonaisuus :as kok]
            [harja.ui.napit :as napit]
            [harja.ui.debug :as debug]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.kentat :as kentat]

            [harja.domain.urakka :as ur]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.valinnat :as valinnat])
  (:require-macros
    [cljs.core.async.macros :refer [go]]
    [harja.makrot :refer [defc fnc]]
    [harja.tyokalut.ui :refer [for*]]))

(defn kohdelomake [e! {:keys [valittu-kohde
                              kohdekokonaisuudet] tallennus-kaynnissa? :kohteiden-tallennus-kaynnissa? :as app}]
  [:div
   [debug/debug app]
   [napit/takaisin #(e! (tiedot/->ValitseKohde nil))]
   [lomake/lomake
    {:otsikko (if (id-olemassa? (::kohde/id valittu-kohde))
                "Muokkaa kohdetta"
                "Lisää kohde")
     :muokkaa! #(e! (tiedot/->KohdettaMuokattu (lomake/ilman-lomaketietoja %)))
     :footer-fn (fn [app]
                  [napit/tallenna
                   "Tallenna"
                   #(e! (tiedot/->TallennaKohdekokonaisuudet (:kohdekokonaisuudet app)))
                   {:tallennus-kaynnissa? tallennus-kaynnissa?
                    :disabled (or tallennus-kaynnissa?
                                  (not (lomake/voi-tallentaa? (:kohdekokonaisuudet app)))
                                  (not (tiedot/kohdekokonaisuudet-voi-tallentaa? (:kohdekokonaisuudet app)))
                                  (not (oikeudet/voi-kirjoittaa? oikeudet/hallinta-vesivaylat)))}])}
    [{:otsikko "Kohdekokonaisuus"
      :tyyppi :valinta
      :valinnat kohdekokonaisuudet
      :valinta-nayta ::kok/nimi
      :nimi ::kohde/kohdekokonaisuus}
     {:otsikko "Nimi"
      :tyyppi :string
      :nimi ::kohde/nimi}]
    valittu-kohde]])

(defn kohdekokonaisuudet-grid [e! {:keys [kohdekokonaisuudet] :as app}]
  [grid/muokkaus-grid
   {:tyhja "Lisää kokonaisuuksia oikeasta yläkulmasta"
    :tunniste ::kok/id
    :voi-poistaa? (fn [kokonaisuus]
                    (tiedot/kokonaisuuden-voi-poistaa? app kokonaisuus))}
   [{:otsikko "Nimi"
     :tyyppi :string
     :nimi ::kok/nimi
     :leveys 4}
    {:otsikko "Kohteiden lukumäärä"
     :tyyppi :numero
     :nimi :kohteiden-lkm
     :leveys 1
     :muokattava? (constantly false)
     :hae (partial tiedot/kohteiden-lkm-kokonaisuudessa app)}]
   (r/wrap
     (zipmap (range) kohdekokonaisuudet)
     #(e! (tiedot/->LisaaKohdekokonaisuuksia (sort-by :id (vals %)))))])

(defn kohdekokonaisuuslomake [e! {tallennus-kaynnissa? :kohdekokonaisuuksien-tallennus-kaynnissa?
                                  :as app}]
  [:div
   [debug/debug app]
   [napit/takaisin #(e! (tiedot/->SuljeKohdekokonaisuusLomake))]
   [lomake/lomake
    {:otsikko "Lisää tai muokkaa kohdekokonaisuuksia"
     ;; muokkaa! on nykyään pakollinen lomakkeelle, mutta tässä lomakeessa
     ;; tietojen päivittämienn tehdään suoraan riville asetetuilla funktioilla
     :muokkaa! (constantly true)
     :footer-fn (fn [app]
                  [napit/tallenna
                   "Tallenna"
                   #(e! (tiedot/->TallennaKohdekokonaisuudet (:kohdekokonaisuudet app)))
                   {:tallennus-kaynnissa? tallennus-kaynnissa?
                    :disabled (or tallennus-kaynnissa?
                                  (not (lomake/voi-tallentaa? (:kohdekokonaisuudet app)))
                                  (not (tiedot/kohdekokonaisuudet-voi-tallentaa? (:kohdekokonaisuudet app)))
                                  (not (oikeudet/voi-kirjoittaa? oikeudet/hallinta-vesivaylat)))}])}
    [{:otsikko "Kohdekokonaisuus"
      :tyyppi :komponentti
      :nimi :kohdekokonaisuudet
      :komponentti (fn [] [kohdekokonaisuudet-grid e! app])}]
    app]])

(defn kohteiden-luonti* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(do (e! (tiedot/->Nakymassa? true))
                           (e! (tiedot/->HaeKohteet))
                           (e! (tiedot/->AloitaUrakoidenHaku)))
                      #(do (e! (tiedot/->Nakymassa? false))
                           (e! (tiedot/->ValitseUrakka nil))
                           (e! (tiedot/->SuljeKohdekokonaisuusLomake))))

    (fn [e! {:keys [kohderivit kohteiden-haku-kaynnissa? kohdekokonaisuuslomake-auki? liittaminen-kaynnissa?
                    uudet-urakkaliitokset valittu-urakka urakat valittu-kohde] :as app}]
      (cond kohdekokonaisuuslomake-auki?
            [kohdekokonaisuuslomake e! app]

            valittu-kohde
            [kohdelomake e! app]

            :else
            [:div
             [debug/debug app]
             [valinnat/urakkatoiminnot {}
              [napit/uusi "Muokkaa kohdekokonaisuuksia"
               #(e! (tiedot/->AvaaKohdekokonaisuusLomake))]
              [napit/uusi "Uusi kohde"
               #(e! (tiedot/->ValitseKohde tiedot/uusi-kohde))]
              [napit/tallenna "Tallenna urakkaliitokset"
               #(e! (tiedot/->PaivitaKohteidenUrakkaliitokset))
               {:disabled (empty? uudet-urakkaliitokset)
                :tallennus-kaynnissa? liittaminen-kaynnissa?}]]
             [:div.otsikko-ja-valinta-rivi
              [:div.otsikko "Kaikki kohteet:"]
              [:div.valinta.label-ja-alasveto
               [:span.alasvedon-otsikko "Kuuluu urakkaan:"]
               [yleiset/livi-pudotusvalikko
                {:valitse-fn #(e! (tiedot/->ValitseUrakka %))
                 :valinta valittu-urakka
                 :format-fn #(or (::ur/nimi %) "Kaikki urakat")}
                (into [nil] urakat)]]]
             [grid/grid
              {:tunniste ::kohde/id
               :tyhja (if kohteiden-haku-kaynnissa?
                        [ajax-loader "Haetaan kohteita"]
                        "Ei perustettuja kohteita")
               :rivi-klikattu #(e! (tiedot/->ValitseKohde %))}
              [{:otsikko "Kohde"
                :nimi ::kohde/nimi
                :leveys 5}
               (if-not valittu-urakka
                 {:otsikko "Urakat"
                  :tyyppi :string
                  :nimi :kohteen-urakat
                  :leveys 6
                  :hae tiedot/kohteen-urakat}
                 {:otsikko (str "Kuuluu urakkaan " (:harja.domain.urakka/nimi valittu-urakka) "?")
                  :leveys 6
                  :tyyppi :komponentti
                  :tasaa :keskita
                  :nimi :valinta
                  :solu-klikattu (fn [rivi]
                                   (let [kuuluu-urakkaan? (tiedot/kohde-kuuluu-urakkaan? app rivi valittu-urakka)]
                                     (e! (tiedot/->AsetaKohteenUrakkaliitos (::kohde/id rivi)
                                                                            (::urakka/id valittu-urakka)
                                                                            (not kuuluu-urakkaan?)))))
                  :komponentti (fn [rivi]
                                 [kentat/tee-kentta
                                  {:tyyppi :checkbox}
                                  (r/wrap
                                    (tiedot/kohde-kuuluu-urakkaan? app rivi valittu-urakka)
                                    (fn [uusi]
                                      (e! (tiedot/->AsetaKohteenUrakkaliitos (::kohde/id rivi)
                                                                             (::urakka/id valittu-urakka)
                                                                             uusi))))])})]
              (tiedot/ryhmittele-kohderivit-kanavalla kohderivit)]]))))

(defc kohteiden-luonti []
  [tuck tiedot/tila kohteiden-luonti*])
