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
            [harja.ui.lomake :as ui-lomake]
            [harja.ui.validointi :as v]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko virheen-ohje] :as yleiset]
            [harja.domain.pot2 :as pot2-domain]
            [harja.tiedot.urakka.pot2.materiaalikirjasto :as mk-tiedot]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.views.urakka.pot2.massat :as massat-view]
            [harja.views.urakka.pot2.massa-ja-murske-yhteiset :as mm-yhteiset]

            [harja.loki :refer [log logt tarkkaile!]]
            [harja.ui.komponentti :as komp])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))


(defn murske-lomake [e! {:keys [pot2-murske-lomake materiaalikoodistot] :as app}]
  (let [saa-sulkea? (atom false)
        muokkaustilassa? (atom false)]
    (komp/luo
      (komp/piirretty #(yleiset/fn-viiveella (fn [] (reset! saa-sulkea? true))))
      (komp/klikattu-ulkopuolelle #(when (and @saa-sulkea?
                                              (not @muokkaustilassa?))
                                     (e! (mk-tiedot/->SuljeMurskeLomake)))
                                  {:tarkista-komponentti? true})
      (fn [e! {:keys [pot2-murske-lomake materiaalikoodistot] :as app}]
        (let [{:keys [mursketyypit]} materiaalikoodistot
              murske-id (::pot2-domain/murske-id pot2-murske-lomake)
              sivulle? (:sivulle? pot2-murske-lomake)
              materiaali-kaytossa (::pot2-domain/kaytossa pot2-murske-lomake)
              materiaali-lukittu? (some #(= (:tila %) "lukittu") materiaali-kaytossa)
              voi-muokata? (and
                             (not materiaali-lukittu?)
                             (if (contains? pot2-murske-lomake :voi-muokata?)
                               (:voi-muokata? pot2-murske-lomake)
                               true))]
          (when voi-muokata? (reset! muokkaustilassa? true))
          [:div.murske-lomake
           [ui-lomake/lomake
            {:muokkaa! #(e! (mk-tiedot/->PaivitaMurskeLomake (ui-lomake/ilman-lomaketietoja %)))
             :luokka (when sivulle? "overlay-oikealla overlay-leveampi") :voi-muokata? voi-muokata?
             :sulje-fn (when sivulle? #(e! (mk-tiedot/->SuljeMurskeLomake)))
             :otsikko-komp (fn [data]
                             [:div.lomake-otsikko-pieni (cond
                                                          (false? voi-muokata?)
                                                          "Murskeen tiedot"

                                                          murske-id
                                                          "Muokkaa mursketta"

                                                          :else
                                                          "Uusi murske")])
             :footer-fn (fn [data]
                          [mm-yhteiset/tallennus-ja-puutelistaus e! {:data data
                                                                     :validointivirheet []
                                                                     :tallenna-fn #(e! (mk-tiedot/->TallennaMurskeLomake data))
                                                                     :voi-tallentaa? (not (ui-lomake/voi-tallentaa? data))
                                                                     :peruuta-fn #(e! (mk-tiedot/->SuljeMurskeLomake))
                                                                     :poista-fn #(e! (mk-tiedot/->TallennaMurskeLomake (merge data {::pot2-domain/poistettu? true})))
                                                                     :tyyppi :murske
                                                                     :id murske-id
                                                                     :materiaali-kaytossa materiaali-kaytossa
                                                                     :voi-muokata? voi-muokata?}])
             :vayla-tyyli? true}
            [{:otsikko "" :piilota-label? true :muokattava? (constantly false) :nimi ::pot2-domain/murskeen-nimi :tyyppi :string :palstoja 3
              :luokka "bold" :vayla-tyyli? true :kentan-arvon-luokka "fontti-20"
              :hae (fn [rivi]
                     (if-not (::pot2-domain/tyyppi rivi)
                       "Nimi muodostuu automaattisesti lomakkeeseen täytettyjen tietojen perusteella"
                       (mm-yhteiset/materiaalin-rikastettu-nimi {:tyypit mursketyypit
                                                                 :materiaali rivi
                                                                 :fmt :string})))}
             (when (and (not voi-muokata?)
                        (not materiaali-lukittu?))
               (mm-yhteiset/muokkaa-nappi #(e! (mk-tiedot/->AloitaMuokkaus :pot2-murske-lomake))))
             (when-not voi-muokata? (ui-lomake/lomake-spacer {}))
             (ui-lomake/rivi
               {:otsikko "Nimen tarkenne" :nimi ::pot2-domain/nimen-tarkenne :tyyppi :string
                :vayla-tyyli? true :palstoja (if sivulle? 3 1)})
             (ui-lomake/rivi
               {:otsikko (if voi-muokata? "" "Tyyppi") :piilota-label? (boolean voi-muokata?) :nimi ::pot2-domain/tyyppi :tyyppi :radio-group :pakollinen? true
                :vaihtoehdot (:mursketyypit materiaalikoodistot) :vaihtoehto-arvo ::pot2-domain/koodi
                :vaihtoehto-nayta ::pot2-domain/nimi :vayla-tyyli? true
                :palstoja 3})
             (when (= (pot2-domain/ainetyypin-nimi->koodi mursketyypit "Muu")
                      (::pot2-domain/tyyppi pot2-murske-lomake))
               (ui-lomake/rivi
                 {:otsikko "Tyyppi" :vayla-tyyli? true :pakollinen? true
                  :nimi ::pot2-domain/tyyppi-tarkenne :tyyppi :string}))
             (when-not (or (mk-tiedot/mursketyyppia? mursketyypit "(UUSIO) RA, Asfalttirouhe" pot2-murske-lomake)
                           (mk-tiedot/mursketyyppia-bem-tai-muu? mursketyypit pot2-murske-lomake))
               (ui-lomake/rivi
                 {:otsikko "DoP" :nimi ::pot2-domain/dop-nro :tyyppi :string
                  :palstoja (if sivulle? 3 1)
                  :validoi [[:ei-tyhja "Anna DoP nro"]]
                  :vayla-tyyli? true :pakollinen? true}))
             (when-not (mk-tiedot/mursketyyppia-bem-tai-muu? mursketyypit pot2-murske-lomake)
               (ui-lomake/rivi
                 {:otsikko "Kiviaines\u00ADesiintymä" :nimi ::pot2-domain/esiintyma :tyyppi :string
                  :palstoja (if sivulle? 3 1)
                  :validoi [[:ei-tyhja "Anna esiintymä"]]
                  :vayla-tyyli? true :pakollinen? true}))
             (when (mk-tiedot/nayta-lahde mursketyypit pot2-murske-lomake)
               (ui-lomake/rivi
                 {:otsikko "Esiintymä / lähde" :nimi ::pot2-domain/lahde :tyyppi :string
                  :palstoja (if sivulle? 3 1)
                  :validoi [[:ei-tyhja "Anna esiintymä"]]
                  :vayla-tyyli? true :pakollinen? true}))
             (when-not (mk-tiedot/mursketyyppia-bem-tai-muu? mursketyypit pot2-murske-lomake)
               (ui-lomake/rivi
                 {:otsikko "Rakeisuus" :vayla-tyyli? true :pakollinen? true
                  :palstoja (if sivulle? 3 1)
                  :nimi ::pot2-domain/rakeisuus :tyyppi :valinta
                  :valinnat pot2-domain/murskeen-rakeisuusarvot}))
             (when (= "Muu" (::pot2-domain/rakeisuus pot2-murske-lomake))
               (ui-lomake/rivi
                 {:otsikko "Muu rakeisuus" :vayla-tyyli? true :pakollinen? true
                  :palstoja (if sivulle? 3 1)
                  :validoi [[:ei-tyhja "Anna rakeisuuden arvo"]]
                  :nimi ::pot2-domain/rakeisuus-tarkenne :tyyppi :string}))
             (when-not (mk-tiedot/mursketyyppia-bem-tai-muu? mursketyypit pot2-murske-lomake)
               (ui-lomake/rivi
                 {:otsikko "Iskunkestävyys" :vayla-tyyli? true :pakollinen? true
                  :palstoja (if sivulle? 3 1)
                  :nimi ::pot2-domain/iskunkestavyys :tyyppi :valinta
                  :valinnat pot2-domain/murskeen-iskunkestavyysarvot}))
             (when (= "Muu" (::pot2-domain/tyyppi pot2-murske-lomake))
               (ui-lomake/rivi
                 {:otsikko "Tyyppi" :vayla-tyyli? true :pakollinen? true
                  :palstoja (if sivulle? 3 1)
                  :nimi ::pot2-domain/tyyppi-tarkenne :tyyppi :string}))
             (ui-lomake/lomake-spacer {})]

            pot2-murske-lomake]])))))


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
                    [mm-yhteiset/materiaalin-rikastettu-nimi {:tyypit (:mursketyypit materiaalikoodistot)
                                                              :materiaali rivi
                                                              :fmt :komponentti}])}
    {:otsikko "Tyyppi" :tyyppi :string :muokattava? (constantly false) :leveys 6
     :hae (fn [rivi]
            (pot2-domain/ainetyypin-koodi->nimi (:mursketyypit materiaalikoodistot) (::pot2-domain/tyyppi rivi)))}
    {:otsikko "Kiviaines\u00ADesiintymä" :nimi ::pot2-domain/esiintyma :tyyppi :string :muokattava? (constantly false) :leveys 8}
    {:otsikko "Rakei\u00ADsuus" :nimi ::pot2-domain/rakeisuus :tyyppi :string :muokattava? (constantly false) :leveys 4}
    {:otsikko "Iskun\u00ADkestävyys" :nimi ::pot2-domain/iskunkestavyys :tyyppi :string :muokattava? (constantly false) :leveys 4}
    {:otsikko "DoP" :nimi ::pot2-domain/dop-nro :tyyppi :string :muokattava? (constantly false) :leveys 4}
    {:otsikko "" :nimi :toiminnot :tyyppi :komponentti :leveys 4
     :komponentti (fn [rivi]
                    [mm-yhteiset/materiaalirivin-toiminnot e! rivi])}]
   murskeet])