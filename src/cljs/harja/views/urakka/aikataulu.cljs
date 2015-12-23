(ns harja.views.urakka.aikataulu
  "Ylläpidon urakoiden aikataulunäkymä"
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log logt]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.urakka.valitavoitteet :as vt]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as y]
            [harja.tiedot.urakka.paallystys :as paallystys]
            [harja.pvm :as pvm]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.fmt :as fmt]
            [cljs-time.core :as t]
            [harja.domain.roolit :as roolit]
            [cljs.core.async :refer [<!]])
  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(defn aikataulu
  []
  (let [_ 2]
    (komp/luo
      (fn []
        [:div.aikataulu
         [grid/grid
          {:otsikko "Kohteiden aikataulu"}

          [{:otsikko "Kohde\u00AD ID" :leveys "5%" :nimi :kohdenumero :tyyppi :string :pituus-max 128 :muokattava? (constantly false)}
           {:otsikko "Kohteen nimi" :leveys "10%" :nimi :kohdenimi :tyyppi :string :pituus-max 128 :muokattava? (constantly false)}

           {:otsikko "TR-osoite" :leveys "10%" :nimi :tr-osoite :tyyppi :string :muokattava? (constantly false)}
           {:otsikko "Pääll. aloitus\u00AD" :leveys "8%" :nimi :paall-aloitusaika :tyyppi :string :muokattava? (constantly false)}
           {:otsikko "Pääll. valmis" :leveys "8%" :nimi :paall-valmistumisaika :tyyppi :string :muokattava? (constantly false)}
           {:otsikko "TM valmis" :leveys "8%" :nimi :tm-valmistumisaika :tyyppi :string :muokattava? (constantly false)}
           {:otsikko "Valmis tie\u00ADmerkin\u00ADtään" :leveys "7%" :nimi :valmis-tiemerkintaan :tyyppi :string :muokattava? (constantly false)}
           {:otsikko "Kohde valmis" :leveys "5%" :nimi :kohde-valmis :tyyppi :string :muokattava? (constantly false)}
           {:otsikko     "Toiminnot" :nimi :toiminnot :tyyppi :komponentti
            :komponentti (fn [rivi]
                           (let [valmis-tiemerkintaan? (= "Kyllä" (:valmis-tiemerkintaan rivi))]
                             [:button.nappi-ensisijainen.nappi-grid
                              {:class    (str "nappi-ensisijainen " (if valmis-tiemerkintaan? "disabled"))
                               :type     "button"
                               :on-click #(log "Painettu")} "Valmis tiemerkintään"]))
            :leveys      "14%"}]
          @paallystys/paallystyskohderivit
          #_[{:kohdenumero       1 :kohdenimi "Mt 4 Ylä-Laakkola":tr-osoite "4/1/100/4/534" :kvl 123 :toimenpide "MPKJ" :yp-lk "1"
            :paall-aloitusaika "10.6.2015" :paall-valmistumisaika "5.7.2015" :tm-alkuaika "6.7.2015" :tm-valmistumisaika "14.7.2015"
            :valmis-tiemerkintaan "Kyllä" :kohde-valmis "Ei"}
           {:kohdenumero       2 :kohdenimi "Mt 4 Ala-Laakkola":tr-osoite "4/4/534/6/134" :kvl 123 :toimenpide "MPKJ" :yp-lk "1"
            :paall-aloitusaika "11.6.2015" :paall-valmistumisaika "6.7.2015" :tm-alkuaika "7.7.2015" :tm-valmistumisaika "14.7.2015"
            :valmis-tiemerkintaan "Ei" :kohde-valmis "Ei"}
           {:kohdenumero       3 :kohdenimi "Mt 4 Päkylä":tr-osoite "4/6/134/7/1534" :kvl 123 :toimenpide "MPKJ" :yp-lk "1"
            :paall-aloitusaika "12.6.2015" :paall-valmistumisaika "7.7.2015" :tm-alkuaika "8.7.2015" :tm-valmistumisaika "14.7.2015"
            :valmis-tiemerkintaan "Ei" :kohde-valmis "Ei"}

           ]]]))))