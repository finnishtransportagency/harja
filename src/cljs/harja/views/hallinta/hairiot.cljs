(ns harja.views.hallinta.hairiot
  "Näkymästä voi lähettää kaikille käyttäjille sähköpostia. Hyödyllinen esimerkiksi päivityskatkoista tiedottamiseen."
  (:require [reagent.core :refer [atom] :as r]
            [harja.tiedot.hallinta.hairiot :as tiedot]
            [cljs.core.async :refer [<! >! timeout chan]]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.ui.komponentti :as komp]
            [harja.domain.hairioilmoitus :as hairio]
            [harja.loki :refer [log]]
            [harja.fmt :as fmt]
            [harja.ui.napit :as napit]
            [harja.ui.kentat :as kentat]
            [harja.ui.lomake :as lomake])
  (:require-macros [harja.tyokalut.ui :refer [for*]]))

(defn- listaa-hairioilmoitus [hairio]
  (str (fmt/pvm (::hairio/pvm hairio))
       " - "
       (hairio/tyyppi-fmt (::hairio/tyyppi hairio))
       " - "
       (::hairio/viesti hairio)))

(defn- vanhat-hairioilmoitukset [hairiot tuorein-hairio]
  [:div
   [:h3 "Vanhat häiriöilmoitukset"]
   (if (empty? hairiot)
     "Ei vanhoja häiriöilmoituksia"
     [:ul
      (for* [hairio hairiot]
        (when (not= (::hairio/id hairio) (::hairio/id tuorein-hairio))
          [:li (listaa-hairioilmoitus hairio)]))])])

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

(defn- tuore-hairioilmoitus [tuore-hairio]
  [:div
   [:h3 "Nykyinen häiriöilmoitus"]
   (if @tiedot/asetetaan-hairioilmoitus?
     [aseta-hairioilmoitus]
     [:div
      [:p (if tuore-hairio
            (listaa-hairioilmoitus tuore-hairio)
            "Ei voimassaolevaa häiriöilmoitusta. Kun asetat häiriöilmoituksen, se näytetään kaikille Harjan käyttäjille selaimen alapalkissa. Ilmoituksen yhteydessä näytetään aina ilmoituksen päivämäärä, joten sitä ei tarvitse kirjoittaa erikseen.")]

      (when-not tuore-hairio
        [napit/yleinen-ensisijainen "Aseta häiriöilmoitus"
         #(reset! tiedot/asetetaan-hairioilmoitus? true)])

      (when tuore-hairio
        [napit/poista "Poista häiriöilmoitus" tiedot/poista-hairioilmoitus
         {:disabled @tiedot/tallennus-kaynnissa?}])])])

(defn hairiot []
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (komp/ulos #(do (reset! tiedot/hairiot nil)
                    (reset! tiedot/asetetaan-hairioilmoitus? false)))
    (komp/sisaan tiedot/hae-hairiot)
    (fn []
      (let [hairiotilmoitukset @tiedot/hairiot
            tuorein-voimassaoleva-hairio (hairio/voimassaoleva-hairio hairiotilmoitukset)]
        (if (nil? hairiotilmoitukset)
          [ajax-loader "Haetaan..."]

          [:div
           [tuore-hairioilmoitus tuorein-voimassaoleva-hairio]
           [vanhat-hairioilmoitukset hairiotilmoitukset tuorein-voimassaoleva-hairio]])))))
