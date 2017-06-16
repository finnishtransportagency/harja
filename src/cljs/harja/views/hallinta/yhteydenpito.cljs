(ns harja.views.hallinta.yhteydenpito
  "Näkymästä voi lähettää kaikille käyttäjille sähköpostia. Hyödyllinen esimerkiksi päivityskatkoista tiedottamiseen."
  (:require [reagent.core :refer [atom] :as r]
            [harja.tiedot.hallinta.yhteydenpito :as tiedot]
            [cljs.core.async :refer [<! >! timeout chan]]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.ui.komponentti :as komp]))

(defn yhteydenpito* [vastaanottajat]
  [:div.yhteydenpito
   [:h3 "Sähköpostin lähettäminen Harja-käyttäjille"]
   [:p
    "Klikkaa "
    [:a {:href (tiedot/mailto-bcc-linkki vastaanottajat)}
     "tästä"]
    " lähettääksesi viestin kaikille Harjan käyttäjille."]])

(defn yhteydenpito []
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (fn []
      (if @tiedot/vastaanottajat
        [yhteydenpito* @tiedot/vastaanottajat]
        [ajax-loader "Ladataan..."]))))
