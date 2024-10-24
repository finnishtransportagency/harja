(ns harja.views.kanavat.urakka.toimenpiteet.kokonaishintaiset
  (:require [tuck.core :refer [tuck]]
            [harja.tiedot.kanavat.urakka.toimenpiteet.kokonaishintaiset :as tiedot]
            [harja.tiedot.navigaatio :as navigaatio]
            [harja.tiedot.kanavat.urakka.kanavaurakka :as kanavaurakka]
            [harja.tiedot.urakka :as urakkatiedot]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.ui.valinnat :as valinnat]
            [harja.domain.kanavat.kanavan-toimenpide :as kanavan-toimenpide]
            [harja.views.kanavat.urakka.toimenpiteet :as toimenpiteet-view]
            [harja.views.kartta :as kartta]
            [harja.views.kartta.tasot :as tasot]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.domain.kanavat.kohde :as kohde]
            [harja.domain.kanavat.kanavan-huoltokohde :as huoltokohde]
            [reagent.core :as r]
            [harja.tiedot.raportit :as raportit]
            [harja.tiedot.kartta :as kartta-tiedot])
  (:require-macros
    [harja.makrot :refer [defc]]))

(defn hakuehdot [e! {:keys [huoltokohteet] :as app} kohteet]
  (let [urakka-map (get-in app [:valinnat :urakka])]
    [valinnat/urakkavalinnat {:urakka urakka-map}
     ^{:key "valinnat"}
     [:div.kanava-suodattimet
      [:div.ryhma
       [urakka-valinnat/urakan-sopimus-ja-hoitokausi-ja-aikavali-ja-toimenpide urakka-map]]

      [:div.ryhma
       [valinnat/kanava-kohde
        (r/wrap (first (filter #(= (::kohde/id %) (get-in app [:valinnat :kanava-kohde-id])) kohteet))
          (fn [uusi]
            (e! (tiedot/->PaivitaValinnat {:kanava-kohde-id (::kohde/id uusi)}))))
        (into [nil] kohteet)
        #(let [nimi (kohde/fmt-kohteen-nimi %)]
           (if (empty? nimi) "Kaikki" nimi))]

       [valinnat/kanava-huoltokohde
        (r/wrap (first (filter #(= (::huoltokohde/id %) (get-in app [:valinnat :huoltokohde-id])) huoltokohteet))
          #(e! (tiedot/->PaivitaValinnat {:huoltokohde-id (::huoltokohde/id %)})))
        (into [nil] huoltokohteet)
        #(or (::huoltokohde/nimi %) "Kaikki")]]]

     ^{:key "toiminnot"}
     [valinnat/urakkatoiminnot {:urakka urakka-map :sticky? true}

      [napit/uusi
       "Uusi toimenpide"
       (fn [_]
         (e! (tiedot/->UusiToimenpide)))]]]))

(defn kokonaishintaiset-toimenpiteet-taulukko [e! {:keys [toimenpiteet]}]
  [:div
   [toimenpiteet-view/ei-yksiloity-vihje]
   [grid/grid
    {:otsikko "Kokonaishintaiset toimenpiteet"
     :voi-lisata? false
     :voi-muokata? false
     :voi-poistaa? false
     :voi-kumota? false
     :piilota-toiminnot? true
     :rivi-klikattu (fn [rivi] (e! (tiedot/->AsetaLomakkeenToimenpiteenTiedot rivi)))
     :jarjesta ::kanavan-toimenpide/pvm
     :tunniste ::kanavan-toimenpide/id
     :raporttivienti #{:excel :pdf}
     :raporttiparametrit (raportit/urakkaraportin-parametrit
                           (:id @navigaatio/valittu-urakka)
                           :kanavien-kokonaishintaiset-toimenpiteet
                           {:urakka @navigaatio/valittu-urakka
                            :hallintayksikko @navigaatio/valittu-hallintayksikko
                            :aikavali @urakkatiedot/valittu-aikavali
                            :urakkatyyppi (:tyyppi @navigaatio/valittu-urakka)})}
    (toimenpiteet-view/toimenpidesarakkeet)
    (sort-by ::kanavan-toimenpide/pvm >
      (kanavan-toimenpide/korosta-ei-yksiloidyt toimenpiteet))]])

(defn kokonaishintainen-toimenpidelomake [e! app]
  [toimenpiteet-view/toimenpidelomake app {:tyhjenna-fn #(e! (tiedot/->TyhjennaAvattuToimenpide))
                                           :aseta-toimenpiteen-tiedot-fn #(e! (tiedot/->AsetaLomakkeenToimenpiteenTiedot %))
                                           :tallenna-lomake-fn #(e! (tiedot/->TallennaToimenpide % false))
                                           :poista-toimenpide-fn #(e! (tiedot/->PoistaToimenpide %))
                                           :paikannus-kaynnissa-fn #(e! (tiedot/->KytkePaikannusKaynnissa))
                                           :lisaa-materiaali-fn #(e! (tiedot/->LisaaMateriaali))
                                           :muokkaa-materiaaleja-fn #(e! (tiedot/->MuokkaaMateriaaleja %))
                                           :lisaa-virhe-fn #(e! (tiedot/->LisaaVirhe %))}])

(defn kokonaishintaiset-nakyma [e! {:keys [avattu-toimenpide] :as app} kohteet]
  (let [nakyma-voidaan-nayttaa? (some? kohteet)]
    (if nakyma-voidaan-nayttaa?
      [:span
       [kartta/kartan-paikka]
       [:div
        (if avattu-toimenpide
          [kokonaishintainen-toimenpidelomake e! app]
          [:div
           [hakuehdot e! app kohteet]
           [kokonaishintaiset-toimenpiteet-taulukko e! app]])]]
      [ajax-loader "Ladataan..."])))

(defn kokonaishintaiset* [e! _]
  (komp/luo
    (komp/watcher tiedot/valinnat (fn [_ _ uusi] (e! (tiedot/->PaivitaValinnat uusi))))
    (komp/sisaan-ulos #(do
                         (tasot/taso-paalle! :kan-kohteet)
                         (tasot/taso-paalle! :kan-toimenpiteet)
                         (tasot/taso-pois! :organisaatio)
                         (kartta-tiedot/kasittele-infopaneelin-linkit!
                           {:kan-toimenpide {:toiminto (fn [t]
                                                         (e! (tiedot/->AsetaLomakkeenToimenpiteenTiedot t))
                                                         (kartta-tiedot/piilota-infopaneeli!))
                                             :teksti "Avaa toimenpide"}})
                         (e! (tiedot/->NakymaAvattu)))
      #(do
         (tasot/taso-pois! :kan-kohteet)
         (tasot/taso-pois! :kan-toimenpiteet)
         (tasot/taso-paalle! :organisaatio)
         (kartta-tiedot/kasittele-infopaneelin-linkit! nil)
         (e! (tiedot/->NakymaSuljettu))))
    (fn [e! {:keys [haku-kaynnissa?] :as app}]
      ;; Reaktio on pakko lukea komponentissa, muuten se ei päivity!
      @tiedot/valinnat
      [:span
       (if haku-kaynnissa?
         [ajax-loader "Haetaan toimenpiteitä"]
         [kokonaishintaiset-nakyma e! app @kanavaurakka/kanavakohteet])])))

(defc kokonaishintaiset []
      [tuck tiedot/tila kokonaishintaiset*])
