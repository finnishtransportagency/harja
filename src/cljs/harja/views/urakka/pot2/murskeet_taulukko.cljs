(ns harja.views.urakka.pot2.murskeet-taulukko
  "POT2 materiaalikirjaston mursketaulukko"
  (:require [clojure.string :as str]
            [cljs.core.async :refer [<! chan]]
            [reagent.core :refer [atom] :as r]
            [harja.ui.grid :as grid]
            [harja.ui.debug :refer [debug]]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko virheen-ohje] :as yleiset]
            [harja.domain.pot2 :as pot2-domain]
            [harja.tiedot.urakka.pot2.materiaalikirjasto :as mk-tiedot]
            [harja.views.urakka.pot2.massa-ja-murske-yhteiset :as mm-yhteiset]

            [harja.loki :refer [log logt tarkkaile!]]
            [harja.ui.komponentti :as komp])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

(defn murskeet-taulukko [e! {:keys [murskeet materiaalikoodistot] :as app}]
  [grid/grid
   {:otsikko "Murskeet"
    :tunniste ::pot2-domain/murske-id
    :tyhja (if (nil? murskeet)
             [ajax-loader "Haetaan urakan murskeita..."]
             "Urakalle ei ole vielä lisätty murskeita")
    :rivi-klikattu #(e! (mk-tiedot/->MuokkaaMursketta % false))
    :voi-lisata? false :voi-kumota? false
    :voi-poistaa? (constantly false) :voi-muokata? true
    :custom-toiminto {:teksti "Lisää murske"
                      :toiminto #(e! (mk-tiedot/->UusiMurske))
                      :opts {:ikoni (ikonit/livicon-plus)
                             :luokka "nappi-ensisijainen"}}}
   [{:otsikko "Nimi" :tyyppi :komponentti :leveys 8
     :komponentti (fn [rivi]
                    [mm-yhteiset/materiaalin-rikastettu-nimi {:tyypit (:mursketyypit materiaalikoodistot)
                                                              :materiaali rivi
                                                              :fmt :komponentti}])}
    {:otsikko "Tyyppi" :tyyppi :string :muokattava? (constantly false) :leveys 6
     :hae (fn [rivi]
            (or
              (::pot2-domain/tyyppi-tarkenne rivi)
              (pot2-domain/ainetyypin-koodi->nimi (:mursketyypit materiaalikoodistot)
                                                  (::pot2-domain/tyyppi rivi))))}
    {:otsikko "Esiintymä / Lähde" :nimi :esiintyma-tai-lahde :tyyppi :string :muokattava? (constantly false) :leveys 8
     :hae (fn [rivi]
            (or (::pot2-domain/esiintyma rivi)
                (::pot2-domain/lahde rivi)))}
    {:otsikko "Rakei\u00ADsuus" :nimi ::pot2-domain/rakeisuus :tyyppi :string :muokattava? (constantly false) :leveys 4}
    {:otsikko "Iskun\u00ADkestävyys" :nimi ::pot2-domain/iskunkestavyys :tyyppi :string :muokattava? (constantly false) :leveys 4}
    {:otsikko "" :nimi :toiminnot :tyyppi :komponentti :leveys 4
     :komponentti (fn [rivi]
                    [mm-yhteiset/materiaalirivin-toiminnot e! rivi])}]
   (sort-by (fn [murske]
              (pot2-domain/murskeen-rikastettu-nimi (:mursketyypit materiaalikoodistot)
                                                    murske))
            murskeet)])