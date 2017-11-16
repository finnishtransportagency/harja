(ns harja.views.kanavat.urakka.toimenpiteet
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :refer [tuck]]

            [harja.loki :refer [tarkkaile! log]]
            [harja.id :refer [id-olemassa?]]

            [harja.ui.kentat :refer [tee-kentta]]
            [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni tietoja]]
            [harja.ui.debug :refer [debug]]

            [harja.domain.kanavat.kanavan-toimenpide :as kanavan-toimenpide]
            [harja.domain.kanavat.kanavan-kohde :as kanavan-kohde]
            [harja.domain.kanavat.kanavan-huoltokohde :as kanavan-huoltokohde]
            [harja.domain.kayttaja :as kayttaja]
            [harja.domain.toimenpidekoodi :as toimenpidekoodi]
            [harja.domain.kayttaja :as kayttaja]

            [harja.pvm :as pvm]
            [clojure.string :as str]
            [harja.tiedot.urakka.urakan-toimenpiteet :as urakan-toimenpiteet]
            [harja.tiedot.kanavat.urakka.toimenpiteet :as kanavatoimenpidetiedot])
  (:require-macros
    [cljs.core.async.macros :refer [go]]
    [harja.makrot :refer [defc fnc]]))

(defn valittu-tehtava [toimenpide]
  (or (::kanavan-toimenpide/toimenpidekoodi-id toimenpide)
      (get-in toimenpide [::kanavan-toimenpide/toimenpidekoodi ::toimenpidekoodi/id])))

(def toimenpidesarakkeet
  [{:otsikko "Päivämäärä"
    :nimi ::kanavan-toimenpide/pvm
    :tyyppi :pvm
    :fmt pvm/pvm-opt}
   {:otsikko "Kohde"
    :nimi :kohde
    :tyyppi :string
    :hae #(get-in % [::kanavan-toimenpide/kohde ::kanavan-kohde/nimi])}
   {:otsikko "Huoltokohde"
    :nimi :huoltokohde
    :tyyppi :string
    :hae #(get-in % [::kanavan-toimenpide/huoltokohde ::kanavan-huoltokohde/nimi])
    :fmt str/lower-case}
   {:otsikko "Toimenpide"
    :nimi :toimenpide
    :tyyppi :string
    :hae #(get-in % [::kanavan-toimenpide/toimenpidekoodi ::toimenpidekoodi/nimi])}
   {:otsikko "Lisätieto"
    :nimi ::kanavan-toimenpide/lisatieto
    :tyyppi :string}
   {:otsikko "Muu toimenpide"
    :nimi ::kanavan-toimenpide/muu-toimenpide
    :tyyppi :string}
   {:otsikko "Suorittaja"
    :nimi ::kanavan-toimenpide/suorittaja
    :tyyppi :string}
   {:otsikko "Kuittaaja"
    :nimi ::kanavan-toimenpide/kuittaaja
    :tyyppi :string
    :hae #(kayttaja/kokonimi (::kanavan-toimenpide/kuittaaja %))}])

(defn toimenpidelomakkeen-kentat [toimenpide sopimukset kohteet huoltokohteet toimenpideinstanssit tehtavat]
  (let [tehtava (valittu-tehtava toimenpide)]
    [{:otsikko "Sopimus"
      :nimi ::kanavan-toimenpide/sopimus-id
      :tyyppi :valinta
      :valinta-arvo first
      :valinta-nayta second
      :valinnat sopimukset
      :pakollinen? true}
     {:otsikko "Päivämäärä"
      :nimi ::kanavan-toimenpide/pvm
      :tyyppi :pvm
      :fmt pvm/pvm-opt
      :pakollinen? true}
     {:otsikko "Kohde"
      :nimi ::kanavan-toimenpide/kohde
      :tyyppi :valinta
      :valinta-nayta #(or (::kanavan-kohde/nimi %) "- Valitse kohde -")
      :valinnat kohteet}
     {:otsikko "Huoltokohde"
      :nimi ::kanavan-toimenpide/huoltokohde
      :tyyppi :valinta
      :valinta-nayta #(or (::kanavan-huoltokohde/nimi %) "- Valitse huoltokohde-")
      :valinnat huoltokohteet
      :pakollinen? true}
     {:otsikko "Toimenpide"
      :nimi ::kanavan-toimenpide/toimenpideinstanssi-id
      :pakollinen? true
      :tyyppi :valinta
      :uusi-rivi? true
      :valinnat toimenpideinstanssit
      :fmt #(:tpi_nimi (urakan-toimenpiteet/toimenpideinstanssi-idlla % toimenpideinstanssit))
      :valinta-arvo :tpi_id
      :valinta-nayta #(if % (:tpi_nimi %) "- Valitse toimenpide -")
      :aseta (fn [rivi arvo]
               (-> rivi
                   (assoc ::kanavan-toimenpide/toimenpideinstanssi-id arvo)
                   (assoc-in [:tehtava :toimenpideinstanssi :id] arvo)
                   (assoc-in [:tehtava :toimenpidekoodi :id] nil)
                   (assoc-in [:tehtava :yksikko] nil)))}
     {:otsikko "Tehtävä"
      :nimi ::kanavan-toimenpide/toimenpidekoodi-id
      :pakollinen? true
      :tyyppi :valinta
      :valinnat tehtavat
      :valinta-arvo :id
      :valinta-nayta #(or (:nimi %) "- Valitse tehtävä -")
      :hae #(valittu-tehtava %)
      :aseta (fn [rivi arvo]
               (-> rivi
                   (assoc ::kanavan-toimenpide/toimenpidekoodi-id arvo)
                   (assoc-in [:tehtava :tpk-id] arvo)
                   (assoc-in [:tehtava :yksikko] (:yksikko (urakan-toimenpiteet/tehtava-idlla arvo tehtavat)))))}
     (when (kanavatoimenpidetiedot/valittu-tehtava-muu? tehtava tehtavat)
       {:otsikko "Muu toimenpide"
        :nimi ::kanavan-toimenpide/muu-toimenpide
        :tyyppi :string})
     {:otsikko "Lisätieto"
      :nimi ::kanavan-toimenpide/lisatieto
      :tyyppi :string}
     {:otsikko "Suorittaja"
      :nimi ::kanavan-toimenpide/suorittaja
      :tyyppi :string
      :pakollinen? true}
     {:otsikko "Kuittaaja"
      :nimi ::kanavan-toimenpide/kuittaaja
      :tyyppi :string
      :hae #(kayttaja/kokonimi (::kanavan-toimenpide/kuittaaja %))
      :muokattava? (constantly false)}]))