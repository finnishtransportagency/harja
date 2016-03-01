(ns harja.tiedot.tierekisteri
  "Tierekisteri-UI-komponenttiin liittyvät asiat, joita ei voinut laittaa viewiin circular dependencyn takia"
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.ui.dom :as dom]
            [harja.ui.kartta.esitettavat-asiat :refer [maarittele-feature]])
  (:require-macros
    [reagent.ratom :refer [reaction run!]]
    [cljs.core.async.macros :refer [go]]))

(def karttataso-tr-alkuosoite (atom true))

(def valittu-alkupiste (atom nil))
(def tr-alkupiste-kartalla
  (reaction
   (when (and @karttataso-tr-alkuosoite @valittu-alkupiste)
     [{:alue (maarittele-feature @valittu-alkupiste
                                 false
                                 {:img    (dom/pinni-ikoni "musta")
                                  :zindex 21}    ;; Tarpeeksi korkeat etteivät vahingossakaan jää
                                 {:color  "gray" ;; muun alle
                                  :zindex 20})}])))

(tarkkaile! "TR-alkuosoite kartalla: " tr-alkupiste-kartalla)
