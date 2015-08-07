(ns harja.views.urakka.kohdeluettelo.yhteenveto
  "Urakan kohdeluettelon yhteenveto"
  (:require [reagent.core :refer [atom] :as r]
            [harja.tiedot.urakka.laadunseuranta :as laadunseuranta]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.lomake :as lomake]
            [harja.ui.kentat :as kentat]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija linkki sisalla? raksiboksi
                                      livi-pudotusvalikko]]
            [harja.ui.komponentti :as komp]
            [harja.ui.liitteet :as liitteet]
            [harja.tiedot.urakka.paallystys :as paallystys]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.domain.paallystys.pot :as paallystys-pot]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.napit :as napit]
            [clojure.string :as str]
            [harja.asiakas.kommunikaatio :as k]
            [cljs.core.async :refer [<!]]
            [harja.tiedot.urakka :as u])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

(defonce paallystyskohderivit (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
                                           [valittu-sopimus-id _] @u/valittu-sopimusnumero
                                           nakymassa? @paallystys/yhteenvetonakymassa?]
                                          (when (and valittu-urakka-id valittu-sopimus-id nakymassa?)
                                            (log "PÄÄ Haetaan päällystyskohteet.")
                                            (paallystys/hae-paallystyskohteet valittu-urakka-id valittu-sopimus-id))))

(defn laske-yhteenveto [sarake rivit]
  (reduce + (mapv
              (fn [rivi] (sarake rivi))
              @paallystyskohderivit)))

(defn yhteenveto []
  (let [sopimuksen-mukaiset-tyot-yhteensa (reaction (laske-yhteenveto :sopimuksen_mukaiset_tyot @paallystyskohderivit))
        muutoshinta-yhteensa (reaction (laske-yhteenveto :muutoshinta @paallystyskohderivit))
        arvonvahennykset-yhteensa (reaction (laske-yhteenveto :arvonvahennykset @paallystyskohderivit))
        bitumi-indeksi-yhteensa (reaction (laske-yhteenveto :bitumi_indeksi @paallystyskohderivit))
        kaasuindeksi-yhteensa (reaction (laske-yhteenveto :kaasuindeksi @paallystyskohderivit))
        kokonaishinta (reaction
                        (+ @muutoshinta-yhteensa
                           @arvonvahennykset-yhteensa
                           @bitumi-indeksi-yhteensa
                           @kaasuindeksi-yhteensa))]
    [yleiset/taulukkotietonakyma {}
     "Tarjoushinnat yhteensä: " (fmt/euro-opt @sopimuksen-mukaiset-tyot-yhteensa)
     "Muutokset yhteensä: " (fmt/euro-opt @muutoshinta-yhteensa)
     "Arvonvähennykset yhteensä: " (fmt/euro-opt @arvonvahennykset-yhteensa)
     "Bit ind. yhteensä: " (fmt/euro-opt @bitumi-indeksi-yhteensa)
     "Kaasuindeksi yhteensä: " (fmt/euro-opt @kaasuindeksi-yhteensa)
     "Urakan kokonaishinta: " (fmt/euro-opt @kokonaishinta)]))

(defn paallystyskohdeosat [rivi]
  (let [urakka-id (:id @nav/valittu-urakka)
        [sopimus-id _] @u/valittu-sopimusnumero
        paallystyskohdeosat (atom nil)]

    (go (reset! paallystyskohdeosat (<! (paallystys/hae-paallystyskohdeosat urakka-id sopimus-id (:id rivi)))))

    (fn [rivi]
      [:div
       [grid/grid
        {:otsikko  "Tierekisterikohteet"
         :tyhja    (if (nil? @paallystyskohdeosat) [ajax-loader "Haetaan..."] "Tierekisterikohteita ei löydy")
         :tallenna #(go (let [urakka-id (:id @nav/valittu-urakka)
                              [sopimus-id _] @u/valittu-sopimusnumero
                              vastaus (<! (paallystys/tallenna-paallystyskohdeosat urakka-id sopimus-id (:id rivi) %))]
                          (log "PÄÄ päällystyskohdeosat tallennettu: " (pr-str vastaus))
                          (reset! paallystyskohdeosat vastaus)))
         :luokat   ["paallystyskohdeosat-haitari"]}
        [{:otsikko "Nimi" :nimi :nimi :tyyppi :string :leveys "20%" :validoi [[:ei-tyhja "Anna arvo"]]}
         {:otsikko "Tienumero" :nimi :tr_numero :tyyppi :numero :leveys "10%" :validoi [[:ei-tyhja "Anna arvo"]]}
         {:otsikko "Aosa" :nimi :tr_alkuosa :tyyppi :numero :leveys "10%" :validoi [[:ei-tyhja "Anna arvo"]]}
         {:otsikko "Aet" :nimi :tr_alkuetaisyys :tyyppi :numero :leveys "10%" :validoi [[:ei-tyhja "Anna arvo"]]}
         {:otsikko "Losa" :nimi :tr_loppuosa :tyyppi :numero :leveys "10%" :validoi [[:ei-tyhja "Anna arvo"]]}
         {:otsikko "Let" :nimi :tr_loppuetaisyys :tyyppi :numero :leveys "10%" :validoi [[:ei-tyhja "Anna arvo"]]}
         {:otsikko "Pit" :nimi :pit :muokattava? (constantly false) :tyyppi :string :hae (fn [rivi] (str (- (:tr_loppuetaisyys rivi) (:tr_alkuetaisyys rivi))))
          :leveys  "10%" :validoi [[:ei-tyhja "Anna arvo"]]}
         {:otsikko "Kvl" :nimi :kvl :tyyppi :numero :leveys "10%" :validoi [[:ei-tyhja "Anna arvo"]]}
         {:otsikko       "Nykyinen päällyste"
          :nimi          :nykyinen_paallyste
          :fmt           #(paallystys-pot/hae-paallyste-koodilla %)
          :tyyppi        :valinta
          :valinta-arvo  :koodi
          :valinnat      paallystys-pot/+paallystetyypit+
          :validoi       [[:ei-tyhja "Anna päällystetyyppi"]]
          :valinta-nayta :nimi
          :leveys        "20%"
          }
         {:otsikko "Toimenpide" :nimi :toimenpide :tyyppi :string :leveys "20%" :validoi [[:ei-tyhja "Anna arvo"]]}]
        @paallystyskohdeosat]])))

(defn paallystyskohteet []
  (let [paallystyskohteet (reaction (let [kohteet @paallystyskohderivit]
                                            (filter #(false? (:muu_tyo %))
                                                    kohteet)))
        muut-tyot (reaction (let [kohteet @paallystyskohderivit]
                             (filter #(true? (:muu_tyo %)) kohteet)))
        valmiit-kohdenumerot-set (reaction (let [rivit (filter #(not (neg? (:id %))) @paallystyskohderivit)
                                                 kohdenumerot (into #{} (map #(:kohdenumero %) rivit))]
                                             (log "PÄÄ rivit: " (pr-str rivit))
                                             (log "PÄÄ kohdenumerot: " (pr-str kohdenumerot))
                                             kohdenumerot))]

    (komp/luo
      (komp/lippu paallystys/yhteenvetonakymassa?)
      (fn []
        [:div
         [grid/grid
          {:otsikko      "Päällystyskohteet"
           :tyhja        (if (nil? @paallystyskohderivit) [ajax-loader "Haetaan kohteita..."] "Ei kohteita")
           :luokat       ["paallysteurakka-kohteet-paasisalto"]
           :vetolaatikot (into {} (map (juxt :kohdenumero (fn [rivi] [paallystyskohdeosat rivi])) @paallystyskohderivit))
           :tunniste     :kohdenumero
           :tallenna     #(go (let [urakka-id (:id @nav/valittu-urakka)
                                    [sopimus-id _] @u/valittu-sopimusnumero
                                    payload (mapv (fn [rivi] (assoc rivi :muu_tyo false)) %)
                                    _ (log "PÄÄ Lähetetään päällystyskohteet: " (pr-str payload))
                                    vastaus (<! (paallystys/tallenna-paallystyskohteet urakka-id sopimus-id payload))]
                                (log "PÄÄ päällystyskohteet tallennettu: " (pr-str vastaus))
                                (reset! paallystyskohderivit vastaus)))
           :voi-poistaa? (fn [rivi] (nil? (:paallystysilmoitus_id rivi)))}
          [{:tyyppi :vetolaatikon-tila :leveys "5%"}
           {:otsikko     "YHA ID" :nimi :kohdenumero :tyyppi :string :leveys "10%" :validoi [[:ei-tyhja "Anna arvo"]
                                                                                        [:uusi-arvo-ei-setissa valmiit-kohdenumerot-set "Kohdenumero on jo olemassa!"]]
            :muokattava? (fn [rivi] (true? (and (:id rivi) (neg? (:id rivi)))))}
           {:otsikko "Kohde" :nimi :nimi :tyyppi :string :leveys "20%" :validoi [[:ei-tyhja "Anna arvo"]]}
           {:otsikko "Tarjoushinta" :nimi :sopimuksen_mukaiset_tyot :fmt fmt/euro-opt :tyyppi :numero :leveys "10%" :validoi [[:ei-tyhja "Anna arvo"]]}
           {:otsikko "Muutokset" :nimi :muutoshinta :muokattava? (constantly false) :fmt fmt/euro-opt :tyyppi :numero :leveys "10%"}
           {:otsikko "Arvonväh." :nimi :arvonvahennykset :fmt fmt/euro-opt :tyyppi :numero :leveys "10%" :validoi [[:ei-tyhja "Anna arvo"]]}
           {:otsikko "Bit ind." :nimi :bitumi_indeksi :fmt fmt/euro-opt :tyyppi :numero :leveys "10%" :validoi [[:ei-tyhja "Anna arvo"]]}
           {:otsikko "Kaasuindeksi" :nimi :kaasuindeksi :fmt fmt/euro-opt :tyyppi :numero :leveys "10%" :validoi [[:ei-tyhja "Anna arvo"]]}
           {:otsikko "Kokonaishinta (indeksit mukana)" :muokattava? (constantly false) :nimi :kokonaishinta :fmt fmt/euro-opt :hae (fn [rivi] (+ (:sopimuksen_mukaiset_tyot rivi)
                                                                                                                                                 (:muutoshinta rivi)
                                                                                                                                                 (:arvonvahennykset rivi)
                                                                                                                                                 (:bitumi_indeksi rivi)
                                                                                                                                                 (:kaasuindeksi rivi)))
            :tyyppi  :numero :leveys "20%" :validoi [[:ei-tyhja "Anna arvo"]]}]
          @paallystyskohteet]

         [grid/grid
          {:otsikko      "Muut työt" ; NOTE: Muut työt ovat alkuperäiseen sopimukseen kuulumattomia töitä.
           :tyhja        (if (nil? {}) [ajax-loader "Haetaan muita töitä..."] "Ei muita töitä")
           :tunniste     :kohdenumero
           :tallenna     #(go (let [urakka-id (:id @nav/valittu-urakka)
                                    [sopimus-id _] @u/valittu-sopimusnumero
                                    payload (mapv (fn [rivi] (assoc rivi :muu_tyo true)) %)
                                    _ (log "PÄÄ Lähetetään päällystyskohteet: " (pr-str payload))
                                    vastaus (<! (paallystys/tallenna-paallystyskohteet urakka-id sopimus-id payload))]
                                (log "PÄÄ päällystyskohteet tallennettu: " (pr-str vastaus))
                                (reset! paallystyskohderivit vastaus)))
           :voi-poistaa? (fn [rivi] (nil? (:paallystysilmoitus_id rivi)))}
          [{:otsikko "Harja-ID" :nimi :kohdenumero :tyyppi :string :leveys "10%" :validoi [[:ei-tyhja "Anna arvo"]]}
           {:otsikko "Kohde" :nimi :nimi :tyyppi :string :leveys "35%" :validoi [[:ei-tyhja "Anna arvo"]]}
           {:otsikko "Tarjoushinta" :nimi :sopimuksen_mukaiset_tyot :fmt fmt/euro-opt :tyyppi :numero :leveys "10%" :validoi [[:ei-tyhja "Anna arvo"]]}
           {:otsikko "Muutokset" :nimi :muutoshinta :muokattava? (constantly false) :fmt fmt/euro-opt :tyyppi :numero :leveys "10%"}
           {:otsikko "Arvonväh." :nimi :arvonvahennykset :fmt fmt/euro-opt :tyyppi :numero :leveys "10%" :validoi [[:ei-tyhja "Anna arvo"]]}
           {:otsikko "Bit ind." :nimi :bitumi_indeksi :fmt fmt/euro-opt :tyyppi :numero :leveys "10%" :validoi [[:ei-tyhja "Anna arvo"]]}
           {:otsikko "Kaasuindeksi" :nimi :kaasuindeksi :fmt fmt/euro-opt :tyyppi :numero :leveys "10%" :validoi [[:ei-tyhja "Anna arvo"]]}
           {:otsikko "Kokonaishinta (indeksit mukana)" :nimi :kokonaishinta :fmt fmt/euro-opt :hae (fn [rivi] (+ (:sopimuksen_mukaiset_tyot rivi)
                                                                                                                 (:muutoshinta rivi)
                                                                                                                 (:arvonvahennykset rivi)
                                                                                                                 (:bitumi_indeksi rivi)
                                                                                                                 (:kaasuindeksi rivi))) :muokattava? (constantly false) :tyyppi :numero :leveys "20%"}]
          @muut-tyot]

         (yhteenveto)]))))