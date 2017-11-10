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
            [harja.ui.lomake :as lomake]
            [harja.ui.debug :as debug]
            [clojure.string :as str]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.viesti :as viesti])
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
  [:div
   [napit/takaisin "Takaisin varusteluetteloon"
    #(e! (tiedot/->TyhjennaValittuToimenpide))]
   [lomake/lomake
    {:otsikko "Uusi toimenpide"
     :muokkaa! #(e! (tiedot/->AsetaToimenpiteenTiedot %))
     :footer-fn (fn [toimenpide]
                  [:div
                   [napit/palvelinkutsu-nappi
                    "Tallenna"
                    #(e! (tiedot/->ToimenpideTallennettu %))
                    {:luokka "nappi-ensisijainen"
                     :ikoni (ikonit/tallenna)
                     :kun-onnistuu #(e! (tiedot/->ToimenpideTallennettu %))
                     :kun-virhe #(viesti/nayta! "Toimenpiteen tallennus epäonnistui" :warning viesti/viestin-nayttoaika-keskipitka)
                     :disabled (not (lomake/voi-tallentaa? toimenpide))}]])
     }
    [{:otsikko "Sopimus"
      :nimi ::kanavan-toimenpide/sopimus-id
      :tyyppi :string
      :fmt pvm/pvm-opt}
     {:otsikko "Päivämäärä"
      :nimi ::kanavan-toimenpide/pvm
      :tyyppi :pvm
      :fmt pvm/pvm-opt}
     {:otsikko "Kohde"
      :nimi ::kanavan-toimenpide/kohde-id
      :tyyppi :valinta
      :valinta-arvo :id
      :valinta-nayta :nimi
      ;; todo: hae oikeat arvot
      :valinnat [{:nimi "hilipati"
                  :id 1}
                 {:nimi "pippaa"
                  :id 2}]}
     {:nimi :sijainti
      :otsikko "GPS-sijainti"
      :tyyppi :sijaintivalitsin
      :karttavalinta? false
      :paikannus-onnistui-fn #(
                                ;;todo: laukaise eventti
                                )
      :paikannus-epaonnistui-fn #(
                                   ;;todo: laukaise eventti
                                   )}
     {:otsikko "Huoltokohde"
      :nimi ::kanavan-toimenpide/huoltokohde-id
      :tyyppi :valinta
      :valinta-arvo :id
      :valinta-nayta :nimi
      ;; todo: hae oikeat arvot
      :valinnat [{:nimi "hilipati"
                  :id 1}
                 {:nimi "pippaa"
                  :id 2}]}
     {:otsikko "Toimenpide"
      :nimi ::kanavan-toimenpide/toimenpidekoodi-id
      :tyyppi :valinta
      :valinta-arvo :id
      :valinta-nayta :nimi
      ;; todo: hae oikeat arvot
      :valinnat [{:nimi "hilipati"
                  :id 1}
                 {:nimi "pippaa"
                  :id 2}]}
     {:otsikko "Lisätieto"
      :nimi ::kanavan-toimenpide/lisatieto
      :tyyppi :string}
     {:otsikko "Muu toimenpide"
      :nimi ::kanavan-toimenpide/muu-toimenpide
      :tyyppi :string}
     {:otsikko "Suorittaja"
      :nimi ::kanavan-toimenpide/suorittaja-id
      :tyyppi :valinta
      :valinta-arvo :id
      :valinta-nayta :nimi
      ;; todo: hae oikeat arvot
      :valinnat [{:nimi "hilipati"
                  :id 1}
                 {:nimi "pippaa"
                  :id 2}]}
     {:otsikko "Kuittaaja"
      :nimi ::kanavan-toimenpide/kuittaaja-id
      :tyyppi :valinta
      :valinta-arvo :id
      :valinta-nayta :nimi
      ;; todo: hae oikeat arvot
      :valinnat [{:nimi "hilipati"
                  :id 1}
                 {:nimi "pippaa"
                  :id 2}]}]
    toimenpide]])

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