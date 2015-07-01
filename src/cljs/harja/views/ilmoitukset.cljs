(ns harja.views.ilmoitukset
  "Harjan ilmoituksien pääsivu."
  (:require [reagent.core :refer [atom] :as r]

            [harja.tiedot.ilmoitukset :as tiedot]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :refer [grid]]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.loki :refer [log]]
            [harja.ui.napit :refer [palvelinkutsu-nappi]]
            [harja.ui.valinnat :refer [urakan-hoitokausi-ja-aikavali]]

            [harja.tiedot.urakka :as u]

            [bootstrap :as bs]
            [harja.tiedot.navigaatio :as nav]))

(defn urakan-sivulle-nappi
  []
  (when @tiedot/valittu-urakka
    [:button.nappi-toissijainen
     {:on-click #(reset! nav/sivu :urakat)}
     "Urakan sivulle"]))

(defn ilmoituksen-tiedot
  []
  [:div
   [:button.nappi-ensisijainen
    {:on-click #(reset! tiedot/valittu-ilmoitus nil)}
    "Palaa"]
   (urakan-sivulle-nappi)
   [bs/panel {}
    "Ilmoituksen tiedot"
    [yleiset/tietoja {}
     "Ilmoitettu:" (:ilmoitettu @tiedot/valittu-ilmoitus)]]
   ])

(defn ilmoitusten-paanakyma
  []
  (komp/luo
    (fn []
      [:span
       [:h3 "Ilmoitukset"]
       (urakan-sivulle-nappi)

       [:div

        [:label "Hae ilmoituksia: "[tee-kentta {:tyyppi :string} tiedot/hakuehto]]

        (if @tiedot/valittu-urakka
          [urakan-hoitokausi-ja-aikavali
           @tiedot/valittu-urakka
           (u/hoitokaudet @tiedot/valittu-urakka) u/valittu-hoitokausi u/valitse-hoitokausi!
           tiedot/valittu-aikavali]

          [:div
           [:label "Saapunut:" [tee-kentta {:tyyppi :pvm :otsikko "Saapunut"} (r/wrap
                                                                                (first @tiedot/valittu-aikavali)
                                                                                (fn [uusi-arvo]
                                                                                  (reset! tiedot/valittu-aikavali
                                                                                          [uusi-arvo (second @tiedot/valittu-aikavali)])))]]
           [:label " \u2014 " [tee-kentta {:tyyppi :pvm} (r/wrap
                                                           (second @tiedot/valittu-aikavali)
                                                           (fn [uusi-arvo]
                                                             (swap! tiedot/valittu-aikavali
                                                                    (fn [[alku _]]
                                                                      [alku uusi-arvo]))))]]])

        [:div
         [:label "Tilat"
          (for [ehto @tiedot/valitut-tilat]
            [tee-kentta
             {:tyyppi :boolean :otsikko (clojure.string/capitalize (name (first ehto)))}
             (r/wrap
               (second ehto)
               (fn [uusi-tila]
                 (reset! tiedot/valitut-tilat
                         (assoc @tiedot/valitut-tilat (first ehto) uusi-tila))))])]]

        [:div
         [:label "Ilmoituksen tyyppi"
          (for [ehto @tiedot/valitut-ilmoitusten-tyypit]
            [tee-kentta
             {:tyyppi :boolean :otsikko (clojure.string/capitalize (name (first ehto)))}
             (r/wrap
               (second ehto)
               (fn [uusi-tila]
                 (reset! tiedot/valitut-ilmoitusten-tyypit
                         (assoc @tiedot/valitut-ilmoitusten-tyypit (first ehto) uusi-tila))))])]]]

       [palvelinkutsu-nappi
        "Hae ilmoitukset"
        #(tiedot/hae-ilmoitukset)
        {:ikoni (harja.ui.ikonit/search)
         :kun-onnistuu #(tiedot/aloita-pollaus)}]


       [grid
        {:tyhja (if @tiedot/haetut-ilmoitukset "Ei löytyneitä tietoja" [ajax-loader "Haetaan ilmoutuksia"])
         :rivi-klikattu #(reset! tiedot/valittu-ilmoitus %)}

        [{:otsikko "Ilmoitettu" :nimi :ilmoitettu}
         {:otsikko "Sijainti" :nimi :sijainti}
         {:otsikko "Tyyppi" :nimi :tyyppi}
         {:otsikko "Vast." :nimi :vastattu?}]

        @tiedot/haetut-ilmoitukset]])))

(defn ilmoitukset []
  (komp/luo
    (komp/lippu tiedot/ilmoitusnakymassa?)

    (fn []
      (if @tiedot/valittu-ilmoitus
        [ilmoituksen-tiedot]
        [ilmoitusten-paanakyma]))))