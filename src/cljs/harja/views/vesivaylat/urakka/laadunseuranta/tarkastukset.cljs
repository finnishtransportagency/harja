(ns harja.views.vesivaylat.urakka.laadunseuranta.tarkastukset
  (:require [reagent.core :refer [atom]]
            [tuck.core :refer [tuck]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.vesivaylat.urakka.laadunseuranta.tarkastukset :as tiedot]
            [harja.ui.valinnat :as valinnat]
            [harja.loki :refer [log]]
            [harja.ui.otsikkopaneeli :refer [otsikkopaneeli]]
            [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni]]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.ui.napit :as napit]
            [harja.ui.grid :as grid]))

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
    [napit/uusi "Lisää uusi tarkastus" #(log "Painoit nappia")]]])


(defn- paneelin-sisalto [e! laatupoikkeamat]
  [grid/grid
   {:tunniste :id ;; TODO namespaceta
    :ei-footer-muokkauspaneelia? true}
   [{:otsikko "Pvm ja aika" :nimi :pvm}
    {:otsikko "Tyyppi" :nimi :tyyppi}
    {:otsikko "Turvalaite" :nimi :turvalaite}
    {:otsikko "Havainnot" :nimi :havainnot}]
   laatupoikkeamat])

(defn- luo-otsikkorivit
  [e! laatupoikkeamat haku-kaynnissa?]
  (let [vaylat (keys (group-by :vayla laatupoikkeamat))] ;; TODO namespaceta
    (vec (mapcat
           (fn [vayla]
             [vayla
              [:span
               (grid/otsikkorivin-tiedot "Väylä" 0) ; TODO Väylän nimi ja rivien määrä tähän
               (when haku-kaynnissa? [:span " " [ajax-loader-pieni]])]
              [paneelin-sisalto
               e!
               laatupoikkeamat ;; TODO Suodata väylällä
               ]])
           vaylat))))

(defn- tarkastukset-listaus [e! app]
  (into [otsikkopaneeli {}]
        (luo-otsikkorivit e! (:laatupoikkeamat app) (:haku-kaynnissa? app))))

(defn tarkastukset* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(e! (tiedot/->Nakymassa? true))
                      #(e! (tiedot/->Nakymassa? false)))
    (fn [e! app]
      [:div

       [valinnat e! app]
       [tarkastukset-listaus e! app]

       [:div {:style {:padding "10px"}}
        [:img {:src "images/harja_favicon.png"}]
        [:div {:style {:color "orange"}} "Työmaa"]]])))

(defn tarkastukset []
  [tuck tiedot/tila tarkastukset*])
