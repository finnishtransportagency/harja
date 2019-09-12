(ns harja.views.urakka.tiemerkinnan-yksikkohintaiset-tyot
  (:require [reagent.core :refer [atom] :as reagent]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as yleiset :refer [ajax-loader linkki raksiboksi
                                                  alasveto-ei-loydoksia livi-pudotusvalikko vihje]]
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
            [harja.fmt :as fmt]
            [harja.domain.yllapitokohde :as yllapitokohteet-domain]
            [harja.domain.yllapitokohde :as yllapitokohde-domain])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]))

(defn toteutuneet-tiemerkinnat
  [urakka tiemerkinnan-toteumat-atom paallystysurakan-kohteet vuosi]
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (fn [urakka tiemerkinnan-toteumat-atom paallystysurakan-kohteet vuosi]
      (let [urakka-id (:id urakka)
            saa-muokata? (oikeudet/voi-kirjoittaa? oikeudet/urakat-toteutus-yksikkohintaisettyot urakka-id)]
        [:div
         [grid/grid
          {:otsikko "Toteutuneet tiemerkinnät"
           :tyhja (if (nil? @tiemerkinnan-toteumat-atom)
                    [ajax-loader "Haetaan töitä..."]
                    "Toteumia ei löytynyt")
           :tallenna (if saa-muokata?
                       (fn [toteumat]
                         (tiedot/tallenna-toteumat-grid
                           {:toteumat toteumat
                            :urakka-id urakka-id
                            :vuosi vuosi
                            :tiemerkinnan-toteumat-atom tiemerkinnan-toteumat-atom
                            :paallystysurakan-kohteet paallystysurakan-kohteet
                            :epaonnistui-fn #(viesti/nayta! "Tallentaminen epäonnistui"
                                                            :warning viesti/viestin-nayttoaika-lyhyt)}))
                       :ei-mahdollinen)}
          [{:otsikko "Pvm"
            :nimi :paivamaara :tyyppi :pvm :leveys 3
            :validoi [[:ei-tyhja "Anna päivämäärä"]]
            :fmt pvm/pvm-opt}
           {:otsikko "Liittyy kohteeseen" :leveys 7 :nimi :yllapitokohde-id :tyyppi :valinta
            :valinnat (conj (map :id paallystysurakan-kohteet) nil)
            :valinta-nayta #(if % (yllapitokohde-domain/yllapitokohde-tekstina
                                    (tiedot/paallystysurakan-kohde-idlla paallystysurakan-kohteet %))
                                  "Ei liity kohteeseen")
            :fmt #(if-let [kohde (tiedot/paallystysurakan-kohde-idlla paallystysurakan-kohteet %)]
                    (yllapitokohde-domain/yllapitokohde-tekstina kohde)
                    "Ei liity kohteeseen")}
           {:otsikko "Selite" :leveys 7 :nimi :selite :tyyppi :string :pituus-max 512
            :validoi [[:ei-tyhja "Anna selite"]]}

           ;; Pituus ja tienumero on annettava jos ei linkitetä suoraan ylläpitokohteeseen
           {:otsikko "Tie\u00ADnu\u00ADme\u00ADro" :nimi :tr-numero :desimaalien-maara 0
            :tyyppi :positiivinen-numero :leveys 3 :tasaa :oikea
            :muokattava? #(boolean (not (:yllapitokohde-id %)))
            :hae #(if-let [yllapitokohde-id (:yllapitokohde-id %)]
                    (:tr-numero (tiedot/paallystysurakan-kohde-idlla paallystysurakan-kohteet yllapitokohde-id))
                    (:tr-numero %))
            :validoi [[:ei-tyhja-jos-toinen-avain-nil :yllapitokohde-id "Anna tienumero"]]}
           {:otsikko "Pit. (m)" :nimi :pituus :leveys 3
            :tyyppi :positiivinen-numero :desimaalien-maara 0
            :tasaa :oikea
            :hae #(if-let [yllapitokohde-id (:yllapitokohde-id %)]
                    (:pituus (tiedot/paallystysurakan-kohde-idlla paallystysurakan-kohteet yllapitokohde-id))
                    (:pituus %))
            :muokattava? #(boolean (not (:yllapitokohde-id %)))
            :validoi [[:ei-tyhja-jos-toinen-avain-nil :yllapitokohde-id "Anna pituus"]]}

           {:otsikko "YP-lk"
            :nimi :yllapitoluokka :leveys 3 :tyyppi :valinta :desimaalien-maara 0
            :valinnat (map :numero yllapitokohteet-domain/nykyiset-yllapitoluokat)
            :valinta-nayta #(do (if % (yllapitokohteet-domain/yllapitoluokkanumero->lyhyt-nimi %) "-"))
            :fmt yllapitokohteet-domain/yllapitoluokkanumero->lyhyt-nimi
            :muokattava? #(boolean (not (:yllapitokohde-id %)))}
           {:otsikko "Hinta"
            :validoi [[:ei-tyhja "Anna hinta"]]
            :nimi :hinta :tyyppi :positiivinen-numero :fmt fmt/euro-opt :leveys 3
            :tasaa :oikea
            :huomio (fn [rivi]
                      (let [hinnan-kohde-muuttunut?
                            (tiedot/toteuman-hinnan-kohde-muuttunut?
                              rivi
                              (tiedot/paallystysurakan-kohde-idlla paallystysurakan-kohteet
                                                                   (:yllapitokohde-id rivi)))]
                        (when hinnan-kohde-muuttunut?
                          {:tyyppi :varoitus
                           :teksti (str "Koh\u00ADteen oso\u00ADite on muut\u00ADtunut.\n
                                      Hin\u00ADta on annet\u00ADtu koh\u00ADteen vanhal\u00ADle osoit\u00ADteelle:\n"
                                        (:hinta-kohteelle rivi))})))}
           {:otsikko "Hintatyyppi"
            :nimi :hintatyyppi :tyyppi :valinta :leveys 3
            :validoi [[:ei-tyhja "Anna hintatyyppi"]]
            :valinta-arvo identity
            :fmt #(case %
                    :suunnitelma "Suunnitelma"
                    :toteuma "Toteuma"
                    "")
            :valinnat [:suunnitelma :toteuma]
            :valinta-nayta #(case %
                              :suunnitelma "Suunnitelma"
                              :toteuma "Toteuma"
                              "- valitse -")}]
          (yllapitokohteet-domain/jarjesta-yllapitokohteet @tiemerkinnan-toteumat-atom)]]))))

(defn- paallystysurakan-kohteet-vetolaatikko [kohdeosat]
  [grid/grid
   {:otsikko "Tierekisterikohteet"
    :tyhja "Ei kohteita"}
   [{:otsikko "Nimi" :leveys 7 :nimi :nimi :tyyppi :string :pituus-max 128
     :muokattava? (constantly false)}
    {:otsikko "Tie\u00ADnu\u00ADme\u00ADro" :nimi :tr-numero
     :tyyppi :positiivinen-numero :leveys 3 :tasaa :oikea
     :muokattava? (constantly false)}
    {:otsikko "Ajo\u00ADrata"
     :nimi :tr-ajorata
     :muokattava? (constantly false)
     :tyyppi :string
     :tasaa :oikea
     :fmt #(pot/arvo-koodilla pot/+ajoradat-numerona+ %)
     :leveys 3}
    {:otsikko "Kais\u00ADta"
     :muokattava? (constantly false)
     :nimi :tr-kaista
     :tyyppi :string
     :tasaa :oikea
     :fmt #(pot/arvo-koodilla pot/+kaistat+ %)
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
     :muokattava? (constantly false)}]
   (yllapitokohde-domain/jarjesta-yllapitokohteet kohdeosat)])

(defn paallystysurakan-kohteet
  [urakka paallystysurakan-kohteet]
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (fn [urakka paallystysurakan-kohteet]
      (let [urakka-id (:id urakka)]
        [:div
         [grid/grid
          {:otsikko "Päällystysurakassa tehdyt päällystykset"
           :tyhja (if (nil? paallystysurakan-kohteet)
                    [ajax-loader "Haetaan töitä..."]
                    "Kohteita ei löytynyt")
           :vetolaatikot (into {}
                               (map (juxt :id
                                          (fn [rivi]
                                            [paallystysurakan-kohteet-vetolaatikko
                                             (:kohdeosat rivi)]))
                                    paallystysurakan-kohteet))}
          [{:tyyppi :vetolaatikon-tila :leveys 1}
           {:otsikko "Koh\u00ADde\u00ADnu\u00ADme\u00ADro" :leveys 3 :nimi :kohdenumero :tyyppi :string
            :pituus-max 128 :muokattava? (constantly false)}
           {:otsikko "YHA-id" :leveys 3 :nimi :yha-id :tyyppi :string
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
            :fmt #(pot/arvo-koodilla pot/+ajoradat-numerona+ %)
            :leveys 3}
           {:otsikko "Kais\u00ADta"
            :muokattava? (constantly false)
            :nimi :tr-kaista
            :tyyppi :string
            :tasaa :oikea
            :fmt #(pot/arvo-koodilla pot/+kaistat+ %)
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
           {:otsikko "Pit. (m)" :nimi :pituus :leveys 3
            :tyyppi :positiivinen-numero
            :tasaa :oikea
            :muokattava? (constantly false)}
           {:otsikko "YP-lk"
            :nimi :yllapitoluokka :tyyppi :numero :leveys 4
            :fmt #(yllapitokohteet-domain/yllapitoluokkanumero->lyhyt-nimi %)
            :muokattava? (constantly false)}]
          (yllapitokohteet-domain/jarjesta-yllapitokohteet paallystysurakan-kohteet)]]))))

(defn- yhteenveto [toteutuneet-tiemerkinnat]
  (let [suunniteltu-yhteensa (->> toteutuneet-tiemerkinnat
                                  (filter #(= (:hintatyyppi %) :suunnitelma))
                                  (map :hinta)
                                  (reduce +))
        toteumat-yhteensa (->> toteutuneet-tiemerkinnat
                               (filter #(= (:hintatyyppi %) :toteuma))
                               (map :hinta)
                               (reduce +))
        kaikki-yhteensa (+ suunniteltu-yhteensa toteumat-yhteensa)]
    [yleiset/taulukkotietonakyma {}
     "Suunnitellut työt yhteensä:"
     (fmt/euro-opt suunniteltu-yhteensa)

     "Toteumat yhteensä:"
     (fmt/euro-opt toteumat-yhteensa)

     "Kaikki yhteensä:"
     (fmt/euro-opt kaikki-yhteensa)]))

(defn yksikkohintaiset-tyot [urakka tiemerkinnan-toteumat-atom paallystysurakan-kohteet-atom vuosi]
  [:div.tiemerkinnan-yks-hint-tyot
   [valinnat/urakan-vuosi urakka]
   [toteutuneet-tiemerkinnat urakka tiemerkinnan-toteumat-atom @paallystysurakan-kohteet-atom vuosi]
   [yhteenveto @tiemerkinnan-toteumat-atom]
   [paallystysurakan-kohteet urakka @paallystysurakan-kohteet-atom]])
