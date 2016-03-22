(ns harja.views.urakka.toteumat.kokonaishintaiset-tyot
  "Urakan 'Toteumat' välilehden 'Kokonaishintaiset työt' osio"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<! >! chan timeout]]
            [harja.atom :refer [paivita!] :refer-macros [reaction<!]]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.views.kartta.popupit :as popupit]
            [harja.tiedot.navigaatio :as navigaatio]
            [harja.tiedot.urakka.toteumat.kokonaishintaiset-tyot :as tiedot]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.domain.skeema :refer [+tyotyypit+]]
            [harja.views.kartta :as kartta]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :as yleiset]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.lomake :as lomake]
            [harja.domain.roolit :as roolit]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.napit :as napit])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja.makrot :refer [defc fnc]]
                   [reagent.ratom :refer [reaction run!]]))

(defn kokonaishintainen-reitti-klikattu [_ toteuma]
  (popupit/nayta-popup (assoc toteuma :aihe :toteuma-klikattu)))

(defn tehtavan-paivakohtaiset-tiedot [pvm toimenpidekoodi]
  (let [tiedot (atom nil)]
    (go (reset! tiedot
                (<! (tiedot/hae-kokonaishintaisen-toteuman-tiedot (:id @nav/valittu-urakka) pvm toimenpidekoodi))))
    (fn [pvm toimenpidekoodi]
      [grid/grid {:otsikko  "Päivän toteumat"
                  :tunniste :id
                  :tyhja    (if (nil? @tiedot) [ajax-loader "Haetaan tehtävän päiväkohtaisia tietoja..."]
                                               "Tietoja ei löytynyt")}
       [{:otsikko "Suorittaja" :nimi :suorittaja :hae (comp :nimi :suorittaja) :leveys 3}
        {:otsikko "Alkanut" :nimi :alkanut :leveys 2 :fmt pvm/aika}
        {:otsikko "Päättynyt" :nimi :paattynyt :leveys 2 :fmt pvm/aika}
        {:otsikko "Pituus" :nimi :pituus :leveys 3 :fmt fmt/pituus-opt}
        {:otsikko "Lisätietoja" :nimi :lisatieto :leveys 3}
        {:otsikko "Tarkastele koko toteumaa"
         :nimi :tarkastele-toteumaa
         :muokattava? (constantly false)
         :tyyppi :komponentti
         :leveys 2
         :komponentti (fn [rivi]
                        [:button.nappi-toissijainen.nappi-grid
                         {:on-click #(reset! tiedot/valittu-kokonaishintainen-toteuma rivi)}
                         (ikonit/eye-open) " Toteuma"])}]
       (sort-by :alkanut @tiedot)])))

(defn tee-taulukko []
  (let [toteumat (into [] (map-indexed ; Summatuilla riveillä ei ole yksilöivää id:tä, generoidaan omat
                            #(assoc %2 :id %1)
                            @tiedot/haetut-toteumat))]
    [:span
     [grid/grid
      {:otsikko                   "Kokonaishintaisten töiden toteumat"
       :tyhja                     (if @tiedot/haetut-toteumat "Toteumia ei löytynyt" [ajax-loader "Haetaan toteumia."])
       :rivi-klikattu             #(do
                                    (nav/vaihda-kartan-koko! :L)
                                    (reset! tiedot/valittu-paivakohtainen-tehtava %))
       :rivi-valinta-peruttu      #(do (reset! tiedot/valittu-paivakohtainen-tehtava nil))
       :mahdollista-rivin-valinta true
       :max-rivimaara 500
       :max-rivimaaran-ylitys-viesti "Toteumia löytyi yli 500. Tarkenna hakurajausta."
       :tunniste (juxt :pvm :toimenpidekoodi)
       :vetolaatikot (into {}
                           (map (juxt
                                 (juxt :pvm :toimenpidekoodi)
                                 (fn [{:keys [pvm toimenpidekoodi]}]
                                   [tehtavan-paivakohtaiset-tiedot pvm toimenpidekoodi])))
                           toteumat)}
      [{:nimi :tarkemmat-tiedot :tyyppi :vetolaatikon-tila :leveys "3%"}
       {:otsikko "Pvm" :tyyppi :pvm :fmt pvm/pvm :nimi :pvm :leveys "19%"}
       {:otsikko "Tehtävä" :tyyppi :string :nimi :nimi :leveys "38%"}
       {:otsikko "Määrä" :tyyppi :numero :nimi :maara :leveys "10%" :fmt #(fmt/desimaaliluku-opt % 1)}
       {:otsikko "Yksikkö" :tyyppi :numero :nimi :yksikko :leveys "10%"}
       {:otsikko "Lähde" :nimi :lahde :hae #(if (:jarjestelmanlisaama %) "Urak. järj." "Harja") :tyyppi :string :leveys "20%"}]
      toteumat]]))

(defn tee-valinnat []
  [urakka-valinnat/urakan-sopimus-ja-hoitokausi-ja-toimenpide @navigaatio/valittu-urakka]
  (let [urakka @navigaatio/valittu-urakka]
    [:span
     (urakka-valinnat/urakan-sopimus urakka)
     (urakka-valinnat/urakan-hoitokausi urakka)
     (urakka-valinnat/aikavali)
     (urakka-valinnat/urakan-toimenpide+kaikki)
     (urakka-valinnat/urakan-kokonaishintainen-tehtava+kaikki)]))

(defn kokonaishintaisten-toteumien-listaus
  "Kokonaishintaisten töiden toteumat"
  []
  [:div
   (tee-valinnat)
   [napit/uusi "Lisää toteuma" #(reset! tiedot/valittu-kokonaishintainen-toteuma
                                        tiedot/uusi-kokonaishintainen-toteuma)
    {:disabled (not (roolit/voi-kirjata-toteumia? (:id @nav/valittu-urakka)))}]
   (tee-taulukko)])

(defn kokonaishintainen-toteuma-lomake []
  (let [muokattu (reaction @tiedot/valittu-kokonaishintainen-toteuma)
        jarjestelman-lisaama-toteuma? (true? (:jarjestelman-lisaama @muokattu))]
    (fnc []
         [:div
          [napit/takaisin "Takaisin luetteloon" #(reset! tiedot/valittu-kokonaishintainen-toteuma nil)]

          [lomake/lomake
           {:otsikko (if (:id @muokattu) "Luo uusi turvallisuuspoikkeama" "Muokkaa turvallisuuspoikkeamaa")
            :muokkaa! #(do (log "TURPO: " (pr-str %)) (reset! muokattu %))
            :footer   [napit/palvelinkutsu-nappi
                       "Tallenna turvallisuuspoikkeama"
                       #(tiedot/tallenna-kokonaishintainen-toteuma @muokattu)
                       {:luokka       "nappi-ensisijainen"
                        :ikoni        (ikonit/tallenna)
                        :kun-onnistuu #(do
                                        (tiedot/toteuman-tallennus-onnistui %)
                                        (reset! tiedot/valittu-kokonaishintainen-toteuma nil))
                        :disabled     (not (lomake/voi-tallentaa? @muokattu))}]}
           [{:otsikko     "Päivämäärä"
             :nimi        :alkanut
             :pakollinen? true
             :tyyppi      :pvm-aika
             :uusi-rivi?  true
             :aseta (fn [rivi arvo]
                      (-> rivi
                          (assoc :paattynyt arvo)
                          (assoc :alkanut arvo)))
             :muokattava? (constantly (not jarjestelman-lisaama-toteuma?))
             :validoi     [[:ei-tyhja "Valitse päivämäärä"]]
             :varoita     [[:urakan-aikana-ja-hoitokaudella]]}]
           @muokattu]])))

(defn kokonaishintaiset-toteumat []
  (komp/luo
    (komp/kuuntelija :toteuma-klikattu kokonaishintainen-reitti-klikattu)
    (komp/lippu tiedot/nakymassa? tiedot/karttataso-kokonaishintainen-toteuma)

    (fn []
      [:span
       [kartta/kartan-paikka]
       (if @tiedot/valittu-kokonaishintainen-toteuma
         [kokonaishintainen-toteuma-lomake]
         [kokonaishintaisten-toteumien-listaus])])))

(def tyhjenna-popupit-kun-filtterit-muuttuu (run!
                                              @tiedot/haetut-toteumat
                                              (kartta/poista-popup!)))
