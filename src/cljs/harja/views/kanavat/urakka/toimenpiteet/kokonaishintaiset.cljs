(ns harja.views.kanavat.urakka.toimenpiteet.kokonaishintaiset
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :refer [tuck]]
            [harja.tiedot.kanavat.urakka.toimenpiteet.kokonaishintaiset :as tiedot]
            [harja.loki :refer [tarkkaile! log]]
            [harja.id :refer [id-olemassa?]]
            [harja.ui.lomake :as lomake]
            [harja.ui.debug :as debug]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.viesti :as viesti]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.ui.napit :as napit]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni tietoja]]
            [harja.ui.debug :refer [debug]]
            [harja.domain.kanavat.kanavan-toimenpide :as kanavan-toimenpide]
            [harja.domain.kanavat.kanavan-kohde :as kanavan-kohde]
            [harja.domain.kanavat.kanavan-huoltokohde :as kanavan-huoltokohde]
            [harja.domain.kayttaja :as kayttaja]
            [harja.pvm :as pvm]
            [harja.views.kanavat.urakka.toimenpiteet :as toimenpiteet-view]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.ui.valinnat :as valinnat]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.navigaatio :as navigaatio]
            [harja.tiedot.urakka.urakan-toimenpiteet :as urakan-toimenpiteet]
            [clojure.string :as str])
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

(defn valittu-tehtava-muu? [toimenpide tehtavat]
  (and
    tehtavat
    (some #(= % (::kanavan-toimenpide/toimenpidekoodi-id toimenpide))
          (map :id
               (filter #(and
                          (:nimi %)
                          (not= -1 (.indexOf (str/upper-case (:nimi %)) "MUU"))) tehtavat)))))

(defn kokonaishintainen-toimenpidelomake [e! toimenpide sopimukset kohteet huoltokohteet toimenpideinstanssit tehtavat]
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
                    #(e! (tiedot/->TallennaToimenpide toimenpide))
                    {:luokka "nappi-ensisijainen"
                     :ikoni (ikonit/tallenna)
                     :disabled (not (lomake/voi-tallentaa? toimenpide))}]])}
    [{:otsikko "Sopimus"
      :nimi ::kanavan-toimenpide/sopimus-id
      :tyyppi :valinta
      :valinta-arvo first
      :valinta-nayta second
      :valinnat sopimukset
      :pakollinen? true}
     {:otsikko "Päivämäärä"
      :nimi ::kanavan-toimenpide/pvm
      :tyyppi :pvm
      :fmt pvm/pvm-opt
      :pakollinen? true}
     {:otsikko "Kohde"
      :nimi ::kanavan-toimenpide/kohde-id
      :tyyppi :valinta
      :valinta-arvo ::kanavan-kohde/id
      :valinta-nayta #(or (::kanavan-kohde/nimi %) "- Valitse kohde -")
      :valinnat kohteet}
     #_{:nimi :sijainti
        :otsikko "Sijainti"
        :tyyppi :sijaintivalitsin
        :paikannus? false
        :muokattava? (constantly true)
        :karttavalinta-tehty-fn #()}
     {:otsikko "Huoltokohde"
      :nimi ::kanavan-toimenpide/huoltokohde-id
      :tyyppi :valinta
      :valinta-arvo ::kanavan-huoltokohde/id
      :valinta-nayta #(or (::kanavan-huoltokohde/nimi %) "- Valitse huoltokohde-")
      :valinnat huoltokohteet
      :pakollinen? true}
     {:otsikko "Toimenpide"
      :nimi :toimenpide
      :pakollinen? true
      :tyyppi :valinta
      :uusi-rivi? true
      :valinnat toimenpideinstanssit
      :fmt #(:tpi_nimi
              (urakan-toimenpiteet/toimenpideinstanssi-idlla % toimenpideinstanssit))
      :valinta-arvo :tpi_id
      :valinta-nayta #(if % (:tpi_nimi %) "- Valitse toimenpide -")
      :hae (comp :id :toimenpideinstanssi :tehtava)
      :aseta (fn [rivi arvo]
               (-> rivi
                   (assoc-in [:tehtava :toimenpideinstanssi :id] arvo)
                   (assoc-in [:tehtava :toimenpidekoodi :id] nil)
                   (assoc-in [:tehtava :yksikko] nil)))}
     {:otsikko "Tehtävä"
      :nimi ::kanavan-toimenpide/toimenpidekoodi-id
      :pakollinen? true
      :tyyppi :valinta
      :valinnat tehtavat
      :valinta-arvo :id
      :valinta-nayta #(if % (:nimi %) "- Valitse tehtävä -")
      :hae (comp :tpk-id :tehtava)
      :aseta (fn [rivi arvo]
               (-> rivi
                   (assoc-in [:tehtava :tpk-id] arvo)
                   (assoc-in [:tehtava :yksikko] (:yksikko
                                                   (urakan-toimenpiteet/tehtava-idlla
                                                     arvo tehtavat)))
                   (assoc ::kanavan-toimenpide/toimenpidekoodi-id arvo)))}
     (when (valittu-tehtava-muu? toimenpide tehtavat)
       {:otsikko "Muu toimenpide"
        :nimi ::kanavan-toimenpide/muu-toimenpide
        :tyyppi :string})
     {:otsikko "Lisätieto"
      :nimi ::kanavan-toimenpide/lisatieto
      :tyyppi :string}
     {:otsikko "Suorittaja"
      :nimi ::kanavan-toimenpide/suorittaja
      :tyyppi :string
      :pakollinen? true}
     {:otsikko "Kuittaaja"
      :nimi ::kanavan-toimenpide/kuittaaja
      :tyyppi :string
      :hae #(kayttaja/kokonimi (::kanavan-toimenpide/kuittaaja %))
      :muokattava? (constantly false)}]
    toimenpide]])

(defn kokonaishintaiset-nakyma [e! app urakka toimenpiteet valittu-toimenpide sopimukset kohteet huoltokohteet toimenpideinstanssit tehtavat]
  [:div
   (if valittu-toimenpide
     [kokonaishintainen-toimenpidelomake e! valittu-toimenpide sopimukset kohteet huoltokohteet toimenpideinstanssit tehtavat]
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
                               {:urakka @navigaatio/valittu-urakka
                                :sopimus-id (first @u/valittu-sopimusnumero)
                                :aikavali @u/valittu-aikavali
                                :toimenpide @u/valittu-toimenpideinstanssi})))
                      #(do
                         (e! (tiedot/->Nakymassa? false))))
    (fn [e! {:keys [toimenpiteet valittu-toimenpide kohteet toimenpideinstanssit tehtavat huoltokohteet] :as app}]
      (let [urakka (get-in app [:valinnat :urakka])
            sopimukset (:sopimukset urakka)]
        @tiedot/valinnat ;; Reaktio on pakko lukea komponentissa, muuten se ei päivity!
        [:span
         [kokonaishintaiset-nakyma e! app urakka toimenpiteet valittu-toimenpide sopimukset kohteet huoltokohteet toimenpideinstanssit tehtavat]]))))

(defc kokonaishintaiset []
      [tuck tiedot/tila kokonaishintaiset*])