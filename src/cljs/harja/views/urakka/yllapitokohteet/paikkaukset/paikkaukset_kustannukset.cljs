(ns harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-kustannukset
  (:require [tuck.core :as tuck]
            [reagent.core :as r]
            [harja.tiedot.urakka.paikkaukset-kustannukset :as tiedot]
            [harja.tiedot.urakka.paikkaukset-yhteinen :as yhteiset-tiedot]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.ui.debug :as debug]
            [harja.loki :refer [log]]
            [harja.ui.grid :as grid]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :as yleiset]
            [harja.domain.oikeudet :as oikeudet]
            [harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-yhteinen :as yhteinen-view]
            [taoensso.timbre :as log]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]))


(defn- nappaa-kohde [kohde-id]
  (first (filter
           #(= (:id %) kohde-id)
           (get-in @yhteiset-tiedot/tila [:valinnat :urakan-paikkauskohteet]))))

(defn- aseta-tr-kentat [rivi {:keys [tierekisteriosoite id]}]
  (let [{:keys [tie aosa aet losa let]} tierekisteriosoite]
    (assoc rivi :tie tie
                :aosa aosa
                :aet aet
                :losa losa
                :let let
                :paikkauskohde id)))

(defn paikkauksien-kokonaishinta-tyomenetelmittain [e! app]
  (fn [e! {:keys [paikkauksien-haku-kaynnissa? paikkauksien-haku-tulee-olemaan-kaynnissa? paikkauksien-kokonaishinta-tyomenetelmittain-grid]}]
    (let [urakka-id (get-in @yhteiset-tiedot/tila [:urakka :id])
          skeema [{:otsikko "Kohde" :leveys 10 :valinta-nayta :nimi :valinta-arvo :id
                   :tyyppi :valinta
                   :valinnat (get-in @yhteiset-tiedot/tila [:valinnat :urakan-paikkauskohteet])
                   :nimi :paikkauskohde :fmt :nimi
                   :aseta (fn [rivi arvo]
                            (aseta-tr-kentat rivi (nappaa-kohde arvo)))
                   :validoi [[:ei-tyhja "Valitse kohde"]]
                   :muokattava? #(if (pos-int? (:paikkaustoteuma-id %))
                                   false
                                   true)}
                  {:nimi :tie :otsikko "Tie" :tyyppi :positiivinen-numero :leveys 2}
                  {:nimi :aosa :otsikko "Aosa" :tyyppi :positiivinen-numero :leveys 2}
                  {:nimi :aet :otsikko "Aet" :tyyppi :positiivinen-numero :leveys 2}
                  {:nimi :losa :otsikko "Losa" :tyyppi :positiivinen-numero :leveys 2}
                  {:nimi :let :otsikko "Let" :tyyppi :positiivinen-numero :leveys 2}
                  {:otsikko "Työmenetelmä"
                   :leveys 5 :tyyppi :valinta
                   :validoi [[:ei-tyhja "Valitse työmenetelmä"]]
                   :valinnat (:urakan-tyomenetelmat @yhteiset-tiedot/tila)
                   :nimi :tyomenetelma}
                  {:otsikko "Valmistumispvm" :leveys 5 :tyyppi :pvm :fmt pvm/pvm-opt :nimi :valmistumispvm
                   :validoi [[:ei-tyhja "Syötä valmistumispvm"]]}
                  {:otsikko "Kokonaishinta" :fmt fmt/euro-opt :leveys 5 :tyyppi :numero :nimi :hinta
                   :validoi [[:ei-tyhja "Syötä hinta"]]}]
          yhteissumma (->> paikkauksien-kokonaishinta-tyomenetelmittain-grid
                           (map :hinta)
                           (reduce +))]
      [:div
       [grid/grid
        {:otsikko (if (or paikkauksien-haku-kaynnissa? paikkauksien-haku-tulee-olemaan-kaynnissa?)
                    [yleiset/ajax-loader-pieni "Päivitetään listaa.."]
                    "Paikkauksien kokonaishintaiset kustannukset työmenetelmittäin")
         :tunniste :paikkaustoteuma-id
         :tallenna (if (and
                         (oikeudet/voi-kirjoittaa? oikeudet/urakat-paikkaukset-kustannukset urakka-id)
                         (not (empty? (get-in @yhteiset-tiedot/tila [:valinnat :urakan-paikkauskohteet]))))
                     #(tiedot/tallenna-kustannukset %)
                     :ei-mahdollinen)
         :tallennus-ei-mahdollinen-tooltip (if-not (oikeudet/voi-kirjoittaa? oikeudet/urakat-paikkaukset-kustannukset urakka-id)
                                             "Ei kirjoitusoikeutta."
                                             (when (empty? (get-in @yhteiset-tiedot/tila [:valinnat :urakan-paikkauskohteet]))
                                               "Urakassa ei ole vielä paikkauskohteita, joille kustannuksia voisi kirjata."))
         :sivuta 50
         :tyhja "Ei kustannuksia"}
        skeema
        (reverse
          (sort-by :valmistumispvm paikkauksien-kokonaishinta-tyomenetelmittain-grid))]
       [yleiset/taulukkotietonakyma {:table-style {:float "right"}}
        "Yhteensä:"
        (fmt/euro-opt yhteissumma)]])))

(defn kustannukset* [e! app]
  (komp/luo
    (komp/ulos #(e! (tiedot/->NakymastaPois)))
    (fn [e! app]
      [:div
       [debug/debug app]
       [yhteinen-view/hakuehdot
        {:nakyma :kustannukset
         :palvelukutsu-onnistui-fn #(e! (tiedot/->KustannuksetHaettu %))}]
       [paikkauksien-kokonaishinta-tyomenetelmittain e! app]])))

(defn kustannukset [ur]
  (komp/luo
    (komp/sisaan #(yhteiset-tiedot/nakyman-urakka tila ur))
    (fn [_]
      [tuck/tuck tiedot/app kustannukset*])))
