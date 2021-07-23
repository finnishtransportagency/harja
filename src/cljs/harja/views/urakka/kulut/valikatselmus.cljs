(ns harja.views.urakka.kulut.valikatselmus
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [harja.loki :refer [log logt]]
            [harja.ui.napit :as napit]
            [harja.ui.debug :as debug]
            [harja.tiedot.urakka.kulut.mhu-kustannusten-seuranta :as kustannusten-seuranta-tiedot]
            [harja.tiedot.navigaatio :as nav]
            [harja.pvm :as pvm]))

(defn valikatselmus-otsikko-ja-tiedot [app]
  (let [urakan-nimi (:nimi @nav/valittu-urakka)
         valittu-hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi app)
         urakan-alkuvuosi (pvm/vuosi (:alkupvm @nav/valittu-urakka))
         hoitokausi-str (pvm/paivamaaran-hoitokausi-str (pvm/hoitokauden-alkupvm urakan-alkuvuosi))]
    [:h1 "Välikatselmuksen päätökset"]
    [:div.caption urakan-nimi]
    [:div.caption (str (- valittu-hoitokauden-alkuvuosi urakan-alkuvuosi) ". hoitovuosi (" hoitokausi-str ")")]))

(defn valikatselmus [e! app]
  (let [toteumat-yhteensa (get-in app [:kustannukset-yhteensa :yht-toteutunut-summa])]
    [:div.valikatselmus-container
     [napit/takaisin "Takaisin" #(e! (kustannusten-seuranta-tiedot/->SuljeValikatselmusLomake)) {:luokka "napiton-nappi tumma"}]
     [valikatselmus-otsikko-ja-tiedot app]
     [debug/debug app]]))
