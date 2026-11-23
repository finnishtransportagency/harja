(ns harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.toiminnot
  (:require [reagent.core :as r]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :as yleiset]
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

(defn- ikoni-ja-teksti [komponentti]
  [kehys/kortti komponentti
   [:p "Ikoni ja teksti vierekkäin. Ikoni on ensimmäinen argumentti, teksti toinen."]
   [:div {:data-cy (kehys/ankkuri komponentti "-juuri")}
    [ikonit/ikoni-ja-teksti [ikonit/livicon-info-sign] "Esimerkkiteksti"]]])

(defn- linkki [komponentti]
  (r/with-let [klikkaukset (r/atom 0)]
    (let [ankkuri (kehys/ankkuri komponentti)]
      [kehys/kortti komponentti
       [:p "Klikattava teksti. Käyttöpaikka antaa toiminnon, disabloitu linkki ei ole klikattava."]
       [:div {:data-cy (str ankkuri "-juuri")}
        [yleiset/linkki "Klikkaa minua" #(swap! klikkaukset inc) {:luokka "linkki" :block? true}]
        [yleiset/linkki "Disabloitu" #(swap! klikkaukset inc) {:disabloitu? true :block? true}]]
       [:p {:data-cy (str ankkuri "-arvo")}
        (str "Klikkauksia: " @klikkaukset)]])))

(register! :ikoni-ja-teksti {:esikatselu ikoni-ja-teksti})
(register! :linkki {:esikatselu linkki})
