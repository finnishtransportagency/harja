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
            [harja.domain.kayttaja :as kayttaja]
            [harja.domain.toimenpidekoodi :as toimenpidekoodi]
            [harja.domain.kayttaja :as kayttaja]

            [harja.pvm :as pvm]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.ui.valinnat :as valinnat]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [clojure.string :as str])
  (:require-macros
    [cljs.core.async.macros :refer [go]]
    [harja.makrot :refer [defc fnc]]))

(defn toimenpidesarakkeet [e! app {:keys [rivi-valittu?-fn rivi-valittu-fn kaikki-valittu?-fn otsikko-valittu-fn]}]
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
    :nimi :huoltokohde
    :tyyppi :string
    :hae #(kayttaja/kokonimi (::kanavan-toimenpide/suorittaja %))}
   {:otsikko "Kuittaaja"
    :nimi :huoltokohde
    :tyyppi :string
    :hae #(kayttaja/kokonimi (::kanavan-toimenpide/suorittaja %))}
   (grid/rivinvalintasarake
     {:otsikkovalinta? true
      :kaikki-valittu?-fn kaikki-valittu?-fn
      :otsikko-valittu-fn otsikko-valittu-fn
      :rivi-valittu?-fn rivi-valittu-fn
      :rivi-valittu-fn rivi-valittu?-fn
      :leveys 3})])