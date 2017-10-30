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

            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.kanavat.kanavan-kohde :as kohde]
            [harja.domain.kanavat.kanava :as kanava]
            [harja.ui.napit :as napit]
            [harja.ui.debug :as debug]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.kentat :as kentat]

            [harja.domain.urakka :as ur]
            [harja.ui.ikonit :as ikonit])
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

(defn varmistusnappi
  ([e! kohde] [varmistusnappi e! kohde {}])
  ([e! kohde opts]
   [napit/nappi
    ""
    #(e! (tiedot/->AsetaPoistettavaKohde kohde))
    (merge
      {:ikoninappi? true
       :luokka "nappi-toissijainen"
       :ikoni (ikonit/livicon-trash)}
      opts)]))

(defn poista-tai-takaisin
  ([e! kohde] [poista-tai-takaisin e! kohde {}])
  ([e! kohde opts]
   [:div
    [napit/takaisin
     ""
     #(e! (tiedot/->AsetaPoistettavaKohde nil))
     (merge
       {:ikoninappi? true}
       opts)]
    [napit/poista
     ""
     #(e! (tiedot/->PoistaKohde kohde))
     (merge
       {:ikoninappi? true}
       opts)]]))

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
         [:div.label-ja-alasveto
          [:span.alasvedon-otsikko "Urakka"]
          [yleiset/livi-pudotusvalikko
           {:valitse-fn #(e! (tiedot/->ValitseUrakka %))
            :valinta valittu-urakka
            :format-fn #(or (::ur/nimi %) "Kaikki urakat")}
           (into [nil] urakat)]]
         [grid/grid
          {:tunniste ::kohde/id
           :tyhja (if kohteiden-haku-kaynnissa?
                    [ajax-loader "Haetaan kohteita"]
                    "Ei perustettuja kohteita")}
          [{:otsikko "Kanava, kohde, ja kohteen tyyppi" :nimi :rivin-teksti
            :leveys 4}
           {:otsikko "Poista"
            :leveys 1
            :tyyppi :komponentti
            :tasaa :keskita
            :komponentti (fn [kohde]
                           (cond
                             (nil? poistettava-kohde)
                             [varmistusnappi e! kohde]

                             (= poistettava-kohde kohde)
                             [poista-tai-takaisin e! kohde {:disabled poistaminen-kaynnissa?}]

                             (some? poistettava-kohde)
                             [varmistusnappi e! kohde {:disabled true}]))}
           (if-not valittu-urakka
             {:otsikko "Urakat"
              :tyyppi :string
              :nimi :kohteen-urakat
              :leveys 6
              :hae tiedot/kohteen-urakat}

             {:otsikko "Kuuluu urakkaan?"
              :leveys 6
              :tyyppi :komponentti
              :tasaa :keskita
              :nimi :valinta
              :solu-klikattu (fn [rivi] (e! (tiedot/->LiitaKohdeUrakkaan rivi
                                                                         (not (:valittu? rivi))
                                                                         valittu-urakka)))
              :komponentti (fn [rivi]
                             (if (tiedot/liittaminen-kaynnissa? app rivi)
                               [ajax-loader-pieni]

                               [kentat/tee-kentta
                                {:tyyppi :checkbox}
                                (r/wrap
                                  (tiedot/kohde-kuuluu-urakkaan? rivi valittu-urakka)
                                  (fn [uusi]
                                    (e! (tiedot/->LiitaKohdeUrakkaan rivi
                                                                     uusi
                                                                     valittu-urakka))))]))})]
          (sort-by :rivin-teksti kohderivit)]
         [napit/uusi
          "Lisää uusi kohde"
          #(e! (tiedot/->AvaaKohdeLomake))
          {:disabled (not (oikeudet/voi-kirjoittaa? oikeudet/hallinta-vesivaylat))}]]

        [luontilomake e! app]))))

(defc kohteiden-luonti []
  [tuck tiedot/tila kohteiden-luonti*])
