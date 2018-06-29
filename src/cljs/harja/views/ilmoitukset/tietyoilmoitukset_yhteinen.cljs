(ns harja.views.ilmoitukset.tietyoilmoitukset-yhteinen
  (:require [harja.pvm :as pvm]
            [harja.domain.tietyoilmoitus :as t]
            [harja.domain.tietyoilmoituksen-email :as e]
            [harja.domain.kayttaja :as ka]
            [reagent.core :refer [atom] :as r]
            [harja.ui.grid :as grid]
            [harja.loki :refer [log]]))

(defn kuittauksen-tila [{lahetetty ::e/lahetetty kuitattu ::e/kuitattu virhe ::e/lahetysvirhe :as foo}]
  (cond
    virhe [:span.tila-virhe (str "Epäonnistunut: " (pvm/pvm-aika (or kuitattu lahetetty)))]
    kuitattu [:span.tila-lahetetty (str "Onnistunut: " (pvm/pvm-aika kuitattu))]
    lahetetty [:span.tila-odottaa-vastausta "Odottaa vastausta..."]
    :else ""))

(defn tietyoilmoituksen-lahetystiedot-komponentti
  [ilmoitus]
  [grid/grid
   {:muokkauspaneeli? false
    :tyhja "Ei lähetetty"
    :voi-lisata? false
    :voi-muokata? false
    :voi-poistaa? false
    :voi-kumota? false
    :piilota-toiminnot? true
    :tunniste ::e/id}
   [{:otsikko "Lähetetty" :nimi ::e/lahetetty
     :muokattava? (constantly false)
     :tyyppi :pvm-aika :fmt pvm/pvm-aika-opt :leveys 2}
    {:otsikko "Lähettäjä" :nimi :lahettaja :tyyppi :string :leveys 4
     :muokattava? (constantly false)
     :hae #(str (get-in % [::e/lahettaja ::ka/etunimi])
                " "
                (get-in % [::e/lahettaja ::ka/sukunimi]))}

    {:otsikko "Kuitattu" :tyyppi :komponentti :leveys 2 :hae identity
     :komponentti (fn [rivi _]
                    [kuittauksen-tila rivi])
     :muokattava? (constantly false)}]
   (sort-by ::e/lahetetty > (::t/email-lahetykset ilmoitus))])