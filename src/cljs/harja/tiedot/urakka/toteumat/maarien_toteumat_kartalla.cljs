(ns harja.tiedot.urakka.toteumat.maarien-toteumat-kartalla
  "UI controlleri määrien toteutumille"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.kartta.esitettavat-asiat :refer [maarittele-feature kartalla-esitettavaan-muotoon]]
            [harja.ui.kartta.asioiden-ulkoasu :as asioiden-ulkoasu])

  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(def kartan-tila (atom {:valinnat nil
                        :valittu-toteuma nil
                        :toteumien-haku-kaynnissa? false
                        :nakymassa? false
                        :kayttajan-urakat [nil]}))

(defonce karttataso-toteumat (atom false))

(defonce karttataso-nakyvissa? (atom false))

(defonce toteumat-kartalla
         (reaction
           (when @karttataso-nakyvissa?
             ;; fixme: otetaan käyttöön kutsu kartalla-esitettavaan-muotoon eikä
             ;; suoraan määrittele-feature
             [{:alue (maarittele-feature @karttataso-toteumat
                                         false
                                         asioiden-ulkoasu/tr-ikoni
                                         asioiden-ulkoasu/tr-viiva)}])))
