(ns harja.views.urakka.pot2.murskeet
  "POT2 murskeiden hallintanäkymä"
  (:require [clojure.string :as str]
            [cljs.core.async :refer [<! chan]]
            [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]

            [harja.ui.grid :as grid]
            [harja.ui.debug :refer [debug]]
            [harja.ui.dom :as dom]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :as kentat]
            [harja.ui.komponentti :as komp]
            [harja.ui.lomake :as ui-lomake]
            [harja.ui.modal :as modal]
            [harja.ui.napit :as napit]
            [harja.ui.validointi :as v]
            [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko virheen-ohje] :as yleiset]
            [harja.domain.paallystysilmoitus :as paallystysilmoitus-domain]
            [harja.domain.pot2 :as pot2-domain]
            [harja.tiedot.urakka.pot2.validoinnit :as pot2-validoinnit]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.pot2.materiaalikirjasto :as mk-tiedot]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.views.urakka.pot2.massat :as massat-view]
            [harja.loki :refer [log logt tarkkaile!]])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))



(defn murskeet-taulukko [e! {:keys [murskeet materiaalikoodistot] :as app}]
  [grid/grid
   {:otsikko "Murskeet"
    :tunniste :pot2-murske/id
    :tyhja (if (nil? murskeet)
             [ajax-loader "Haetaan urakan murskeita..."]
             "Urakalle ei ole vielä lisätty murskeita")
    :rivi-klikattu #(e! (mk-tiedot/->MuokkaaMursketta % false))
    :voi-lisata? false :voi-kumota? false
    :voi-poistaa? (constantly false) :voi-muokata? true
    :custom-toiminto {:teksti "Luo uusi murske"
                      :toiminto #(e! (mk-tiedot/->UusiMurske true))
                      :opts {:ikoni (ikonit/livicon-plus)
                             :luokka "napiton-nappi"}}}
   [{:otsikko "Nimi" :tyyppi :string
     :hae (fn [rivi]
            (pot2-domain/murskeen-rikastettu-nimi (:mursketyypit materiaalikoodistot) rivi))
     :solun-luokka (constantly "bold") :leveys 8}
    {:nimi ::pot2-domain/mursketyyppi :tyyppi :string :muokattava? (constantly false)}

    {:otsikko "Toiminnot" :nimi :toiminnot :tyyppi :komponentti :leveys 3
     :komponentti (fn [rivi]
                    [massat-view/massan-toiminnot e! rivi])}]
   murskeet])