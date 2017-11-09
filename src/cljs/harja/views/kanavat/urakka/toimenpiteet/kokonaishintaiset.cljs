(ns harja.views.kanavat.urakka.toimenpiteet.kokonaishintaiset
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :refer [tuck]]

            [harja.tiedot.kanavat.urakka.toimenpiteet.kokonaishintaiset :as tiedot]
            [harja.loki :refer [tarkkaile! log]]
            [harja.id :refer [id-olemassa?]]

            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.ui.napit :as napit]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni tietoja]]
            [harja.ui.debug :refer [debug]]

            [harja.domain.kanavat.kanavan-toimenpide :as kanavan-toimenpide]
            [harja.domain.kanavat.kanavan-kohde :as kanavan-kohde]
            [harja.domain.kanavat.kanavan-huoltokohde :as kanavan-huoltokohde]
            [harja.domain.toimenpidekoodi :as toimenpidekoodi]
            [harja.domain.kayttaja :as kayttaja]

            [harja.pvm :as pvm]
            [harja.views.kanavat.urakka.toimenpiteet :as toimenpiteet-view]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.ui.valinnat :as valinnat]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja-laadunseuranta.ui.yleiset.lomake :as lomake]
            [harja.ui.debug :as debug])
  (:require-macros
    [cljs.core.async.macros :refer [go]]
    [harja.makrot :refer [defc fnc]]))

(defn hakuehdot [e! urakka]
  [valinnat/urakkavalinnat {:urakka urakka}
   ^{:key "valinnat"}
   [urakka-valinnat/urakan-sopimus-ja-hoitokausi-ja-aikavali-ja-toimenpide urakka]
   ^{:key "toiminnot"}
   [valinnat/urakkatoiminnot {:urakka urakka :sticky? true}
    ^{:key "uusi-nappi"}
    [napit/uusi
     "Uusi toimenpide"
     (fn [_]
       (e! (tiedot/->UusiToimenpide)))]]])

(defn kokonaishintaiset-toimenpiteet-taulukko [toimenpiteet]
  [grid/grid
   {:otsikko "Kokonaishintaiset toimenpiteet"
    :voi-lisata? false
    :voi-muokata? false
    :voi-poistaa? false
    :voi-kumota? false
    :piilota-toiminnot? true
    :tyhja "Ei kokonaishitaisia toimenpiteita"
    :jarjesta ::kanavan-toimenpide/pvm
    :tunniste ::kanavan-toimenpide/id}
   toimenpiteet-view/toimenpidesarakkeet
   toimenpiteet])

(defn kokonaishintainen-toimenpidelomake [e! toimenpide]
  [napit/takaisin "Takaisin varusteluetteloon"
   #(e! (tiedot/->TyhjennaValittuToimenpide))]

  #_(lomake/lomake
    {:otsikko "Uusi toimenpide"}
    [{:nimi :hilipati
      :otsikko "Hilipati"
      :tyyppi :string}]
    toimenpide))

(defn kokonaishintaiset-nakyma [e! app urakka toimenpiteet valittu-toimenpide]
  [:div
   (if valittu-toimenpide
     [kokonaishintainen-toimenpidelomake e! valittu-toimenpide]
     [:div
      [hakuehdot e! urakka]
      [kokonaishintaiset-toimenpiteet-taulukko toimenpiteet]])
   [debug/debug app]])

(defn kokonaishintaiset* [e! app]
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
    (fn [e! {:keys [toimenpiteet valittu-toimenpide haku-kaynnissa?] :as app}]
      (let [urakka (get-in app [:valinnat :urakka])]
        @tiedot/valinnat ;; Reaktio on pakko lukea komponentissa, muuten se ei päivity!
        [:span
         [kokonaishintaiset-nakyma e! app urakka toimenpiteet valittu-toimenpide]]))))

(defc kokonaishintaiset []
      [tuck tiedot/tila kokonaishintaiset*])