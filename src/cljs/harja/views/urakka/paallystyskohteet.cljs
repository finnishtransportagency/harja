(ns harja.views.urakka.paallystyskohteet
  "Päällystyskohteet"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.lomake :as lomake]
            [harja.ui.kentat :as kentat]
            [harja.ui.yleiset :refer [ajax-loader linkki raksiboksi
                                      livi-pudotusvalikko]]
            [harja.ui.komponentti :as komp]
            [harja.ui.liitteet :as liitteet]
            [harja.tiedot.urakka.paallystys :refer [paallystyskohderivit paallystys-tai-paikkauskohteet-nakymassa paivita-kohde!] :as paallystys]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.domain.paallystys.pot :as paallystys-pot]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.ui.napit :as napit]
            [clojure.string :as str]
            [harja.asiakas.kommunikaatio :as k]
            [cljs.core.async :refer [<!]]
            [harja.tiedot.urakka :as u]

            [harja.tyokalut.vkm :as vkm]
            [harja.views.kartta :as kartta]
            [harja.geo :as geo]
            [harja.ui.tierekisteri :as tierekisteri])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

(defn paallystyskohteet []
  (let [paallystyskohteet (reaction (let [kohteet @paallystyskohderivit]
                                      (filter #(false? (:muu_tyo %))
                                              kohteet)))
        muut-tyot (reaction (let [kohteet @paallystyskohderivit]
                              (filter #(true? (:muu_tyo %)) kohteet)))
        yhteensa (reaction (let [_ @paallystyskohderivit
                                 sopimuksen-mukaiset-tyot-yhteensa (laske-sarakkeen-summa :sopimuksen_mukaiset_tyot)
                                 toteutunut-hinta-yhteensa (laske-sarakkeen-summa :toteutunut_hinta)
                                 muutoshinta-yhteensa (laske-sarakkeen-summa :muutoshinta)
                                 arvonvahennykset-yhteensa (laske-sarakkeen-summa :arvonvahennykset)
                                 bitumi-indeksi-yhteensa (laske-sarakkeen-summa :bitumi_indeksi)
                                 kaasuindeksi-yhteensa (laske-sarakkeen-summa :kaasuindeksi)
                                 kokonaishinta (+ sopimuksen-mukaiset-tyot-yhteensa
                                                  toteutunut-hinta-yhteensa
                                                  muutoshinta-yhteensa
                                                  arvonvahennykset-yhteensa
                                                  bitumi-indeksi-yhteensa
                                                  kaasuindeksi-yhteensa)]
                             [{:id                       0
                               :sopimuksen_mukaiset_tyot sopimuksen-mukaiset-tyot-yhteensa
                               :muutoshinta              muutoshinta-yhteensa
                               :toteutunut_hinta         toteutunut-hinta-yhteensa
                               :arvonvahennykset         arvonvahennykset-yhteensa
                               :bitumi_indeksi           bitumi-indeksi-yhteensa
                               :kaasuindeksi             kaasuindeksi-yhteensa
                               :kokonaishinta            kokonaishinta}]))]

    (komp/luo
      (komp/ulos #(kartta/poista-popup!))
      (komp/lippu paallystys-tai-paikkauskohteet-nakymassa)
      (fn []
        (let [haitari-leveys 5
              id-leveys 10
              kohde-leveys 20
              tarjoushinta-leveys 10
              muutoshinta-leveys 10
              toteutunut-hinta-leveys 10
              arvonvahennykset-leveys 10
              bitumi-indeksi-leveys 10
              kaasuindeksi-leveys 10
              yhteensa-leveys 15
              paallystysnakyma? (= :paallystys (:tyyppi @nav/valittu-urakka))]
          [:div.paallystyskohteet
           [kartta/kartan-paikka]
           [:h3 "Päällystyskohteet"]
           [grid/grid
            {:otsikko                  "Kohteet"
             :tyhja                    (if (nil? @paallystyskohderivit) [ajax-loader "Haetaan kohteita..."] "Ei kohteita")
             :vetolaatikot             (into {} (map (juxt :id (fn [rivi] [paallystyskohdeosat rivi])) @paallystyskohderivit))
             :tallenna                 #(go (let [urakka-id (:id @nav/valittu-urakka)
                                                  [sopimus-id _] @u/valittu-sopimusnumero
                                                  payload (mapv (fn [rivi] (assoc rivi :muu_tyo false)) %)
                                                  _ (log "PÄÄ Lähetetään päällystyskohteet: " (pr-str payload))
                                                  vastaus (<! (paallystys/tallenna-paallystyskohteet urakka-id sopimus-id payload))]
                                              (log "PÄÄ päällystyskohteet tallennettu: " (pr-str vastaus))
                                              (reset! paallystyskohderivit vastaus)))
             :esta-poistaminen?        (fn [rivi] (or (not (nil? (:paallystysilmoitus_id rivi)))
                                                      (not (nil? (:paikkausilmoitus_id rivi)))))
             :esta-poistaminen-tooltip (fn [rivi] "Kohteelle on kirjattu ilmoitus, kohdetta ei voi poistaa.")}
            [{:tyyppi :vetolaatikon-tila :leveys haitari-leveys}
             {:otsikko     "YHA-ID" :nimi :kohdenumero :tyyppi :string :leveys id-leveys
              :validoi     [[:ei-tyhja "Anna kohdenumero"] [:uniikki "Sama kohdenumero voi esiintyä vain kerran."]]
              :muokattava? (fn [rivi] (true? (and (:id rivi) (neg? (:id rivi)))))}
             {:otsikko "Kohde" :nimi :nimi :tyyppi :string :leveys kohde-leveys :validoi [[:ei-tyhja "Anna arvo"]]}
             (when paallystysnakyma?
               {:otsikko "Tarjous\u00ADhinta" :nimi :sopimuksen_mukaiset_tyot :fmt fmt/euro-opt :tyyppi :numero :leveys tarjoushinta-leveys :validoi [[:ei-tyhja "Anna arvo"]]})
             (when paallystysnakyma?
               {:otsikko "Muutok\u00ADset" :nimi :muutoshinta :muokattava? (constantly false) :fmt fmt/euro-opt :tyyppi :numero :leveys muutoshinta-leveys})
             (when-not paallystysnakyma?
               {:otsikko "Toteutunut hinta" :nimi :toteutunut_hinta :muokattava? (constantly false) :fmt fmt/euro-opt :tyyppi :numero :leveys toteutunut-hinta-leveys})
             {:otsikko "Arvon\u00ADväh." :nimi :arvonvahennykset :fmt fmt/euro-opt :tyyppi :numero :leveys arvonvahennykset-leveys :validoi [[:ei-tyhja "Anna arvo"]]}
             {:otsikko "Bitumi\u00ADindeksi" :nimi :bitumi_indeksi :fmt fmt/euro-opt :tyyppi :numero :leveys bitumi-indeksi-leveys :validoi [[:ei-tyhja "Anna arvo"]]}
             {:otsikko "Kaasu\u00ADindeksi" :nimi :kaasuindeksi :fmt fmt/euro-opt :tyyppi :numero :leveys kaasuindeksi-leveys :validoi [[:ei-tyhja "Anna arvo"]]}
             {:otsikko "Kokonais\u00ADhinta (indeksit mukana)" :muokattava? (constantly false) :nimi :kokonaishinta :fmt fmt/euro-opt :tyyppi :numero :leveys yhteensa-leveys
              :hae     (fn [rivi] (+ (:sopimuksen_mukaiset_tyot rivi)
                                     (:muutoshinta rivi)
                                     (:toteutunut_hinta rivi)
                                     (:arvonvahennykset rivi)
                                     (:bitumi_indeksi rivi)
                                     (:kaasuindeksi rivi)))}]
            @paallystyskohteet]

           [grid/grid
            {:otsikko                  "Muut kohteet"       ; NOTE: Muut kohteet ovat alkuperäiseen sopimukseen kuulumattomia töitä.
             :tyhja                    (if (nil? @muut-tyot) [ajax-loader "Haetaan muita töitä..."] "Ei muita töitä")
             :vetolaatikot             (into {} (map (juxt :id (fn [rivi] [paallystyskohdeosat rivi])) @paallystyskohderivit))
             :tallenna                 #(go (let [urakka-id (:id @nav/valittu-urakka)
                                                  [sopimus-id _] @u/valittu-sopimusnumero
                                                  payload (mapv (fn [rivi] (assoc rivi :muu_tyo true)) %)
                                                  _ (log "PÄÄ Lähetetään päällystyskohteet: " (pr-str payload))
                                                  vastaus (<! (paallystys/tallenna-paallystyskohteet urakka-id sopimus-id payload))]
                                              (log "PÄÄ päällystyskohteet tallennettu: " (pr-str vastaus))
                                              (reset! paallystyskohderivit vastaus)))
             :esta-poistaminen?        (fn [rivi] (or (not (nil? (:paallystysilmoitus_id rivi)))
                                                      (not (nil? (:paikkausilmoitus_id rivi)))))
             :esta-poistaminen-tooltip (fn [rivi] "Kohteelle on kirjattu ilmoitus, kohdetta ei voi poistaa.")}
            [{:tyyppi :vetolaatikon-tila :leveys haitari-leveys}
             {:otsikko "Harja-ID" :nimi :kohdenumero :tyyppi :string :leveys id-leveys  :validoi [[:ei-tyhja "Anna kohdenumero"] [:uniikki "Sama kohdenumero voi esiintyä vain kerran."]]}
             {:otsikko "Kohde" :nimi :nimi :tyyppi :string :leveys kohde-leveys :validoi [[:ei-tyhja "Anna arvo"]]}
             (when paallystysnakyma?
               {:otsikko "Tarjous\u00ADhinta" :nimi :sopimuksen_mukaiset_tyot :fmt fmt/euro-opt :tyyppi :numero :leveys tarjoushinta-leveys :validoi [[:ei-tyhja "Anna arvo"]]})
             (when paallystysnakyma?
               {:otsikko "Muutok\u00ADset" :nimi :muutoshinta :muokattava? (constantly false) :fmt fmt/euro-opt :tyyppi :numero :leveys muutoshinta-leveys})
             (when-not paallystysnakyma?
               {:otsikko "Toteutunut hinta" :nimi :toteutunut_hinta :fmt fmt/euro-opt :muokattava? (constantly false) :tyyppi :numero :leveys toteutunut-hinta-leveys})
             {:otsikko "Arvon\u00ADväh." :nimi :arvonvahennykset :fmt fmt/euro-opt :tyyppi :numero :leveys arvonvahennykset-leveys :validoi [[:ei-tyhja "Anna arvo"]]}
             {:otsikko "Bitumi\u00ADindeksi" :nimi :bitumi_indeksi :fmt fmt/euro-opt :tyyppi :numero :leveys bitumi-indeksi-leveys :validoi [[:ei-tyhja "Anna arvo"]]}
             {:otsikko "Kaasu\u00ADindeksi" :nimi :kaasuindeksi :fmt fmt/euro-opt :tyyppi :numero :leveys kaasuindeksi-leveys :validoi [[:ei-tyhja "Anna arvo"]]}
             {:otsikko "Kokonais\u00ADhinta (indeksit mukana)" :muokattava? (constantly false) :nimi :kokonaishinta :fmt fmt/euro-opt :tyyppi :numero :leveys yhteensa-leveys
              :hae     (fn [rivi] (+ (:sopimuksen_mukaiset_tyot rivi)
                                     (:muutoshinta rivi)
                                     (:arvonvahennykset rivi)
                                     (:bitumi_indeksi rivi)
                                     (:kaasuindeksi rivi)))}]
            @muut-tyot]

           [grid/grid
            {:otsikko "Yhteensä"
             :tyhja   (if (nil? {}) [ajax-loader "Lasketaan..."] "")}
            [{:otsikko "" :nimi :tyhja :tyyppi :string :leveys haitari-leveys}
             {:otsikko "" :nimi :kohdenumero :tyyppi :string :leveys id-leveys}
             {:otsikko "" :nimi :nimi :tyyppi :string :leveys kohde-leveys}
             (when paallystysnakyma?
               {:otsikko "Tarjous\u00ADhinta" :nimi :sopimuksen_mukaiset_tyot :fmt fmt/euro-opt :tyyppi :numero :leveys tarjoushinta-leveys})
             (when paallystysnakyma?
               {:otsikko "Muutok\u00ADset" :nimi :muutoshinta :fmt fmt/euro-opt :tyyppi :numero :leveys muutoshinta-leveys})
             (when-not paallystysnakyma?
               {:otsikko "Toteutunut hinta" :nimi :toteutunut_hinta :fmt fmt/euro-opt :tyyppi :numero :leveys toteutunut-hinta-leveys})
             {:otsikko "Arvon\u00ADväh." :nimi :arvonvahennykset :fmt fmt/euro-opt :tyyppi :numero :leveys arvonvahennykset-leveys}
             {:otsikko "Bitumi\u00ADindeksi" :nimi :bitumi_indeksi :fmt fmt/euro-opt :tyyppi :numero :leveys bitumi-indeksi-leveys}
             {:otsikko "Kaasu\u00ADindeksi" :nimi :kaasuindeksi :fmt fmt/euro-opt :tyyppi :numero :leveys kaasuindeksi-leveys}
             {:otsikko "Kokonais\u00ADhinta (indeksit mukana)" :nimi :kokonaishinta :fmt fmt/euro-opt :tyyppi :numero :leveys yhteensa-leveys}
             {:otsikko "" :nimi :muokkaustoiminnot-tyhja :tyyppi :string :leveys 3}]
            @yhteensa]])))))
