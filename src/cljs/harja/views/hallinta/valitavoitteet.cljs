(ns harja.views.hallinta.valitavoitteet
  "Valtakunnallisten välitavoitteiden näkymä"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<! >! timeout chan]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.hallinta.valitavoitteet :as tiedot]
            [harja.pvm :as pvm]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.grid :refer [grid]]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as y]
            [harja.domain.oikeudet :as oikeudet]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.navigaatio :as nav]
            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]))

(defn valitavoitteet-grid [tavoitteet-atom]
  [grid/grid
   {:otsikko "Valtakunnalliset välitavoitteet"
    :tyhja (if (nil? @tavoitteet-atom)
             [y/ajax-loader "Välitavoitteita haetaan..."]
             "Ei valtakunnallisia välitavoitteita")
    :tallenna (when (oikeudet/voi-kirjoittaa? oikeudet/hallinta-valitavoitteet)
                #(go (let [vastaus (<! (tiedot/tallenna-valitavoitteet %))]
                       (if (k/virhe? vastaus)
                         (viesti/nayta! "Välitavoitteiden tallentaminen epännistui"
                                        :warning viesti/viestin-nayttoaika-keskipitka)
                         (reset! tavoitteet-atom vastaus)))))}
   [{:otsikko "Nimi" :leveys 60 :nimi :nimi :tyyppi :string :pituus-max 128}
    {:otsikko "Urakkatyyppi" :leveys 20 :nimi :urakkatyyppi
     :tyyppi :valinta
     :validoi [[:ei-tyhja "Valitse materiaali"]]
     :valinta-arvo :arvo
     :valinta-nayta #(or (:nimi %) "- valitse -")
     :valinnat nav/+urakkatyypit+}
    {:otsikko "Takaraja" :leveys 20 :nimi :takaraja :fmt pvm/pvm-opt :tyyppi :pvm}]
   @tavoitteet-atom])

(defn valitavoitteet []
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (fn []
      [valitavoitteet-grid tiedot/valitavoitteet])))
