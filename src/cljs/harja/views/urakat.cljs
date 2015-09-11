(ns harja.views.urakat
  "Harjan näkymä, jossa näytetään karttaa sekä kontekstisidonnaiset asiat."
  (:require [reagent.core :refer [atom] :as reagent]
            [cljs.core.async :as async :refer [chan <! >!]]
            [bootstrap :as bs]
            [harja.ui.listings :refer [suodatettu-lista]]
            [harja.ui.leaflet :refer [leaflet] :as leaflet]
            [harja.ui.yleiset :as yleiset]
            
            [harja.tiedot.hallintayksikot :as hal]
            [harja.tiedot.urakat :as ur]
            [harja.loki :refer [log]]
            
            [harja.asiakas.tapahtumat :as t]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.navigaatio :as nav]

            [harja.views.urakka :as urakka]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn valitse-hallintayksikko []
  [:span
   [:h5.haku-otsikko "Valitse hallintayksikkö"]
   [:div
    ^{:key "hy-lista"}
    [suodatettu-lista {:format :nimi :haku :nimi
                       :selection nav/valittu-hallintayksikko
                       :on-select nav/valitse-hallintayksikko
                       :aputeksti "Kirjoita hallintayksikön nimi tähän"}
     @hal/hallintayksikot]]])

(defn valitse-urakka []
  (let [urakkalista @nav/urakkalista]
      (if (nil? urakkalista)
        [yleiset/ajax-loader "Urakoita haetaan..."]
        [:span
         [:h5.haku-otsikko "Valitse hallintayksikön urakka"]
         [:div
          ^{:key "ur-lista"}
          [suodatettu-lista {:format         :nimi :haku :nimi
                             :selection      nav/valittu-urakka
                             :nayta-ryhmat   [:kaynnissa :paattyneet]
                             :ryhmittely     (let [nyt (pvm/nyt)]
                                               #(if (pvm/jalkeen? nyt (:loppupvm %))
                                                 :paattyneet
                                                 :kaynnissa))
                             :ryhman-otsikko #(case %
                                               :kaynnissa "Käynnissä olevat urakat"
                                               :paattyneet "Päättyneet urakat")
                             :on-select      nav/valitse-urakka
                             :aputeksti      "Kirjoita urakan nimi tähän"}
           @nav/suodatettu-urakkalista]]])))

(defn valitse-hallintayksikko-ja-urakka
  "Jos hallintayksikköä ei ole valittu, palauttaa hallintayksikönvalintakomponentin
   Jos hallintayksikkö on valittu, mutta urakkaa ei, palauttaa urakanvalintakomponentin
   Jos molemmat on valittu, palauttaa nil"
  []
  (let [v-hal @nav/valittu-hallintayksikko
        v-ur @nav/valittu-urakka]
    (if-not v-hal
      (valitse-hallintayksikko)
      (when-not v-ur
        (valitse-urakka)))))

(defn urakat
  "Urakan koko sisältö."
  []
   ;; TODO: urakkasivun koon (col-sm-?) oltava dynaaminen perustuen kartan kokoon joka on navigaatio.cljs:ssä
   (let [v-ur @nav/valittu-urakka]
     (or (valitse-hallintayksikko-ja-urakka)
         [urakka/urakka v-ur])))