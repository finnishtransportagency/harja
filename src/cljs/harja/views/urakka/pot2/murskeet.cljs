(ns harja.views.urakka.pot2.murskeet
  "POT2 murskeiden hallintanäkymä"
  (:require [clojure.string :as str]
            [cljs.core.async :refer [<! chan]]
            [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]

            [harja.ui.grid :as grid]
            [harja.ui.debug :refer [debug]]
            [harja.ui.dom :as dom]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :as kentat]
            [harja.ui.komponentti :as komp]
            [harja.ui.lomake :as ui-lomake]
            [harja.ui.modal :as modal]
            [harja.ui.napit :as napit]
            [harja.ui.validointi :as v]
            [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko virheen-ohje] :as yleiset]
            [harja.domain.paallystysilmoitus :as paallystysilmoitus-domain]
            [harja.domain.pot2 :as pot2-domain]
            [harja.tiedot.urakka.pot2.validoinnit :as pot2-validoinnit]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.pot2.materiaalikirjasto :as mk-tiedot]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.views.urakka.pot2.massat :as massat-view]
            [harja.loki :refer [log logt tarkkaile!]])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

(defn murske-lomake [e! {:keys [pot2-murske-lomake materiaalikoodistot] :as app}]
  (let [{:keys [mursketyypit]} materiaalikoodistot
        _ (js/console.log "murske-pot2-murske-lomake :: pot2-murske-lomake " (pr-str pot2-murske-lomake))]
    [:div
     [ui-lomake/lomake
      {:muokkaa! #(e! (mk-tiedot/->PaivitaMurskeLomake (ui-lomake/ilman-lomaketietoja %)))
       :otsikko (if (::pot2-domain/murske-id pot2-murske-lomake)
                  "Muokkaa mursketta"
                  "Uusi murske")
       :footer-fn (fn [data]
                    [:div
                     (when-not (and (empty? (ui-lomake/puuttuvat-pakolliset-kentat data)))
                       [:div
                        [:div "Seuraavat pakolliset kentät pitää täyttää ennen tallentamista: "]
                        [:ul
                         (for [puute (ui-lomake/puuttuvat-pakolliset-kentat data)]
                           ^{:key (name puute)}
                           [:li (name puute)])]])
                     [:div.flex-row
                      [:div.tallenna-peruuta
                       [napit/tallenna
                        "Tallenna"
                        #(e! (mk-tiedot/->TallennaMurskeLomake data))
                        {:vayla-tyyli? true
                         :luokka "suuri"
                         :disabled (not (ui-lomake/voi-tallentaa? data))}]
                       [napit/yleinen
                        "Peruuta" :toissijainen
                        #(e! (mk-tiedot/->TyhjennaMurskeLomake data))
                        {:vayla-tyyli? true
                         :luokka "suuri"}]]

                      [napit/poista
                       "Poista"
                       (fn []
                         (varmista-kayttajalta/varmista-kayttajalta
                           {:otsikko "Murskeen poistaminen"
                            :sisalto
                            [:div "Haluatko ihan varmasti poistaa tämän murskeen?"]
                            :toiminto-fn #(e! (mk-tiedot/->TallennaMurskeLomake (merge data {::pot2-domain/poistettu? true})))
                            :hyvaksy "Kyllä"}))
                       {:vayla-tyyli? true
                        :luokka "suuri"}]]])
       :vayla-tyyli? true}
      [{:otsikko "Murskeen nimi" :muokattava? (constantly false) :nimi ::pot2-domain/murskeen-nimi :tyyppi :string :palstoja 3
        :luokka "bold" :vayla-tyyli? true :kentan-arvon-luokka "placeholder"
        :hae (fn [rivi]
               (if-not (::pot2-domain/tyyppi rivi)
                 "Nimi muodostuu automaattisesti lomakkeeseen täytettyjen tietojen perusteella"
                 (mk-tiedot/murskeen-rikastettu-nimi mursketyypit rivi :string)))}
       (ui-lomake/rivi
         {:otsikko "Nimen tarkenne" :nimi ::pot2-domain/nimen-tarkenne :tyyppi :string
          :vayla-tyyli? true})
       (ui-lomake/rivi
         {:otsikko "" :nimi ::pot2-domain/tyyppi :tyyppi :valinta
          :valinnat (:mursketyypit materiaalikoodistot)
          :valinta-nayta :nimi :valinta-arvo :koodi
          :vayla-tyyli? true})
       (ui-lomake/rivi
         {:otsikko "Rakeisuus"
          :nimi ::pot2-domain/rakeisuus
          :tyyppi :valinta :valinta-nayta (fn [rivi]
                                            (str (:nimi rivi)))
          :vayla-tyyli? true :valinta-arvo :nimi
          :valinnat pot2-domain/murskeen-rakeisuusarvot
          :pakollinen? true}
         {:otsikko "Iskunkestävyys"
          :nimi ::pot2-domain/iskunkestavyys :tyyppi :valinta
          :valinta-nayta (fn [rivi]
                           (str rivi))
          :vayla-tyyli? true
          :valinta-arvo identity
          :valinnat pot2-domain/murskeen-iskunkestavyysarvot
          :pakollinen? true}
         {:otsikko "DoP nro" :nimi ::pot2-domain/dop-nro :tyyppi :string
          :validoi [[:ei-tyhja "Anna DoP nro"]]
          :vayla-tyyli? true :pakollinen? true})]

      pot2-murske-lomake]]))


(defn murskeet-taulukko [e! {:keys [murskeet materiaalikoodistot] :as app}]
  [grid/grid
   {:otsikko "Murskeet"
    :tunniste ::pot2-domain/murske-id
    :tyhja (if (nil? murskeet)
             [ajax-loader "Haetaan urakan murskeita..."]
             "Urakalle ei ole vielä lisätty murskeita")
    :rivi-klikattu #(e! (mk-tiedot/->MuokkaaMursketta % false))
    :voi-lisata? false :voi-kumota? false
    :voi-poistaa? (constantly false) :voi-muokata? true
    :custom-toiminto {:teksti "Luo uusi murske"
                      :toiminto #(e! (mk-tiedot/->UusiMurske))
                      :opts {:ikoni (ikonit/livicon-plus)
                             :luokka "napiton-nappi"}}}
   [{:otsikko "Nimi" :tyyppi :komponentti :leveys 8
     :komponentti (fn [rivi]
                    [mk-tiedot/murskeen-rikastettu-nimi (:mursketyypit materiaalikoodistot) rivi :komponentti])}
    {:otsikko "Tyyppi" :tyyppi :string :muokattava? (constantly false) :leveys 8
     :hae (fn [rivi]
            (mk-tiedot/ainetyypin-koodi->nimi (:mursketyypit materiaalikoodistot) (::pot2-domain/tyyppi rivi)))}
    {:otsikko "Kiviaines\u00ADesiintymä" :nimi ::pot2-domain/esiintyma :tyyppi :string :muokattava? (constantly false) :leveys 8}
    {:otsikko "Rakei\u00ADsuus" :nimi ::pot2-domain/rakeisuus :tyyppi :string :muokattava? (constantly false) :leveys 8}
    {:otsikko "Iskun\u00ADkestävyys" :nimi ::pot2-domain/iskunkestavyys :tyyppi :string :muokattava? (constantly false) :leveys 8}
    {:otsikko "DOP" :nimi ::pot2-domain/dop-nro :tyyppi :string :muokattava? (constantly false) :leveys 8}
    {:otsikko "Toiminnot" :nimi :toiminnot :tyyppi :komponentti :leveys 3
     :komponentti (fn [rivi]
                    [massat-view/massan-toiminnot e! rivi])}]
   murskeet])