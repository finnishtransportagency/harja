(ns harja.views.urakka.laadunseuranta.tarkastukset
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<! >! chan]]

            [harja.ui.komponentti :as komp]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.laadunseuranta :as laadunseuranta]
            [harja.views.urakka.valinnat :as valinnat]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.loki :refer [log]]
            [harja.ui.grid :as grid]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.ui.kentat :refer [tee-kentta]])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [harja.atom :refer [reaction<!]]))

(def +tarkastystyyppi+ [:tiesto :talvihoito :soratie])

(defonce tarkastustyyppi (atom nil)) ;; nil = kaikki, :tiesto, :talvihoito, :soratie
(defonce tienumero (atom nil)) ;; tienumero, tai kaikki

(defonce valittu-tarkastus (atom nil))

(defonce urakan-tarkastukset
  (reaction<! (let [urakka-id (:id @nav/valittu-urakka)
                    [alku loppu] @tiedot-urakka/valittu-aikavali
                    laadunseurannassa? @laadunseuranta/laadunseurannassa?
                    valilehti @laadunseuranta/valittu-valilehti]
                (when (and laadunseurannassa? (= :tarkastukset valilehti)
                           urakka-id alku loppu)
                  (laadunseuranta/hae-urakan-tarkastukset urakka-id alku loppu)))))
                

(defn uusi-tarkastus []
  {::uusi? true
   :aika (pvm/nyt)})

(defn tarkastuslistaus
  "Tarkastuksien pääkomponentti"
  []
  (komp/luo
   (fn []
     (let [urakka @nav/valittu-urakka]
       [:div.tarkastukset
        [valinnat/urakan-hoitokausi urakka]
        [valinnat/hoitokauden-aikavali urakka]

        [:span.label-ja-kentta
         [:span.kentan-otsikko "Tienumero"]
         [:div.kentta
          [tee-kentta {:tyyppi :numero :placeholder "Rajaa tienumerolla" :kokonaisluku? true} tienumero]]]
        
        [napit/uusi "Uusi tarkastus"
         #(reset! valittu-tarkastus (uusi-tarkastus)) {}]
        
        [grid/grid
         {:otsikko "Tarkastukset"
          :tyhja "Ei tarkastuksia"}
         
         [{:otsikko "Pvm ja aika"
           :tyyppi :pvm-aika :fmt pvm/pvm-aika 
           :nimi :aika}
         


          ]

         @urakan-tarkastukset]]))))

(defn talvihoitomittaus [mittaus]
  (lomake/ryhma "Talvihoitomittaus"
                {:otsikko "Lumimäärä" :tyyppi :numero :yksikko "cm"
                 :nimi :lumimaara :leveys-col 1}
                {:otsikko "Epätasaisuus" :tyyppi :numero :yksikko "cm"
                 :nimi :epatasaisuus :leveys-col 1}
                {:otsikko "Kitka" :tyyppi :numero
                 :nimi :kitka :leveys-col 1}
                {:otsikko "Lämpötila" :tyyppi :numero :yksikko "\u2103"
                 :nimi :lampotila :leveys-col 1}))

(defn tarkastus [tarkastus]
  [:div.tarkastus
   [napit/takaisin "Takaisin tarkastusluetteloon" #(reset! tarkastus nil)]

   [lomake/lomake
    {:luokka :horizontal
     :muokkaa! #(reset! tarkastus %)
     :footer (fn [g]
               [napit/palvelinkutsu-nappi
                "Tallenna tarkastus"
                (fn []
                  (log "jotain pitää kysellä"))
                {:disabled true}])}

    [{:otsikko "Pvm ja aika" :nimi :aika :tyyppi :pvm-aika}
     {:otsikko "Tierekisteriosoite" :nimi :tr
      :tyyppi :tierekisteriosoite}
     {:otsikko "Tarkastus" :nimi :tyyppi
      :tyyppi :valinta
      :valinnat +tarkastystyyppi+
      :valinta-nayta #(case %
                        :tiesto "Tiestötarkastus"
                        :talvihoito "Talvihoitotarkastus"
                        :soratie "Soratien tarkastus"
                        "- valitse -")
      :leveys-col 2}

     (case (:tyyppi @tarkastus)
       :talvihoito (talvihoitomittaus (r/wrap (:talvihoitomittaus @tarkastus)
                                              #(swap! tarkastus assoc :talvihoitomittaus %)))
       nil)
     ]

    @tarkastus]])

(defn tarkastukset
  "Tarkastuksien pääkomponentti"
  []
  (if @valittu-tarkastus
    [tarkastus valittu-tarkastus]
    [tarkastuslistaus]))
