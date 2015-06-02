(ns harja.views.urakka.laadunseuranta.tarkastukset
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<! >! chan]]

            [harja.ui.komponentti :as komp]
            [harja.tiedot.urakka :as urakka]
            [harja.pvm :as pvm])
  (:require-macros [reagent.ratom :refer [reaction]]))

(defonce tarkastustyyppi (atom nil)) ;; nil = kaikki, :tiesto, :talvihoito, :soratie

(defonce aikavali
  ;; Alustetaan aikaväli valitun hoitokauden ensimmäiseen kuukauteen
  (reaction (some-> @urakka/valittu-hoitokausi
                    first
                    pvm/kuukauden-aikavali)))
  

(defn tarkastukset
  "Tarkastuksien pääkomponentti"
  []
  (komp/luo

   (fn []
     [:div "tässä on tarkastukset"])))
