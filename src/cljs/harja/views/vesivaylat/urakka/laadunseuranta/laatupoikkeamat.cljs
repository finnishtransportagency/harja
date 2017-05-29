(ns harja.views.vesivaylat.urakka.laadunseuranta.laatupoikkeamat
  (:require [reagent.core :refer [atom]]
            [tuck.core :refer [tuck]]
            [harja.ui.komponentti :as komp]
            [harja.ui.otsikkopaneeli :refer [otsikkopaneeli]]
            [harja.tiedot.vesivaylat.urakka.laadunseuranta.laatupoikkeamat :as tiedot]
            [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni]]
            [harja.ui.valinnat :as valinnat]
            [harja.domain.vesivaylat.vayla :as va]
            [harja.loki :refer [log]]
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
    [:div ] ;; TODO Tee väylätyypin ja väylän valinnasta yleinen valintakomponentti
    ;; TODO pitäisikö väylätyyppi / väylä olla yleinen valinta-atomi, kun sitä on nyt useassa näkymässä?
    ^{:key "ryhma3"}
    [:div]]

   ^{:key "urakkatoiminnot"}
   [valinnat/urakkatoiminnot {:sticky? true}
    ^{:key "siirtonappi"}
    [napit/uusi "Lisää uusi laatupoikkeama" #(log "Painoit nappia")]]])

(defn- paneelin-sisalto [e! laatupoikkeamat]
  [grid/grid
   {:tunniste :id ;; TODO namespaceta
    :ei-footer-muokkauspaneelia? true}
   [{:otsikko "Pvm ja aika" :nimi :pvm}
    {:otsikko "Turvalaite" :nimi :turvalaite}
    {:otsikko "Kuvaus" :nimi :kuvaus}
    {:otsikko "Tekijä" :nimi :tekija}
    {:otsikko "Päätös" :nimi :paatos}]
   laatupoikkeamat])

(defn- luo-otsikkorivit
  [e! laatupoikkeamat haku-kaynnissa?]
  (let [vaylat (keys (group-by :vayla laatupoikkeamat))] ;; TODO namespaceta
    (vec (mapcat
           (fn [vayla]
             [(::va/id vayla)

              [:span
               (grid/otsikkorivin-tiedot (::va/nimi vayla) 0) ; TODO Väylän rivien määrä tähän
               (when haku-kaynnissa? [:span " " [ajax-loader-pieni]])]

              [paneelin-sisalto
               e!
               laatupoikkeamat ;; TODO Suodata väylällä
               ]])
           vaylat))))

(defn- laatupoikkeamat-listaus [e! app]
  (into [otsikkopaneeli {}]
        (luo-otsikkorivit e! (:laatupoikkeamat app) (:haku-kaynnissa? app))))

(defn laatupoikkeamat* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(e! (tiedot/->Nakymassa? true))
                      #(e! (tiedot/->Nakymassa? false)))
    (fn [e! app]
      [:div

       [valinnat e! app]
       [laatupoikkeamat-listaus e! app]

       [:div {:style {:padding "10px"}}
        [:img {:src "images/harja_favicon.png"}]
        [:div {:style {:color "orange"}} "Työmaa"]]])))

(defn laatupoikkeamat []
  [tuck tiedot/tila laatupoikkeamat*])
