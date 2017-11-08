(ns harja.views.kanavat.urakka.toimenpiteet.muutos-ja-lisatyot
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :refer [tuck]]

            [harja.tiedot.kanavat.urakka.toimenpiteet.muutos-ja-lisatyot :as tiedot]
            [harja.views.kanavat.urakka.toimenpiteet :as toimenpide-view]
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
            [harja.domain.kanavat.kanavan-toimenpide :as kanavan-toimenpide]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.ui.valinnat :as valinnat]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.ui.napit :as napit]
            [harja.ui.debug :as debug])
  (:require-macros
    [cljs.core.async.macros :refer [go]]
    [harja.makrot :refer [defc fnc]]
    [harja.tyokalut.ui :refer [for*]]))

(defn suodattimet [e! app]
  (let [urakka (get-in app [:valinnat :urakka])]
    [:div
     [valinnat/urakkavalinnat {:urakka urakka}
      ^{:key "valinnat"}
      [urakka-valinnat/urakan-sopimus-ja-hoitokausi-ja-aikavali-ja-toimenpide urakka]
      [valinnat/urakkatoiminnot {:urakka urakka}
       [napit/uusi
        "Uusi toimenpide"
        (fn [_]
          ;;todo
          )]]]]))

(defn taulukko [e! {:keys [toimenpiteiden-haku-kaynnissa? toimenpiteet] :as app}]
  [grid/grid
   {:otsikko "Muutos- ja lisätyöt"
    :tyhja (if (:toiden-haku-kaynnissa? app)
             [ajax-loader "Haetaan toimenpiteitä"]
             "Ei toimenpiteitä")
    :tunniste ::kanavan-toimenpide/id}
   toimenpide-view/toimenpidesarakkeet
   toimenpiteet])

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
                                  :toimenpide @u/valittu-toimenpideinstanssi})))
                        #(do
                           (e! (tiedot/->Nakymassa? false))))
      (fn [e! {:keys [toimenpiteet haku-kaynnissa?] :as app}]
        @tiedot/valinnat ;; Reaktio on pakko lukea komponentissa, muuten se ei päivity!
        [:div
         [suodattimet e! app]
         [taulukko e! app]]))))

(defc lisatyot []
      [tuck tiedot/tila lisatyot*])



