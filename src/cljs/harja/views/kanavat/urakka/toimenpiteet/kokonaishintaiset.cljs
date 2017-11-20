(ns harja.views.kanavat.urakka.toimenpiteet.kokonaishintaiset
  (:require [reagent.core :refer [atom]]
            [tuck.core :refer [tuck]]
            [harja.tiedot.kanavat.urakka.toimenpiteet.kokonaishintaiset :as tiedot]
            [harja.tiedot.navigaatio :as navigaatio]
            [harja.tiedot.urakka :as urakkatiedot]
            [harja.loki :refer [tarkkaile! log]]
            [harja.ui.lomake :as lomake]
            [harja.ui.debug :as debug]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.ui.napit :as napit]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni tietoja]]
            [harja.ui.debug :refer [debug]]
            [harja.ui.valinnat :as valinnat]
            [harja.domain.kanavat.kanavan-toimenpide :as kanavan-toimenpide]
            [harja.views.kanavat.urakka.toimenpiteet :as toimenpiteet-view]
            [harja.views.urakka.valinnat :as urakka-valinnat])
  (:require-macros
    [cljs.core.async.macros :refer [go]]
    [harja.makrot :refer [defc fnc]]))

(defn hakuehdot [e! app]
  (let [urakka (get-in app [:valinnat :urakka])]
    [valinnat/urakkavalinnat {:urakka urakka}
     ^{:key "valinnat"}
     [urakka-valinnat/urakan-sopimus-ja-hoitokausi-ja-aikavali-ja-toimenpide urakka]
     ^{:key "toiminnot"}
     [valinnat/urakkatoiminnot {:urakka urakka :sticky? true}
      ^{:key "uusi-nappi"}
      [napit/uusi
       "Uusi toimenpide"
       (fn [_]
         (e! (tiedot/->UusiToimenpide)))]]]))

(defn kokonaishintaiset-toimenpiteet-taulukko [e! {:keys [toimenpiteet]}]
  [grid/grid
   {:otsikko "Kokonaishintaiset toimenpiteet"
    :voi-lisata? false
    :voi-muokata? false
    :voi-poistaa? false
    :voi-kumota? false
    :piilota-toiminnot? true
    :tyhja "Ei kokonaishitaisia toimenpiteita"
    :jarjesta ::kanavan-toimenpide/pvm
    :tunniste ::kanavan-toimenpide/id
    :rivi-klikattu #(e! (tiedot/->ValitseToimenpide %))}
   toimenpiteet-view/toimenpidesarakkeet
   toimenpiteet])

(defn kokonaishintainen-toimenpidelomake [e! {:keys [valittu-toimenpide
                                                     kohteet
                                                     toimenpideinstanssit
                                                     tehtavat
                                                     huoltokohteet]
                                              :as app}]
  (let [urakka (get-in app [:valinnat :urakka])
        sopimukset (:sopimukset urakka)]
    [:div
     [napit/takaisin "Takaisin varusteluetteloon"
      #(e! (tiedot/->TyhjennaValittuToimenpide))]
     [lomake/lomake
      {:otsikko "Uusi toimenpide"
       :muokkaa! #(e! (tiedot/->AsetaToimenpiteenTiedot %))
       :footer-fn (fn [toimenpide]
                    [:div
                     [napit/tallenna
                      "Tallenna"
                      #(e! (tiedot/->TallennaToimenpide toimenpide))
                      {:tallennus-kaynnissa? tallennus-kaynnissa?
                       :disabled (not (lomake/voi-tallentaa? valittu-toimenpide))}]])}
      (toimenpiteet-view/toimenpidelomakkeen-kentat valittu-toimenpide sopimukset kohteet huoltokohteet toimenpideinstanssit tehtavat)
      valittu-toimenpide]]))

(defn kokonaishintaiset-nakyma [e! {:keys [valittu-toimenpide]
                                    :as app}]
  [:div
   (if valittu-toimenpide
     [kokonaishintainen-toimenpidelomake e! app]
     [:div
      [hakuehdot e! app]
      [kokonaishintaiset-toimenpiteet-taulukko e! app]])
   [debug/debug app]])

(defn kokonaishintaiset* [e! _]
  (komp/luo
    (komp/watcher tiedot/valinnat (fn [_ _ uusi] (e! (tiedot/->PaivitaValinnat uusi))))
    (komp/sisaan-ulos #(e! (tiedot/->NakymaAvattu)) #(e! (tiedot/->NakymaSuljettu)))
    (fn [e! app]
      ;; Reaktio on pakko lukea komponentissa, muuten se ei päivity!
      @tiedot/valinnat
      [:span
       [kokonaishintaiset-nakyma e! app]])))

(defc kokonaishintaiset []
      [tuck tiedot/tila kokonaishintaiset*])
