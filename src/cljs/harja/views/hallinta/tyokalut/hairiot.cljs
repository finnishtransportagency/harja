(ns harja.views.hallinta.tyokalut.hairiot
  "Näkymästä voi lähettää kaikille käyttäjille sähköpostia. Hyödyllinen esimerkiksi päivityskatkoista tiedottamiseen."
  (:require [harja.ui.debug :as debug]
            [tuck.core :refer [tuck]]
            [clojure.string]

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
            [harja.tiedot.hallinta.tyokalut.hairiot :as tiedot])
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


(defn- vanhat-hairioilmoitukset [_e! {:keys [vanhat] :as _app}]
  [:div
   [:h3 "Vanhat ilmoitukset"]
   (if (empty? vanhat)
     [:span "Ei vanhoja ilmoituksia"]
     [:ul (for* [hairio vanhat]
            [:li (listaa-hairioilmoitus hairio)])])])


(defn- aseta-hairioilmoitus [e! {:keys [tallennus-kaynnissa? tuore-hairioilmoitus] :as _app}]
  [:div
   [lomake/lomake
    {:otsikko "Uusi häiriöilmoitus tai tiedote"
     :muokkaa! #(e! (tiedot/->TuoreHairioilmoitus (lomake/ilman-lomaketietoja %)))
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


(defn- muokkaa-ilmoitus-lomake [e! tallennus-kaynnissa? muokattava-ilmoitus]
  [:div
   [lomake/lomake
    {:muokkaa! #(e! (tiedot/->MuokkaaIlmoitustaTiedot (lomake/ilman-lomaketietoja %)))
     :footer [:<>
              [napit/tallenna "Tallenna" #(e! (tiedot/->TallennaMuokattuIlmoitus)) {:disabled tallennus-kaynnissa?}]
              [napit/peruuta #(e! (tiedot/->PeruMuokkaus))]]}
    [{:otsikko "Viesti"
      :tyyppi :text
      :nimi :viesti
      :pituus-max 1024
      :palstoja 2
      :koko [80 5]}
     (lomake/rivi
       {:otsikko "Alkamisaika"
        :tyyppi :pvm-aika
        :nimi :alkuaika}
       {:otsikko "Päättymisaika"
        :tyyppi :pvm-aika
        :nimi :loppuaika})]
    muokattava-ilmoitus]])

(defn- yksittainen-voimassaoleva-ilmoitus [e! tallennus-kaynnissa? tyyppi-otsikko ilmoitus muokattava-ilmoitus]
  (let [muokataan-tata? (and muokattava-ilmoitus
                             (= (::hairio/id muokattava-ilmoitus) (::hairio/id ilmoitus)))]
    [:div.margin-bottom-16
     [:h2 (str "Voimassaoleva - " tyyppi-otsikko)]
     (cond
       muokataan-tata?
       [muokkaa-ilmoitus-lomake e! tallennus-kaynnissa? muokattava-ilmoitus]

       ilmoitus
       [:div
        [:p (listaa-hairioilmoitus ilmoitus)]
        [:div
         [napit/yleinen-toissijainen (str "Muokkaa " (clojure.string/lower-case tyyppi-otsikko) "ta")
          #(e! (tiedot/->MuokkaaIlmoitusta ilmoitus))
          {:disabled tallennus-kaynnissa?}]
         [napit/poista (str "Poista " (clojure.string/lower-case tyyppi-otsikko))
          #(e! (tiedot/->PoistaHairio (::hairio/id ilmoitus)))
          {:disabled tallennus-kaynnissa?}]]]

       :else
       [:p "Ei voimassaolevaa ilmoitusta."])]))

(defn- voimassaolevat-ilmoitukset [e! {:keys [voimassaolevat-tyypeittain asetetaan-hairioilmoitus? tallennus-kaynnissa? muokattava-ilmoitus] :as app}]
  (let [{:keys [hairio tiedote]} voimassaolevat-tyypeittain]
    [:div
     [:h1 "Nykyiset ilmoitukset"]
     [:div
      [:p (str
            "Kun asetat häiriöilmoituksen tai tiedotteen, "
            "se näytetään kaikille Harjan käyttäjille selaimen yläpalkissa. "
            "Ilmoituksen yhteydessä näytetään aina ilmoituksen päivämäärä, joten sitä ei tarvitse kirjoittaa erikseen. "
            "Voit myös ajastaa ilmoituksia etukäteen.")]
      [yksittainen-voimassaoleva-ilmoitus e! tallennus-kaynnissa? "Häiriöilmoitus" hairio muokattava-ilmoitus]
      [yksittainen-voimassaoleva-ilmoitus e! tallennus-kaynnissa? "Tiedote" tiedote muokattava-ilmoitus]]

     (when asetetaan-hairioilmoitus?
       [aseta-hairioilmoitus e! app])
     ]))


(defn- tulevat-hairioilmoitukset [e! {:keys [tulevat haku-kaynnissa?] :as _app}]

  (let [tulevat-hairiot (vec tulevat)
        tyypit (map (fn [[k _]] {:tyyppi k}) hairio/tyyppi-fmt)]
    
    [:div.tulevat-hairiot
     (if (empty? tulevat-hairiot)
       [:span "Ei tulevia ilmoituksia"]
       [:<>
        [grid/grid {:otsikko "Tulevat ilmoitukset"
                    :tyhja (if haku-kaynnissa?
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
           :leveys 1
           :luokka "caption text-nowrap"
           :validoi [[:ei-tyhja "Valitse päivämäärä"]]}

          {:otsikko "Loppuaika"
           :nimi ::hairio/loppuaika
           :fmt pvm/pvm-opt
           :tyyppi :pvm-aika
           :leveys 1
           :luokka "caption text-nowrap"
           :validoi [[:ei-tyhja "Valitse päivämäärä"]]}

          {:otsikko "Viesti"
           :tyyppi :string
           :nimi ::hairio/viesti
           :leveys 5
           :validoi [[:ei-tyhja "Anna ilmoitukselle viesti"]]}

          {:otsikko "Tyyppi"
           :tyyppi :valinta
           :valinnat (map (fn [{:keys [tyyppi]}]
                            {:nimi (hairio/tyyppi-fmt tyyppi) :arvo tyyppi})
                       tyypit)
           :valinta-nayta #(hairio/tyyppi-fmt (keyword (if (map? %) (:arvo %) %)))
           :nimi ::hairio/tyyppi
           :leveys 1
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
       [voimassaolevat-ilmoitukset e! app]
       [tulevat-hairioilmoitukset e! app]
       (when-not (:asetetaan-hairioilmoitus? app)
         [napit/yleinen-ensisijainen "Aseta uusi ilmoitus" #(e! (tiedot/->AsetetaanHairioilmoitus))])
       [:hr]
       [vanhat-hairioilmoitukset e! app]
       #_ [debug/debug app]])))


(defn hairiot []
  [tuck urakka-tila/hallinta-hairiot hairiot*])
