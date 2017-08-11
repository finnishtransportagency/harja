(ns harja.views.hallinta.hairiot
  "Näkymästä voi lähettää kaikille käyttäjille sähköpostia. Hyödyllinen esimerkiksi päivityskatkoista tiedottamiseen."
  (:require [reagent.core :refer [atom] :as r]
            [harja.tiedot.hallinta.hairiot :as tiedot]
            [cljs.core.async :refer [<! >! timeout chan]]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.ui.komponentti :as komp]
            [harja.domain.hairioilmoitus :as hairio]
            [harja.fmt :as fmt])
  (:require-macros [harja.tyokalut.ui :refer [for*]]))

(defn- listaa-hairioilmoitus [hairio]
  (str (fmt/pvm (::hairio/pvm hairio))
       " - "
       (::hairio/viesti hairio)))

(defn hairiot []
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (komp/sisaan tiedot/hae-hairiot)
    (fn []
      (let [hairiot @tiedot/hairiot
            tuorein-hairio (hairio/tuorein-voimassaoleva-hairio hairiot)]
        [:div
         [:h3 "Nykyinen häiriöilmoitus"]
         [:p (cond (nil? hairiot)
                   [ajax-loader "Haetaan..."]

                   (some? tuorein-hairio)
                   (listaa-hairioilmoitus tuorein-hairio)

                   :default
                   "Ei voimassaolevaa häiriöilmoitusta")]

         [:h3 "Vanhat häiriöt"]
         (if (empty? hairiot)
           "Ei vanhoja häiriöilmoituksia"
           [:ul
            (for* [hairio hairiot]
              (when (not= hairio tuorein-hairio)
                [:li (listaa-hairioilmoitus hairio)]))])]))))
