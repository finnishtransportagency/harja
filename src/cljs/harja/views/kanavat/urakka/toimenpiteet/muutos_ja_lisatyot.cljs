(ns harja.views.kanavat.urakka.toimenpiteet.muutos-ja-lisatyot
  (:require [tuck.core :refer [tuck]]
            [harja.tiedot.kanavat.urakka.toimenpiteet.muutos-ja-lisatyot :as tiedot]
            [harja.views.kanavat.urakka.toimenpiteet :as toimenpide-view]
            [harja.views.kanavat.urakka.toimenpiteet.hinnoittelu :as hinnoittelu-ui]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.ui.debug :refer [debug]]
            [harja.domain.kanavat.kanavan-toimenpide :as kanavan-toimenpide]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.ui.valinnat :as valinnat]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.ui.napit :as napit]
            [harja.tiedot.kanavat.urakka.kanavaurakka :as kanavaurakka]
            [harja.views.kartta :as kartta]
            [harja.views.kartta.tasot :as tasot]
            [harja.domain.kanavat.kommentti :as kommentti]
            [harja.tiedot.kartta :as kartta-tiedot])
  (:require-macros
    [harja.makrot :refer [defc]]))

(defn suodattimet [e! app]
  (let [urakka-map (get-in app [:valinnat :urakka])]
    [:div
     [valinnat/urakkavalinnat {:urakka urakka-map}
      ^{:key "valinnat"}
      [urakka-valinnat/urakan-sopimus-ja-hoitokausi-ja-aikavali-ja-toimenpide urakka-map]
      [valinnat/urakkatoiminnot {:urakka urakka-map}
       [napit/yleinen-ensisijainen
        "Siirrä valitut kokonaishintaisiin"
        (fn [_]
          (e! (tiedot/->SiirraValitut)))
        {:disabled (zero? (count (:valitut-toimenpide-idt app)))}]
       [napit/uusi
        "Uusi toimenpide"
        (fn [_]
          (e! (tiedot/->UusiToimenpide)))]]]]))

(defn lisatyot-lomake [e! app]
  [toimenpide-view/toimenpidelomake app {:tyhjenna-fn #(e! (tiedot/->TyhjennaAvattuToimenpide))
                                           :aseta-toimenpiteen-tiedot-fn #(e! (tiedot/->AsetaLomakkeenToimenpiteenTiedot %))
                                           :tallenna-lomake-fn #(e! (tiedot/->TallennaToimenpide % false))
                                           :poista-toimenpide-fn #(e! (tiedot/->PoistaToimenpide %))
                                           :paikannus-kaynnissa-fn #(e! (tiedot/->KytkePaikannusKaynnissa))
                                           :lisaa-materiaali-fn #(e! (tiedot/->LisaaMateriaali))
                                           :muokkaa-materiaaleja-fn #(e! (tiedot/->MuokkaaMateriaaleja %))
                                           :lisaa-virhe-fn #(e! (tiedot/->LisaaVirhe %))}])

(defn taulukko [e! {:keys [toimenpiteiden-haku-kaynnissa? toimenpiteet] :as app}]
  (let [hinta-sarake {:otsikko "Hinta"
                      :nimi :hinta
                      :tyyppi :komponentti
                      :leveys 30
                      :komponentti (fn [rivi] [hinnoittelu-ui/hinnoittele-toimenpide e! app rivi])}
        tila-sarake {:otsikko "Tila"
                     :nimi ::kanavan-toimenpide/kommentit
                     :tyyppi :string
                     :fmt kommentti/hinnoittelun-tila->str
                     :leveys 7}
        toimenpidesarakkeet (toimenpide-view/toimenpidesarakkeet)
        toimenpidesarakkeet-ilman-valinta-saraketta (subvec toimenpidesarakkeet 0 (dec (count toimenpidesarakkeet)))
        valinta-sarake (last toimenpidesarakkeet)
        sarakkeet (concat toimenpidesarakkeet-ilman-valinta-saraketta [hinta-sarake] [tila-sarake] [valinta-sarake])]
    [:div
     [toimenpide-view/ei-yksiloity-vihje]
     [grid/grid
      {:otsikko "Muutos- ja lisätyöt"
       :tyhja (if (:toimenpiteiden-haku-kaynnissa? app)
                [ajax-loader "Haetaan toimenpiteitä"]
                "Ei toimenpiteitä")
       :rivi-klikattu (fn [rivi] (e! (tiedot/->AsetaLomakkeenToimenpiteenTiedot rivi)))
       :tunniste ::kanavan-toimenpide/id}
      sarakkeet
      (sort-by ::kanavan-toimenpide/pvm >
               (kanavan-toimenpide/korosta-ei-yksiloidyt toimenpiteet))]]))

(defn lisatyot* [e! app]
  (let [urakka (get-in app [:valinnat :urakka-id])]
    (komp/luo
      (komp/watcher tiedot/valinnat (fn [_ _ uusi]
                                      (e! (tiedot/->PaivitaValinnat uusi))))
      (komp/sisaan-ulos #(do
                           (e! (tiedot/->Nakymassa? true))
                           (e! (tiedot/->PaivitaValinnat
                                 {:urakka @nav/valittu-urakka
                                  :sopimus-id (first @u/valittu-sopimusnumero)
                                  :aikavali @u/valittu-aikavali
                                  :toimenpide @u/valittu-toimenpideinstanssi}))
                           (e! (tiedot/->HaeSuunnitellutTyot))
                           (e! (tiedot/->HaeHuoltokohteet))
                           (kartta-tiedot/kasittele-infopaneelin-linkit!
                             {:kan-toimenpide {:toiminto (fn [t]
                                                           (e! (tiedot/->AsetaLomakkeenToimenpiteenTiedot t))
                                                           (kartta-tiedot/piilota-infopaneeli!))
                                               :teksti "Avaa toimenpide"}})
                           (tasot/taso-paalle! :kan-kohteet)
                           (tasot/taso-paalle! :kan-toimenpiteet)
                           (tasot/taso-pois! :organisaatio))
                        #(do
                           (e! (tiedot/->Nakymassa? false))
                           (kartta-tiedot/kasittele-infopaneelin-linkit! nil)
                           (tasot/taso-pois! :kan-kohteet)
                           (tasot/taso-pois! :kan-toimenpiteet)
                           (tasot/taso-paalle! :organisaatio)))

      (fn [e! {:keys [toimenpiteet haku-kaynnissa? avattu-toimenpide] :as app}]
        @tiedot/valinnat ;; Reaktio on pakko lukea komponentissa, muuten se ei päivity!
        (let [kohteet @kanavaurakka/kanavakohteet
              nakyma-voidaan-nayttaa? (some? kohteet)]
          (if nakyma-voidaan-nayttaa?
            [:span
             [kartta/kartan-paikka]
             [:div
              [debug app]
              (if avattu-toimenpide
                [lisatyot-lomake e! app]
                [:div
                 [suodattimet e! app]
                 [taulukko e! app]])
              [debug app]]]
            [ajax-loader "Ladataan..."]))))))

(defc lisatyot []
  [tuck tiedot/tila lisatyot*])
