(ns harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.toiminnot
  (:require [reagent.core :as r]
            [harja.ui.napit :as napit]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.kehys :as kehys]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.rekisterointi :refer [register!]]))

(defn- nappi [komponentti]
  (r/with-let [suoritettu? (r/atom false)]
    [kehys/kortti komponentti
     [:p "Teksti, callback ja käyttöpaikan optiot kuten :data-cy, :disabled ja :luokka."]
     [napit/nappi "Esimerkkitoiminto" #(reset! suoritettu? true)
      {:data-cy (kehys/ankkuri komponentti "-painike")
       :luokka "nappi-toissijainen"}]
     [:p {:data-cy (kehys/ankkuri komponentti "-tila")}
      (if @suoritettu? "Toiminto suoritettu" "Toimintoa ei ole suoritettu")]]))

(defn- nappi-simulaattorissa [solmu tila _lapsi-fn]
  [napit/nappi
   (get-in solmu [:asetukset :teksti])
   (fn [] (reset! (:tila tila) :onnistui))
   {:data-cy "ui-simulaattori-tallenna"}])

(register! :nappi {:esikatselu nappi
                   :renderoi nappi-simulaattorissa})
