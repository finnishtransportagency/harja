(ns harja.tiedot.tierekisteri
  "Tierekisteri-UI-komponenttiin liittyvät asiat, joita ei voinut laittaa viewiin circular dependencyn takia"
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.ui.kartta.ikonit :as kartta-ikonit]
            [harja.ui.kartta.esitettavat-asiat :refer [maarittele-feature]]
            [harja.ui.kartta.varit.puhtaat :as puhtaat]
            [harja.ui.kartta.asioiden-ulkoasu :as asioiden-ulkoasu])
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
                                 asioiden-ulkoasu/tr-ikoni
                                 asioiden-ulkoasu/tr-viiva)}])))
