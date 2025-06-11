(ns harja.views.hallinta.hairiot
  "Näkymästä voi lähettää kaikille käyttäjille sähköpostia. Hyödyllinen esimerkiksi päivityskatkoista tiedottamiseen."
  (:require [tuck.core :refer [tuck]]

            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :refer [ajax-loader-pieni]]
            [harja.ui.napit :as napit]
            [harja.ui.lomake :as lomake]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.urakka :as urakka-tiedot]
            [harja.tiedot.urakka.urakka :as urakka-tila]
            [harja.domain.hairioilmoitus :as hairio]
            [harja.tiedot.hallinta.hairiot :as tiedot])
  (:require-macros [harja.tyokalut.ui :refer [for*]]))


(defn- listaa-hairioilmoitus [hairio]
  (let [tuleva? (pvm/ennen? (pvm/nyt) (::hairio/alkuaika hairio))
        loppuva? (some? (::hairio/loppuaika hairio))
        loppunut? (or
                    (not (::hairio/voimassa? hairio))
                    (pvm/jalkeen? (pvm/nyt) (::hairio/loppuaika hairio)))
        
        voimassaolo-teksti (cond
                             loppunut?
                             nil
                             (and tuleva? loppuva?)
                             (str " (Alkaa " (pvm/pvm-aika (::hairio/alkuaika hairio))
                               ", loppuu " (pvm/pvm-aika (::hairio/loppuaika hairio)) ")")

                             tuleva?
                             (str " (Alkaa " (pvm/pvm-aika (::hairio/alkuaika hairio)) ")")

                             loppuva?
                             (str " (Loppuu " (pvm/pvm-aika (::hairio/loppuaika hairio)) ")")

                             :else
                             nil)]
    (str 
      (when (::hairio/pvm hairio) (fmt/pvm (::hairio/pvm hairio)))
      (when voimassaolo-teksti voimassaolo-teksti)
      " - "
      (hairio/tyyppi-fmt (::hairio/tyyppi hairio))
      " - "
      (::hairio/viesti hairio))))


(defn- vanhat-hairioilmoitukset [_e! {:keys [rivit] :as _app}]
  [:div
   [:h3 "Vanhat häiriöilmoitukset"]
   (if (empty? rivit)
     [:span "Ei vanhoja häiriöilmoituksia"]
     [:ul (for* [hairio rivit]
            [:li (listaa-hairioilmoitus hairio)])])])


(defn- aseta-hairioilmoitus [e! {:keys [tallennus-kaynnissa? tuore-hairioilmoitus] :as _app}]
  [:div
   [lomake/lomake
    {:muokkaa! #(e! (tiedot/->TuoreHairioilmoitus (lomake/ilman-lomaketietoja %))) 
     :footer [:<>
              [napit/tallenna "Aseta" #(e! (tiedot/->AsetaHairioilmoitus)) {:disabled tallennus-kaynnissa?}]
              [napit/peruuta #(e! (tiedot/->KumoaIlmoitus))]]}
    
    [{:otsikko "Viesti"
      :tyyppi :text
      :nimi :teksti
      :pituus-max 1024
      :palstoja 2
      :koko [80 5]}
     
     {:otsikko "Tyyppi"
      :tyyppi :valinta
      :nimi :tyyppi
      :valinnat [:hairio :tiedote]
      :valinta-nayta hairio/tyyppi-fmt}
     
     (lomake/rivi
       {:otsikko "Alkamisaika"
        :tyyppi :pvm-aika
        :nimi :alkuaika}
       {:otsikko "Päättymisaika"
        :tyyppi :pvm-aika
        :nimi :loppuaika})]
    tuore-hairioilmoitus]])


(defn- voimassaoleva-hairioilmoitus [e! {:keys [rivit asetetaan-hairioilmoitus? tallennus-kaynnissa?] :as app}]
  (let [voimassaoleva-hairio (hairio/voimassaoleva-hairio rivit)]
    [:div
     [:h3 "Nykyinen häiriöilmoitus"]
     (if asetetaan-hairioilmoitus?
       [aseta-hairioilmoitus e! app]
       [:div
        [:p (if voimassaoleva-hairio
              (listaa-hairioilmoitus voimassaoleva-hairio)
              (str
                "Ei voimassaolevaa häiriöilmoitusta. Kun asetat häiriöilmoituksen, "
                "se näytetään kaikille Harjan käyttäjille selaimen alapalkissa. "
                "Ilmoituksen yhteydessä näytetään aina ilmoituksen päivämäärä, joten sitä ei tarvitse kirjoittaa erikseen. "
                "Voit myös ajastaa häiriöilmoituksia etukäteen."))]

        (when voimassaoleva-hairio
          [napit/poista "Poista häiriöilmoitus" #(e! (tiedot/->PoistaHairio
                                                       (::hairio/id voimassaoleva-hairio)))
           {:disabled tallennus-kaynnissa?}])])]))


(defn- tulevat-hairioilmoitukset [e! {:keys [rivit haku-kaynnissa?] :as _app}]
  
  (let [tulevat-hairiot (vec (hairio/tulevat-hairiot rivit))
        tyypit (map (fn [[k _]] {:tyyppi k}) hairio/tyyppi-fmt)]
    
    [:div.tulevat-hairiot
     [:h3.otsikko "Tulevat häiriöilmoitukset"]
     (if (empty? tulevat-hairiot)
       [:span "Ei tulevia häiriöilmoituksia"]
       [:<>
        [grid/grid {:tyhja (if haku-kaynnissa?
                             [ajax-loader-pieni "Haku käynnissä..."]
                             "Ei löytynyt tuloksia.")
                    :sivuta 25
                    :tunniste ::hairio/id
                    :voi-kumota? false
                    :voi-lisata? false
                    :tallenna-vain-muokatut true
                    :mahdollista-rivin-valinta? false
                    :tallenna (fn [sisalto]
                                (let [sisalto (map (fn [rivi]
                                                     (update rivi ::hairio/tyyppi #(if (map? %) (:arvo %) %)))
                                                sisalto)]
                                  (tuck-apurit/e-kanavalla! e! tiedot/->TallennaMuokatut sisalto)))}

         [{:otsikko-komp (fn [_ _]
                           [:div.pvm "Alkuaika"
                            [:div [ikonit/action-sort-descending]]])

           :nimi ::hairio/alkuaika
           :fmt pvm/pvm-opt
           :tyyppi :pvm-aika
           :leveys 0.4
           :luokka "caption text-nowrap"
           :validoi [[:ei-tyhja "Valitse päivämäärä"]]}

          {:otsikko "Loppuaika"
           :nimi ::hairio/loppuaika
           :fmt pvm/pvm-opt
           :tyyppi :pvm-aika
           :leveys 0.4
           :luokka "caption text-nowrap"
           :validoi [[:ei-tyhja "Valitse päivämäärä"]]}

          {:otsikko "Viesti"
           :tyyppi :string
           :nimi ::hairio/viesti
           :leveys 0.3
           :validoi [[:ei-tyhja "Anna ilmoitukselle viesti"]]}

          {:otsikko "Tyyppi"
           :tyyppi :valinta
           :valinnat (map (fn [{:keys [tyyppi]}]
                            {:nimi (hairio/tyyppi-fmt tyyppi) :arvo tyyppi})
                       tyypit)
           :valinta-nayta #(hairio/tyyppi-fmt (keyword (if (map? %) (:arvo %) %)))
           :nimi ::hairio/tyyppi
           :leveys 0.3
           :validoi [[:ei-tyhja "Valitse ilmoituksen tyyppi"]]}]
         tulevat-hairiot]])]))


(defn hairiot* [e! _app]
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (komp/sisaan
      #(do
         (urakka-tiedot/valitse-kuluva-hk!)
         (e! (tiedot/->HaeTiedot))))
    
    (fn [e! app]
      [:div.hairioilmoitukset
       [voimassaoleva-hairioilmoitus e! app]
       [tulevat-hairioilmoitukset e! app]
       [napit/yleinen-ensisijainen "Aseta häiriöilmoitus" #(e! (tiedot/->AsetetaanHairioilmoitus))]
       [:hr]
       [vanhat-hairioilmoitukset e! app]])))


(defn hairiot []
  [tuck urakka-tila/hallinta-hairiot hairiot*])
