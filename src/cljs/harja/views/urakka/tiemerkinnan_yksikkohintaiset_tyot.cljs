(ns harja.views.urakka.tiemerkinnan-yksikkohintaiset-tyot
  (:require [reagent.core :refer [atom] :as reagent]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as yleiset :refer [ajax-loader linkki raksiboksi
                                                  alasveto-ei-loydoksia livi-pudotusvalikko radiovalinta vihje]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.urakan-toimenpiteet :as urakan-toimenpiteet]

            [harja.loki :refer [log logt tarkkaile!]]
            [harja.tiedot.urakka.tiemerkinnan-yksikkohintaiset-tyot :as tiedot]
            [cljs.core.async :refer [<!]]

            [harja.views.urakka.valinnat :as valinnat]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.tierekisteri :as tr-domain]
            [harja.domain.paallystysilmoitus :as pot]
            [harja.ui.viesti :as viesti]
            [harja.asiakas.kommunikaatio :as k]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]))

(defn yksikkohintaiset-tyot
  [urakka]
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (fn [urakka]
      (let [urakka-id (:id urakka)
            saa-muokata? (oikeudet/voi-kirjoittaa? oikeudet/urakat-toteutus-yksikkohintaisettyot urakka-id)]
        [:div
         [grid/grid
          {:otsikko "Yksikköhintaiset työt"
           :tyhja (if (nil? @tiedot/yksikkohintaiset-tyot)
                    [ajax-loader "Haetaan töitä..."]
                    "Töitä ei löytynyt")
           :voi-poistaa? (constantly false)
           :voi-lisata? false
           :piilota-toiminnot? true
           :tallenna (if saa-muokata?
                       #(go (let [vastaus (<! (tiedot/tallenna-tiemerkinnan-yksikkohintaiset-tyot urakka-id %))]
                              (if (k/virhe? vastaus)
                                (viesti/nayta! "Tallentaminen epäonnistui"
                                               :warning viesti/viestin-nayttoaika-lyhyt)
                                (reset! tiedot/yksikkohintaiset-tyot vastaus))))
                       :ei-mahdollinen)}
          [{:otsikko "Koh\u00ADde\u00ADnu\u00ADme\u00ADro" :leveys 3 :nimi :kohdenumero :tyyppi :string
            :pituus-max 128 :muokattava? (constantly false)}
           {:otsikko "Koh\u00ADteen nimi" :leveys 7 :nimi :nimi :tyyppi :string :pituus-max 128
            :muokattava? (constantly false)}
           {:otsikko "Tie\u00ADnu\u00ADme\u00ADro" :nimi :tr-numero
            :tyyppi :positiivinen-numero :leveys 3 :tasaa :oikea
            :muokattava? (constantly false)}
           {:otsikko "Ajo\u00ADrata"
            :nimi :tr-ajorata
            :muokattava? (constantly false)
            :tyyppi :string
            :tasaa :oikea
            :fmt (fn [arvo] (:koodi (first (filter #(= (:koodi %) arvo) pot/+ajoradat+))))
            :leveys 3}
           {:otsikko "Kais\u00ADta"
            :muokattava? (constantly false)
            :nimi :tr-kaista
            :tyyppi :string
            :tasaa :oikea
            :fmt (fn [arvo] (:nimi (first (filter #(= (:koodi %) arvo) pot/+kaistat+))))
            :leveys 3}
           {:otsikko "Aosa" :nimi :tr-alkuosa :leveys 3
            :tyyppi :positiivinen-numero
            :tasaa :oikea
            :muokattava? (constantly false)}
           {:otsikko "Aet" :nimi :tr-alkuetaisyys :leveys 3
            :tyyppi :positiivinen-numero
            :tasaa :oikea
            :muokattava? (constantly false)}
           {:otsikko "Losa" :nimi :tr-loppuosa :leveys 3
            :tyyppi :positiivinen-numero
            :tasaa :oikea
            :muokattava? (constantly false)}
           {:otsikko "Let" :nimi :tr-loppuetaisyys :leveys 3
            :tyyppi :positiivinen-numero
            :tasaa :oikea
            :muokattava? (constantly false)}
           {:otsikko "Pit. (m)" :nimi :tr-pituus :leveys 3
            :tyyppi :positiivinen-numero
            :tasaa :oikea
            :muokattava? (constantly false)}
           {:otsikko "YP-lk"
            :nimi :yllapitoluokka :tyyppi :numero :leveys 4
            :fmt :lyhyt-nimi
            :muokattava? (constantly false)}
           {:otsikko "Hinta"
            :nimi :hinta :tyyppi :positiivinen-numero :fmt fmt/euro-opt :leveys 3
            :tasaa :oikea
            :muokattava? (constantly saa-muokata?)
            :huomio (fn [rivi]
                      (when (:hinnan-kohde-muuttunut? rivi)
                        {:tyyppi :varoitus
                        :teksti (str "Koh\u00ADteen oso\u00ADite on muut\u00ADtunut.\n
                                      Hin\u00ADta on annet\u00ADtu koh\u00ADteen vanhal\u00ADle osoit\u00ADteelle:\n"
                                     (:hinta-kohteelle rivi))}))}
           {:otsikko "Hintatyyppi"
            :nimi :hintatyyppi :tyyppi :valinta :leveys 5
            :valinta-arvo identity
            :fmt #(case %
                   :suunnitelma "Suunnitelma"
                   :toteuma "Toteuma"
                   "")
            :valinnat [:suunnitelma :toteuma]
            :valinta-nayta #(case %
                             :suunnitelma "Suunnitelma"
                             :toteuma "Toteuma"
                             "- valitse -")
            :muokattava? (constantly saa-muokata?)}
           {:otsikko "Muutospvm"
            :nimi :muutospvm :tyyppi :pvm :leveys 4
            :fmt pvm/pvm-opt
            :muokattava? (constantly saa-muokata?)}]
          (sort-by tr-domain/tiekohteiden-jarjestys @tiedot/yksikkohintaiset-tyot)]]))))