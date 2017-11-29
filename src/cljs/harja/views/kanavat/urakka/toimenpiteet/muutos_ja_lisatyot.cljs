(ns harja.views.kanavat.urakka.toimenpiteet.muutos-ja-lisatyot
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :refer [tuck]]

            [harja.tiedot.kanavat.urakka.toimenpiteet.muutos-ja-lisatyot :as tiedot]
            [harja.views.kanavat.urakka.toimenpiteet :as toimenpide-view]
            [harja.views.kanavat.urakka.toimenpiteet.hinnoittelu :as hinnoittelu-ui]
            [harja.loki :refer [tarkkaile! log]]
            [harja.pvm :as pvm]

            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.ui.lomake :as lomake]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni tietoja]]
            [harja.ui.debug :refer [debug]]
            [harja.ui.modal :as modal]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.kanavat.kanavan-toimenpide :as kanavan-toimenpide]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.ui.valinnat :as valinnat]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.ui.napit :as napit]
            [harja.ui.debug :as debug]
            [harja.views.kanavat.urakka.toimenpiteet :as toimenpiteet-view])
  (:require-macros
    [cljs.core.async.macros :refer [go]]
    [harja.makrot :refer [defc fnc]]
    [harja.tyokalut.ui :refer [for*]]))

(defn suodattimet [e! app]
  (let [urakka (get-in app [:valinnat :urakka :id])]
    [:div
     [valinnat/urakkavalinnat {:urakka urakka}
      ^{:key "valinnat"}
      [urakka-valinnat/urakan-sopimus-ja-hoitokausi-ja-aikavali-ja-toimenpide urakka]
      [valinnat/urakkatoiminnot {:urakka urakka}
       [napit/yleinen-ensisijainen
        "Siirrä valitut kokonaishintaisiin"
        (fn [_]
          (e! (tiedot/->SiirraValitut)))
        {:disabled (zero? (count (:valitut-toimenpide-idt app)))}]
       [napit/uusi
        "Uusi toimenpide"
        (fn [_]
          ;;todo
          )]]]]))

(defn taulukko [e! {:keys [toimenpiteiden-haku-kaynnissa? toimenpiteet] :as app}]
  (let [hinta-sarake {:otsikko "Hinta"
                      :nimi :hinta
                      :tyyppi :komponentti
                      :komponentti (fn [rivi] [hinnoittelu-ui/hinnoittele-toimenpide e! app rivi])}
        toimenpidesarakkeet (toimenpide-view/toimenpidesarakkeet
                              e! app
                              {:kaikki-valittu?-fn #(= (count (:toimenpiteet app))
                                                       (count (:valitut-toimenpide-idt app)))
                               :otsikko-valittu-fn (fn [uusi-arvo]
                                                     (e! (tiedot/->ValitseToimenpiteet
                                                           {:kaikki-valittu? uusi-arvo})))
                               :rivi-valittu?-fn (fn [rivi]
                                                   (boolean ((:valitut-toimenpide-idt app)
                                                              (::kanavan-toimenpide/id rivi))))
                               :rivi-valittu-fn (fn [rivi uusi-arvo]
                                                  (e! (tiedot/->ValitseToimenpide
                                                        {:id (::kanavan-toimenpide/id rivi)
                                                         :valittu? uusi-arvo})))})
        toimenpidesarakkeet-ilman-valinta-saraketta (subvec toimenpidesarakkeet 0 (dec (count toimenpidesarakkeet)))
        valinta-sarake (last toimenpidesarakkeet)
        sarakkeet (concat toimenpidesarakkeet-ilman-valinta-saraketta [hinta-sarake] [valinta-sarake])]
    [:div
     [toimenpiteet-view/ei-yksiloity-vihje]
     [grid/grid
     {:otsikko "Muutos- ja lisätyöt"
      :tyhja (if (:toiden-haku-kaynnissa? app)
               [ajax-loader "Haetaan toimenpiteitä"]
               "Ei toimenpiteitä")
      :tunniste ::kanavan-toimenpide/id}
     sarakkeet
     (kanavan-toimenpide/korosta-ei-yksiloidyt toimenpiteet)]]))

(defn lisatyot* [e! app]
  (komp/luo
   (komp/watcher tiedot/valinnat (fn [_ _ uusi]
                                   (e! (tiedot/->PaivitaValinnat uusi))))
   (komp/sisaan-ulos #(do
                        @tiedot/valinnat ;; luetaan sivuvaikutusten vuoksi
                        (e! (tiedot/->Nakymassa? true))
                        (e! (tiedot/->PaivitaValinnat
                             {:urakka @nav/valittu-urakka
                              :sopimus-id (first @u/valittu-sopimusnumero)
                              :aikavali @u/valittu-aikavali
                              :toimenpide @u/valittu-toimenpideinstanssi}))
                        (e! (tiedot/->HaeSuunnitellutTyot)))
                     #(do
                        (e! (tiedot/->Nakymassa? false))))

   (fn [e! {:keys [toimenpiteet haku-kaynnissa?] :as app}]
     @tiedot/valinnat ;; Reaktio on pakko lukea komponentissa, muuten se ei päivity!
     [:div
      [debug app]
      [suodattimet e! app]
      [taulukko e! app]])))

(defc lisatyot []
  [tuck tiedot/tila lisatyot*])
