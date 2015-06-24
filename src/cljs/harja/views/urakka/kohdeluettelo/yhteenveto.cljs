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


(defonce kohderivit (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
                                 [valittu-sopimus-id _] @u/valittu-sopimusnumero
                                 valittu-urakan-valilehti @u/urakan-valittu-valilehti]
                                (when (and valittu-urakka-id valittu-sopimus-id (= valittu-urakan-valilehti :kohdeluettelo))
                                  (log "PÄÄ Haetaan päällystyskohteet.")
                                  (paallystys/hae-paallystyskohteet valittu-urakka-id valittu-sopimus-id))))


(defn paallystyskohdeosat [rivi]
  (let [urakka-id (:id @nav/valittu-urakka)
        [sopimus-id _] @u/valittu-sopimusnumero
        paallystyskohdeosat (atom nil)]

  (go (reset! paallystyskohdeosat (<! (paallystys/hae-paallystyskohdeosat urakka-id sopimus-id (:id rivi)))))

  (fn [rivi]
    [:div
     [grid/grid
      {:otsikko     "Tierekisterikohteet"
       :tyhja       (if (nil? @paallystyskohdeosat) [ajax-loader "Haetaan..."] "Päällystyskohdeosia ei löydy")
       :tunniste    :tr_numero
       :luokat ["paallystyskohdeosat-haitari"]}
      [{:otsikko "Nimi" :nimi :nimi :muokattava? (constantly false) :tyyppi :string :leveys "20%"}
       {:otsikko "Tieosa" :nimi :tr_numero :muokattava? (constantly true) :tyyppi :numero :leveys "10%"}
       {:otsikko "Aot" :nimi :tr_alkuosa :muokattava? (constantly false) :tyyppi :numero :leveys "10%"}
       {:otsikko "Aet" :nimi :tr_alkuetaisyys :muokattava? (constantly false) :tyyppi :numero  :leveys "10%"}
       {:otsikko "Losa" :nimi :tr_loppuosa :muokattava? (constantly false) :tyyppi :numero  :leveys "10%"}
       {:otsikko "Let" :nimi :tr_loppuetaisyys :muokattava? (constantly false) :tyyppi :numero  :leveys "10%"}
       {:otsikko "Pit" :nimi :pit :muokattava? (constantly false) :tyyppi :string :hae (fn [rivi] (if (= (:tr_alkuosa rivi) (:tr_loppuosa rivi))
                                                                                                    (str (- (:tr_loppuetaisyys rivi) (:tr_alkuetaisyys rivi)))
                                                                                                    "-")) ; FIXME Onko oikein päätelty?
        :leveys "10%"}
       {:otsikko "Kvl" :nimi :kvl :muokattava? (constantly false) :tyyppi :numero :leveys "10%"}
       {:otsikko "Nyk. päällyste" :nimi :nykyinen_paallyste :muokattava? (constantly false) :tyyppi :numero :hae (fn [rivi] (paallystys-pot/hae-paallyste-koodilla (:nykyinen_paallyste rivi))) :leveys "10%"}
       {:otsikko "Toimenpide" :nimi :toimenpide :muokattava? (constantly false) :tyyppi :string :leveys "20%"}]
      @paallystyskohdeosat]])))

(defn paallystyskohteet
  []

    (komp/luo
      (fn []
        [:div
         [grid/grid
          {:otsikko "Kohteet"
           :tyhja (if (nil? @kohderivit) [ajax-loader "Haetaan kohteita..."] "Ei kohteita")
           :luokat ["paallysteurakka-kohteet-paasisalto"]
           :vetolaatikot (into {} (map (juxt :kohdenumero (fn [rivi] [paallystyskohdeosat rivi])) @kohderivit))
           :tunniste :kohdenumero}
          [{:tyyppi :vetolaatikon-tila :leveys "5%"}
           {:otsikko "#" :nimi :kohdenumero :muokattava? (constantly false) :tyyppi :numero :leveys "5%"}
           {:otsikko "Kohde" :nimi :nimi :muokattava? (constantly false) :tyyppi :string :leveys "15%"}
           {:otsikko "Tarjoushinta" :nimi :sopimuksen_mukaiset_tyot :muokattava? (constantly false) :fmt fmt/euro-opt :tyyppi :numero :leveys "10%"}
           {:otsikko "Lisätyöt" :nimi :lisatyot :muokattava? (constantly false) :fmt fmt/euro-opt :tyyppi :numero :leveys "10%"}
           {:otsikko "Muutokset" :nimi :muutoshinta :muokattava? (constantly false) :fmt fmt/euro-opt :tyyppi :numero :leveys "10%"}
           {:otsikko "Arvonväh." :nimi :arvonvahennykset :fmt fmt/euro-opt :muokattava? (constantly false) :tyyppi :numero :leveys "10%"}
           {:otsikko "Bit ind." :nimi :bitumi_indeksi :fmt fmt/euro-opt :muokattava? (constantly false) :tyyppi :numero :leveys "10%"}
           {:otsikko "Kaasuindeksi" :nimi :kaasuindeksi :fmt fmt/euro-opt :muokattava? (constantly false) :tyyppi :numero :leveys "10%"}
           {:otsikko "Kokonaishinta (indeksit mukana)" :nimi :kokonaishinta :fmt fmt/euro-opt :hae (fn [rivi] (+ (:sopimuksen_mukaiset_tyot rivi)
                                                                                                                 (:lisatyot rivi)
                                                                                                                 (:muutostyot rivi)
                                                                                                                 (:arvonvahennykset rivi)
                                                                                                                 (:bitumi_indeksi rivi)
                                                                                                                 (:kaasuindeksi rivi))) :muokattava? (constantly false) :tyyppi :numero :leveys "20%"}]
            @kohderivit]])))
