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

            [harja.tiedot.urakka :as urakka-tiedot]
            [harja.tiedot.urakka.urakka :as urakka-tila]
            [harja.domain.hairioilmoitus :as hairio]
            [harja.tiedot.hallinta.hairiot :as tiedot]))


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
    (str (when 
           
           (fmt/pvm (::hairio/pvm hairio)))
      (when voimassaolo-teksti
        voimassaolo-teksti)
      " - "
      (hairio/tyyppi-fmt (::hairio/tyyppi hairio))
      " - "
      (::hairio/viesti hairio))))

#_ (defn- vanhat-hairioilmoitukset [hairiot]
  [:div
   [:h3 "Vanhat häiriöilmoitukset"]
   (if (empty? hairiot)
     "Ei vanhoja häiriöilmoituksia"
     [:ul
      (for* [hairio hairiot]
          [:li (listaa-hairioilmoitus hairio)])])])

(defn- aseta-hairioilmoitus [e! {:keys [tallennus-kaynnissa? asetetaan-hairioilmoitus? tuore-hairioilmoitus] :as app}]
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
  
  (let [voimassaoleva-hairio (hairio/voimassaoleva-hairio rivit)
        
        _ (println "\n voimassa oleva: " voimassaoleva-hairio)
        ]

  [:div
     [:h3 "Nykyinen häiriöilmoitus"]
     (if asetetaan-hairioilmoitus?
       [aseta-hairioilmoitus e! app]
       [:div
        [:p (if voimassaoleva-hairio
              (listaa-hairioilmoitus voimassaoleva-hairio)
              "Ei voimassaolevaa häiriöilmoitusta. Kun asetat häiriöilmoituksen, se näytetään kaikille Harjan käyttäjille selaimen alapalkissa. Ilmoituksen yhteydessä näytetään aina ilmoituksen päivämäärä, joten sitä ei tarvitse kirjoittaa erikseen. Voit myös ajastaa häiriöilmoituksia etukäteen.")]

        (when asetetaan-hairioilmoitus?
          [napit/poista "Poista häiriöilmoitus" #(tiedot/poista-hairioilmoitus {:id (::hairio/id asetetaan-hairioilmoitus?)})
           {:disabled tallennus-kaynnissa?}])])]))

(defn- tulevat-hairioilmoitukset [e! {:keys [rivit haku-kaynnissa?] :as app}]
  (let [tulevat-hairiot (vec (hairio/tulevat-hairiot rivit))
        
        _ (println "\n t: " tulevat-hairiot)
        ]
    [:div
     [:h3 "Tulevat häiriöilmoitukset"]
     (if (empty? tulevat-hairiot)
       [:span "Ei tulevia häiriöilmoituksia"]
       [:<> 
        [grid/grid {:tyhja (if haku-kaynnissa?
                             [ajax-loader-pieni "Haku käynnissä..."]
                             "Ei löytynyt tuloksia.")
                    :tunniste ::hairio/id
                    :voi-kumota? false
                    :piilota-toiminnot? true
                    :tallenna-vain-muokatut true
                    :mahdollista-rivin-valinta? false
                    :sivuta 25

                    :tallenna (fn [sisalto]
                                #_ ())
                    }
        
         [{:otsikko-komp (fn [_ _]
                           [:div.pvm "Alkuaika"
                            [:div [ikonit/action-sort-descending]]])
           
           :nimi ::hairio/alkuaika
           :fmt pvm/pvm-opt 
           :tyyppi :pvm
           
           :luokka "caption text-nowrap"
           :leveys 0.2}
          
          {:otsikko "Loppuaika"
           :nimi ::hairio/loppuaika
           :fmt pvm/pvm-opt
           :tyyppi :pvm 
           :luokka "caption text-nowrap"
           :leveys 0.2}

        
          {:otsikko "Viesti"
           :tyyppi :string 
           :nimi ::hairio/viesti
           :leveys 0.5}
          
          {:otsikko "Tyyppi"
           :tyyppi :valinta
           :valinnat hairio/tyyppi-fmt
           :valinta-nayta #(when % (if (vector? %) (second %) (% hairio/tyyppi-fmt)))
           :nimi ::hairio/tyyppi
           :leveys 0.5}

          
          ]
         tulevat-hairiot]
        
        ]
       
       #_ [:ul
        (for* [hairio tulevat]
          [:div
           [:li (listaa-hairioilmoitus hairio)]
           [:div.flex-row [napit/poista "Poista häiriöilmoitus" #(tiedot/poista-hairioilmoitus {:id (::hairio/id hairio)})
                           {:disabled @tiedot/tallennus-kaynnissa?}]]])]
       
       
       )]))

#_ (defn hairiot []
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (komp/ulos #(do (reset! tiedot/hairiot nil)
                    (reset! tiedot/asetetaan-hairioilmoitus? false)))
    (komp/sisaan tiedot/hae-hairiot)
    (fn []
      (let [hairiotilmoitukset @tiedot/hairiot
            voimassaoleva-hairio (hairio/voimassaoleva-hairio hairiotilmoitukset)
            tulevat-hairiot (hairio/tulevat-hairiot hairiotilmoitukset)
            vanhat-hairiot (hairio/vanhat-hairiot hairiotilmoitukset)]
        (if (nil? hairiotilmoitukset)
          [ajax-loader "Haetaan..."]

          [:div.hairioilmoitukset
           [voimassaoleva-hairioilmoitus voimassaoleva-hairio]
           [tulevat-hairioilmoitukset tulevat-hairiot]
           [napit/yleinen-ensisijainen "Aseta häiriöilmoitus"
            #(reset! tiedot/asetetaan-hairioilmoitus? true)]
           [:hr]
           [vanhat-hairioilmoitukset vanhat-hairiot]])))))

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
       #_ [vanhat-hairioilmoitukset e! app]
       
       ]
      
      ))
  
  
  
  #_ (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (komp/ulos #(do (reset! tiedot/hairiot nil)
                  (reset! tiedot/asetetaan-hairioilmoitus? false)))
    (komp/sisaan tiedot/hae-hairiot)
    (fn []
      (let [hairiotilmoitukset @tiedot/hairiot
            voimassaoleva-hairio (hairio/voimassaoleva-hairio hairiotilmoitukset)
            tulevat-hairiot (hairio/tulevat-hairiot hairiotilmoitukset)
            vanhat-hairiot (hairio/vanhat-hairiot hairiotilmoitukset)]
        (if (nil? hairiotilmoitukset)
          [ajax-loader "Haetaan..."]
  
          [:div.hairioilmoitukset
           [voimassaoleva-hairioilmoitus voimassaoleva-hairio]
           [tulevat-hairioilmoitukset tulevat-hairiot]
           [napit/yleinen-ensisijainen "Aseta häiriöilmoitus"
            #(reset! tiedot/asetetaan-hairioilmoitus? true)]
           [:hr]
           [vanhat-hairioilmoitukset vanhat-hairiot]]))))
  )


(defn hairiot []
  [tuck urakka-tila/hallinta-hairiot hairiot*])
