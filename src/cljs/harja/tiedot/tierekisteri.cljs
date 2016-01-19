(ns harja.tiedot.tierekisteri
  "Tierekisteri-UI-komponenttiin liittyvät asiat, joita ei voinut laittaa viewiin circular dependencyn takia"
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.ui.yleiset :as yleiset])
  (:require-macros
    [reagent.ratom :refer [reaction run!]]
    [cljs.core.async.macros :refer [go]]))

(def karttataso-tr-alkuosoite (atom true))

(def valittu-alkupiste (atom nil))
(def tr-alkupiste-kartalla (reaction
                             (when (and @karttataso-tr-alkuosoite @valittu-alkupiste)
                               [{:alue {:type        :tack-icon
                                        :coordinates (:coordinates @valittu-alkupiste)
                                        :zindex      6
                                        :img         (yleiset/karttakuva "tr-piste-tack-harmaa")}}])))

(tarkkaile! "TR-alkuosoite kartalla: " tr-alkupiste-kartalla)
