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
            [harja.domain.oikeudet :as oikeudet])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]))

(defn valitavoitteet-grid [tavoitteet]
  [grid/grid
   {:otsikko "Valtakunnalliset välitavoitteet"
    :tyhja (if (nil? tavoitteet)
             [y/ajax-loader "Välitavoitteita haetaan..."]
             "Ei valtakunnallisia välitavoitteita")
    :tallenna (when (oikeudet/voi-kirjoittaa? oikeudet/hallinta-valitavoitteet)
                #(go (log "[VALTVÄL] TODO Tallenna")))}
   [{:otsikko "Nimi" :leveys 70 :nimi :nimi :tyyppi :string :pituus-max 128}
    {:otsikko "Takaraja" :leveys 30 :nimi :takaraja :fmt pvm/pvm-opt :tyyppi :pvm}]
   tavoitteet])

(defn valitavoitteet []
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (fn []
      [valitavoitteet-grid @tiedot/tavoitteet])))
