(ns harja.views.kanavat.urakka.toimenpiteet
  (:require [harja.ui.grid :as grid]
            [harja.pvm :as pvm]
            [clojure.string :as str]
            [harja.domain.kayttaja :as kayttaja]))

(defn toimenpidesarakkeet [e! app {:keys [rivi-valittu?-fn rivi-valittu-fn kaikki-valittu?-fn otsikko-valittu-fn]}]
  [{:otsikko "Päivä\u00ADmäärä"
    :nimi ::kanavan-toimenpide/pvm
    :tyyppi :pvm
    :fmt pvm/pvm-opt
    :leveys 5}
   {:otsikko "Kohde"
    :nimi :kohde
    :tyyppi :string
    :hae #(get-in % [::kanavan-toimenpide/kohde ::kanavan-kohde/nimi])
    :leveys 10}
   {:otsikko "Huolto\u00ADkohde"
    :nimi :huoltokohde
    :tyyppi :string
    :hae #(get-in % [::kanavan-toimenpide/huoltokohde ::kanavan-huoltokohde/nimi])
    :fmt str/lower-case
    :leveys 10}
   {:otsikko "Toimen\u00ADpide"
    :nimi :toimenpide
    :tyyppi :string
    :hae #(get-in % [::kanavan-toimenpide/toimenpidekoodi ::toimenpidekoodi/nimi])
    :leveys 15}
   {:otsikko "Lisä\u00ADtieto"
    :nimi ::kanavan-toimenpide/lisatieto
    :tyyppi :string
    :leveys 10}
   {:otsikko "Muu toimen\u00ADpide"
    :nimi ::kanavan-toimenpide/muu-toimenpide
    :tyyppi :string
    :leveys 10}
   {:otsikko "Suorit\u00ADtaja"
    :nimi :huoltokohde
    :tyyppi :string
    :hae #(kayttaja/kokonimi (::kanavan-toimenpide/suorittaja %))
    :leveys 10}
   {:otsikko "Kuit\u00ADtaaja"
    :nimi :huoltokohde
    :tyyppi :string
    :hae #(kayttaja/kokonimi (::kanavan-toimenpide/suorittaja %))
    :leveys 10}
   (grid/rivinvalintasarake
     {:otsikkovalinta? true
      :kaikki-valittu?-fn kaikki-valittu?-fn
      :otsikko-valittu-fn otsikko-valittu-fn
      :rivi-valittu?-fn rivi-valittu?-fn
      :rivi-valittu-fn rivi-valittu-fn
      :leveys 5})])

(defn toimenpiteiden-toiminto-suoritettu [toimenpiteiden-lkm toiminto]
  (str toimenpiteiden-lkm " "
       (if (= 1 toimenpiteiden-lkm) "toimenpide" "toimenpidettä")
       " " toiminto "."))