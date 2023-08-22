(ns harja.views.hallinta.hairiot
  "Näkymästä voi lähettää kaikille käyttäjille sähköpostia. Hyödyllinen esimerkiksi päivityskatkoista tiedottamiseen."
  (:require [harja.tiedot.hallinta.hairiot :as tiedot]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.ui.komponentti :as komp]
            [harja.domain.hairioilmoitus :as hairio]
            [harja.fmt :as fmt]
            [harja.ui.napit :as napit]
            [harja.ui.lomake :as lomake]
            [harja.pvm :as pvm])
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
    (str (fmt/pvm (::hairio/pvm hairio))
      (when voimassaolo-teksti
        voimassaolo-teksti)
      " - "
      (hairio/tyyppi-fmt (::hairio/tyyppi hairio))
      " - "
      (::hairio/viesti hairio))))

(defn- vanhat-hairioilmoitukset [hairiot]
  [:div
   [:h3 "Vanhat häiriöilmoitukset"]
   (if (empty? hairiot)
     "Ei vanhoja häiriöilmoituksia"
     [:ul
      (for* [hairio hairiot]
          [:li (listaa-hairioilmoitus hairio)])])])

(defn- aseta-hairioilmoitus []
  [:div
   [lomake/lomake
    {:muokkaa! (fn [data]
                 (reset! tiedot/tuore-hairioilmoitus data))
     :footer [:<>
              [napit/tallenna "Aseta" #(tiedot/aseta-hairioilmoitus @tiedot/tuore-hairioilmoitus)
               {:disabled @tiedot/tallennus-kaynnissa?}]
              [napit/peruuta
               #(do (reset! tiedot/asetetaan-hairioilmoitus? false)
                    (reset! tiedot/tuore-hairioilmoitus {:tyyppi :hairio
                                                         :teksti nil}))]]}
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
    @tiedot/tuore-hairioilmoitus]])

(defn- voimassaoleva-hairioilmoitus [hairio]
  [:div
     [:h3 "Nykyinen häiriöilmoitus" ]
     (if @tiedot/asetetaan-hairioilmoitus?
       [aseta-hairioilmoitus]
       [:div
        [:p (if hairio
              (listaa-hairioilmoitus hairio)
              "Ei voimassaolevaa häiriöilmoitusta. Kun asetat häiriöilmoituksen, se näytetään kaikille Harjan käyttäjille selaimen alapalkissa. Ilmoituksen yhteydessä näytetään aina ilmoituksen päivämäärä, joten sitä ei tarvitse kirjoittaa erikseen.")]

        (when hairio
          [napit/poista "Poista häiriöilmoitus" #(tiedot/poista-hairioilmoitus {:id (::hairio/id hairio)})
           {:disabled @tiedot/tallennus-kaynnissa?}])])])

(defn- tulevat-hairioilmoitukset [hairiot]
  (let [tulevat (hairio/tulevat-hairiot hairiot)]
    [:div
     [:h3 "Tulevat häiriöilmoitukset"]
     (if (empty? tulevat)
       "Ei tulevia häiriöilmoituksia"
       [:ul
        (for* [hairio tulevat]
          [:div
           [:li (listaa-hairioilmoitus hairio)]
           [:div.flex-row [napit/poista "Poista häiriöilmoitus" #(tiedot/poista-hairioilmoitus {:id (::hairio/id hairio)})
                           {:disabled @tiedot/tallennus-kaynnissa?}]]])])]))

(defn hairiot []
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

          [:div
           [harja.ui.debug/debug {:a hairiotilmoitukset
                                  :voimassaoleva voimassaoleva-hairio
                                  :tulevat tulevat-hairiot
                                  :vanhat vanhat-hairiot}]
           [voimassaoleva-hairioilmoitus voimassaoleva-hairio]
           [tulevat-hairioilmoitukset tulevat-hairiot]
           [vanhat-hairioilmoitukset vanhat-hairiot]
           [napit/yleinen-ensisijainen "Aseta häiriöilmoitus"
            #(reset! tiedot/asetetaan-hairioilmoitus? true)]])))))
