(ns harja.views.kanavat.urakka.toimenpiteet
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :refer [tuck]]

            [harja.tiedot.kanavat.urakka.toimenpiteet.kokonaishintaiset :as tiedot]
            [harja.loki :refer [tarkkaile! log]]
            [harja.id :refer [id-olemassa?]]

            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni tietoja]]
            [harja.ui.debug :refer [debug]]

            [harja.domain.kanavat.kanavan-toimenpide :as kanavan-toimenpide]
            [harja.domain.kanavat.kanavan-kohde :as kanavan-kohde]
            [harja.domain.kanavat.kanavan-huoltokohde :as kanavan-huoltokohde]
            [harja.domain.toimenpidekoodi :as toimenpidekoodi]
            [harja.domain.kayttaja :as kayttaja]

            [harja.pvm :as pvm]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.ui.valinnat :as valinnat]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u])
  (:require-macros
    [cljs.core.async.macros :refer [go]]
    [harja.makrot :refer [defc fnc]]))

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
    :hae #(get-in % [::kanavan-toimenpide/huoltokohde ::kanavan-huoltokohde/nimi])}
   {:otsikko "Toimenpide"
    :nimi :huoltokohde
    :tyyppi :string
    :hae #(get-in % [::kanavan-toimenpide/toimenpidekoodi ::toimenpidekoodi/nimi])}
   {:otsikko "Suorittaja"
    :nimi :huoltokohde
    :tyyppi :string
    :hae #(henkilon-nimi (::kanavan-toimenpide/suorittaja %))}
   {:otsikko "Kuittaaja"
    :nimi :huoltokohde
    :tyyppi :string
    :hae #(henkilon-nimi (::kanavan-toimenpide/suorittaja %))}])