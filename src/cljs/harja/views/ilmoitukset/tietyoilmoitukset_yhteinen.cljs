(ns harja.views.ilmoitukset.tietyoilmoitukset-yhteinen
  (:require [harja.pvm :as pvm]
            [harja.domain.tietyoilmoitus :as t]
            [harja.domain.tietyoilmoituksen-email :as e]
            [harja.domain.kayttaja :as ka]
            [reagent.core :refer [atom] :as r]
            [harja.ui.grid :as grid]
            [harja.loki :refer [log]]))

(defn tietyoilmoituksen-lahetystiedot-komponentti
  [ilmoitus]
  [grid/muokkaus-grid
   {:otsikko ""
    :tyhja "Ei lähetetty"
    :voi-lisata? false
    :voi-muokata? false
    :voi-poistaa? false
    :voi-kumota? false
    :piilota-toiminnot? true
    :tunniste :lahetetty}
   [{:otsikko "Lähetetty" :nimi ::e/lahetetty
     :muokattava? (constantly false)
     :tyyppi :pvm-aika :fmt pvm/pvm-aika-opt :leveys 2}
    {:otsikko "Lähettäjä" :nimi :lahettaja :tyyppi :string :leveys 4
     :muokattava? (constantly false)
     :hae #(str (get-in % [::e/lahettaja ::ka/etunimi])
                " "
                (get-in % [::e/lahettaja ::ka/sukunimi]))}

    {:otsikko "Kuitattu" :nimi ::e/kuitattu :tyyppi :pvm-aika :leveys 2
     :muokattava? (constantly false)
     :fmt pvm/pvm-aika-opt}]
   (atom
     (into {}
           (map-indexed (fn [i lahetys]
                          [i lahetys]))
           (sort-by ::e/lahetetty > (::t/email-lahetykset ilmoitus))))])