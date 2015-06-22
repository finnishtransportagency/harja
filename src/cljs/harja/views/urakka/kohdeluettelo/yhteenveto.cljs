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


(defonce kohderivit (reaction<! (let [valittu-urakka-id (:id @nav/valittu-urakka)
                                      [valittu-sopimus-id _] @u/valittu-sopimusnumero
                                      valittu-urakan-valilehti @u/urakan-valittu-valilehti]
                                  (when (and valittu-urakka-id valittu-sopimus-id (= valittu-urakan-valilehti :kohdeluettelo))
                                    (log "PÄÄ Haetaan päällystyskohteet.")
                                    (paallystys/hae-paallystyskohteet valittu-urakka-id valittu-sopimus-id)))))

(defn yhteenveto
  []

    (komp/luo
      (fn []
        [:div
         [grid/grid
          {:otsikko "Kohteet"
           :tyhja (if (nil? @kohderivit) [ajax-loader "Haetaan kohteita..."] "Ei kohteita")
           :luokat ["paallysteurakka-kohteet-paasisalto"]
           ; FIXME Tämä rivi on kesken :vetolaatikot (into {} (map (juxt :id (fn [rivi] [yksiloidyt-tehtavat rivi tehtavien-summat])) (filter (fn [rivi] (> (:hoitokauden-toteutunut-maara rivi) 0)) @tyorivit)))
           }
          [{:tyyppi :vetolaatikon-tila :leveys "5%"}
           {:otsikko "#" :nimi :kohdenumero :muokattava? (constantly false) :tyyppi :numero :leveys "5%"}
           {:otsikko "Nimi" :nimi :nimi :muokattava? (constantly false) :tyyppi :string :leveys "15%"}
           {:otsikko "Sop. muk. työt" :nimi :sopimuksen-mukaiset-tyot :muokattava? (constantly false) :fmt fmt/euro-opt :tyyppi :numero :leveys "10%"}
           {:otsikko "Lisätyöt" :nimi :lisatyot :muokattava? (constantly false) :fmt fmt/euro-opt :tyyppi :numero :leveys "10%"}
           {:otsikko "Muutostyöt" :nimi :muutostyot :muokattava? (constantly false) :fmt fmt/euro-opt :tyyppi :numero :leveys "10%"}
           {:otsikko "Arvonväh." :nimi :arvonvahennykset :fmt fmt/euro-opt :muokattava? (constantly false) :tyyppi :numero :leveys "10%"}
           {:otsikko "Bit ind." :nimi :bitumi-indeksi :fmt fmt/euro-opt :muokattava? (constantly false) :tyyppi :numero :leveys "10%"}
           {:otsikko "Kaasuindeksi" :nimi :kaasuindeksi :fmt fmt/euro-opt :muokattava? (constantly false) :tyyppi :numero :leveys "10%"}
           {:otsikko "Kokonaishinta (indeksit mukana)" :nimi :kokonaishinta :fmt fmt/euro-opt :muokattava? (constantly false) :tyyppi :numero :leveys "20%"}
           {:otsikko "Laskutettu" :nimi :laskutettu :fmt fmt/euro-opt :muokattava? (constantly false) :tyyppi :numero :leveys "10%"}]
            @kohderivit]])))