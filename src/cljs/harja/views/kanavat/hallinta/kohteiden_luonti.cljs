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
            [harja.ui.debug :refer [debug]]

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

(defn kohteet-grid [e! kanavan-kohteet]
  [grid/muokkaus-grid
   {:tyhja "Lisää kohteita oikeasta yläkulmasta"
    :tunniste ::kohde/id
    :voi-poistaa? (constantly false)
    :jarjesta-avaimen-mukaan identity
    :piilota-toiminnot? true
    :uusi-id (count kanavan-kohteet)}
   [{:otsikko "Kohteen nimi"
     :tyyppi :string
     :nimi ::kohde/nimi
     :leveys 1}
    {:otsikko "Kohteen tyyppi"
     :tyyppi :valinta
     :leveys 1
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
     #(e! (tiedot/->LisaaKohteita (sort-by :id (vals %)))))])

(defn luontilomake [e! {tallennus-kaynnissa? :kohteiden-tallennus-kaynnissa?
                        kanavat :kanavat
                        {muokattava-kanava :kanava
                         :as uudet-tiedot} :lomakkeen-tiedot
                        :as app}]
  [:div
   [debug/debug app]
   [napit/takaisin #(e! (tiedot/->SuljeKohdeLomake))]
   [lomake/lomake
    {:otsikko "Lisää tai muokkaa kanavan kohteita"
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
      :valinnat (sort-by ::kok/nimi kanavat)
      :valinta-nayta #(or (::kok/nimi %) "Valitse kanava")
      :aseta (fn [_ arvo]
               ;; kentat/atomina funktiossa katsotaan, onko :aseta ehto annettu
               ;; jos on, oletetaan, että funktio palauttaa lomakkeen käyttämän datan
               (:lomakkeen-tiedot (e! (tiedot/->ValitseKanava arvo))))}
     (when muokattava-kanava
       {:otsikko "Kohteet"
        :nimi :kohteet
        :tyyppi :komponentti
        :palstoja 3
        :komponentti (fn [_]
                       [kohteet-grid
                        e!
                        (tiedot/muokattavat-kohteet app)])})]
    uudet-tiedot]])

(defn- ryhmittele-kohderivit-kanavalla [kohderivit]
  (let [ryhmat (group-by ::kok/id (sort-by ::kok/nimi kohderivit))]
    (mapcat
      (fn [kohdekokonaisuus-id]
        (let [ryhman-sisalto (get ryhmat kohdekokonaisuus-id)]
          (concat
            [(grid/otsikko (::kok/nimi (first ryhman-sisalto)))]
            (sort-by ::kohde/nimi ryhman-sisalto))))
      (keys ryhmat))))

(defn kohteiden-luonti* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(do (e! (tiedot/->Nakymassa? true))
                           (e! (tiedot/->HaeKohteet))
                           (e! (tiedot/->AloitaUrakoidenHaku)))
                      #(do (e! (tiedot/->Nakymassa? false))
                           (e! (tiedot/->ValitseUrakka nil))
                           (e! (tiedot/->SuljeKohdeLomake))))

    (fn [e! {:keys [kohderivit kohteiden-haku-kaynnissa? kohdelomake-auki?
                    valittu-urakka urakat poistaminen-kaynnissa? poistettava-kohde] :as app}]
      (if-not kohdelomake-auki?
        [:div
         [debug/debug app]
         [valinnat/urakkatoiminnot {}
          #_[napit/uusi "Lisää kohteen osia"
             (fn [] (log "TODO"))]
          [napit/tallenna "Tallenna urakkaliitokset"
           (fn [] (log "TODO"))]]
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
                    "Ei perustettuja kohteita")}
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
          (ryhmittele-kohderivit-kanavalla kohderivit)]]

        [luontilomake e! app]))))

(defc kohteiden-luonti []
  [tuck tiedot/tila kohteiden-luonti*])
