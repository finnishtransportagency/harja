(ns harja.views.vesivaylat.urakka.laadunseuranta.laatupoikkeamat
  (:require [reagent.core :refer [atom]]
            [tuck.core :refer [tuck]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.vesivaylat.urakka.laadunseuranta.tarkastukset :as tiedot]
            [harja.ui.valinnat :as valinnat]
            [harja.loki :refer [log]]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.ui.napit :as napit]))

(defn- valinnat [e! app]
  ;; TODO Tuleeko kaikkiin laadunseurannan näkymiin nämä samat? Jos tulee, käytä yhteistä komponenttia
  [valinnat/urakkavalinnat {}
   ^{:key "valintaryhmat"}
   [valinnat/valintaryhmat-3
    [urakka-valinnat/aikavali (get-in app [:valinnat :urakka-id])]

    ^{:key "ryhma2"}
    [:div [:p "TODO Tee väylätyypin ja väylän valinnasta yleinen valintakomponentti"]]
    ;; TODO pitäisikö väylätyyppi / väylä olla yleinen valinta-atomi, kun sitä on nyt useassa näkymässä?
    ^{:key "ryhma3"}
    [:div]]

   ^{:key "urakkatoiminnot"}
   [valinnat/urakkatoiminnot {:sticky? true}
    ^{:key "siirtonappi"}
    [napit/uusi "Lisää uusi laatupoikkeama" #(log "Painoit nappia")]]])

(defn laatupoikkeamat* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(e! (tiedot/->Nakymassa? true))
                      #(e! (tiedot/->Nakymassa? false)))
    (fn [e! app]
      [:div

       [valinnat e! app]

       [:div {:style {:padding "10px"}}
        [:img {:src "images/harja_favicon.png"}]
        [:div {:style {:color "orange"}} "Työmaa"]]])))

(defn laatupoikkeamat []
  [tuck tiedot/tila laatupoikkeamat*])
