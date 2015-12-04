(ns harja.views.urakka.aikataulu
  "Ylläpidon urakoiden aikataulunäkymä"
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log logt]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.urakka.valitavoitteet :as vt]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as y]
            [harja.pvm :as pvm]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.fmt :as fmt]
            [cljs-time.core :as t]
            [harja.domain.roolit :as roolit]
            [cljs.core.async :refer [<!]])
  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))



(defn aikataulu
  "Urakan välitavoitteet näkymä. Ottaa parametrinä urakan ja hakee välitavoitteet sille."
  []
  (let [_ 2]
    (komp/luo
      (fn []
        [:div.aikataulu
         [grid/grid
          {:otsikko "Kohteiden aikataulu"}

          [{:otsikko "Kohde\u00ADnumero" :leveys "10%" :nimi :kohdenumero :tyyppi :string :pituus-max 128 :muokattava? (constantly false)}
           {:otsikko "TR-osoite" :leveys "10%" :nimi :tr-osoite :tyyppi :string :muokattava? (constantly false)}
           {:otsikko "KVL" :leveys "10%" :nimi :kvl :tyyppi :string :muokattava? (constantly false)}
           {:otsikko "Toimenpide" :leveys "10%" :nimi :toimenpide :tyyppi :string :muokattava? (constantly false)}
           {:otsikko "YP LK" :leveys "10%" :nimi :yp-lk :tyyppi :string :muokattava? (constantly false)}
           {:otsikko "Aloitus\u00ADaika" :leveys "10%" :nimi :alkuaika :tyyppi :string :muokattava? (constantly false)}
           {:otsikko "Pääll. valmis" :leveys "10%" :nimi :valmistumisaika :tyyppi :string :muokattava? (constantly false)}
           {:otsikko "TM aloitus\u00ADaika" :leveys "10%" :nimi :tm-alkuaika :tyyppi :string :muokattava? (constantly false)}
           {:otsikko "TM valmis" :leveys "10%" :nimi :tm-valmistumisaika :tyyppi :string :muokattava? (constantly false)}
           {:otsikko "Kohde valmis" :leveys "10%" :nimi :kohde-valmis :tyyppi :string :muokattava? (constantly false)}]

          [{:kohdenumero 1 :tr-osoite "4/1/100/4/534" :kvl 123 :toimenpide "MPKJ" :yp-lk "1"
            :alkuaika "10.6.2015" :valmistumisaika "5.7.2015" :tm-alkuaika "6.7.2015" :tm-valmistumisaika "14.7.2015" :kohde-valmis "Ei"}
           {:kohdenumero 2 :tr-osoite "4/4/534/6/134" :kvl 123 :toimenpide "MPKJ" :yp-lk "1"
            :alkuaika "11.6.2015" :valmistumisaika "6.7.2015" :tm-alkuaika "7.7.2015" :tm-valmistumisaika "14.7.2015" :kohde-valmis "Ei"}
           {:kohdenumero 3 :tr-osoite "4/6/134/7/1534" :kvl 123 :toimenpide "MPKJ" :yp-lk "1"
            :alkuaika "12.6.2015" :valmistumisaika "7.7.2015" :tm-alkuaika "8.7.2015" :tm-valmistumisaika "14.7.2015" :kohde-valmis "Ei"}

           ]]]))))