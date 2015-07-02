(ns harja.views.urakka.laadunseuranta.tarkastukset
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<! >! chan]]

            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.loki :refer [log]]
            
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.laadunseuranta :as laadunseuranta]
            [harja.tiedot.istunto :as istunto]
            
            [harja.ui.grid :as grid]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.ui.komponentti :as komp]

            [harja.views.urakka.valinnat :as valinnat]
            [harja.views.urakka.laadunseuranta.havainnot :as havainnot]
            
            [harja.domain.laadunseuranta :refer [Tarkastus validi-tarkastus?]])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [harja.atom :refer [reaction<!]]))

(def +tarkastystyyppi+ [:tiesto :talvihoito :soratie])

(defonce tarkastustyyppi (atom nil)) ;; nil = kaikki, :tiesto, :talvihoito, :soratie
(defonce tienumero (atom nil)) ;; tienumero, tai kaikki

(defonce valittu-tarkastus (atom nil))

(defonce urakan-tarkastukset
  (reaction<! [urakka-id (:id @nav/valittu-urakka)
               [alku loppu] @tiedot-urakka/valittu-aikavali
               laadunseurannassa? @laadunseuranta/laadunseurannassa?
               valilehti @laadunseuranta/valittu-valilehti]
              
              (when (and laadunseurannassa? (= :tarkastukset valilehti)
                         urakka-id alku loppu)
                (laadunseuranta/hae-urakan-tarkastukset urakka-id alku loppu))))
                

(defn uusi-tarkastus []
  {:uusi? true
   :aika (pvm/nyt)
   :tarkastaja @istunto/kayttajan-nimi})

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

(defn talvihoitomittaus []
  (lomake/ryhma "Talvihoitomittaus"
                {:otsikko "Lumimäärä" :tyyppi :numero :yksikko "cm"
                 :nimi :lumimaara :leveys-col 1
                 :hae (comp :lumimaara :talvihoitomittaus) :aseta #(assoc-in %1 [:talvihoitomittaus :lumimaara] %2)}
                {:otsikko "Epätasaisuus" :tyyppi :numero :yksikko "cm"
                 :nimi :epatasaisuus :leveys-col 1
                 :hae (comp :epatasaisuus :talvihoitomittaus) :aseta #(assoc-in %1 [:talvihoitomittaus :epatasaisuus] %2)}
                {:otsikko "Kitka" :tyyppi :numero
                 :nimi :kitka :leveys-col 1
                 :hae (comp :kitka :talvihoitomittaus) :aseta #(assoc-in %1 [:talvihoitomittaus :kitka] %2)}
                {:otsikko "Lämpötila" :tyyppi :numero :yksikko "\u2103"
                 :validoi [#(when-not (<= -55 %1 55)
                              "Anna lämpotila välillä -55 \u2103 \u2014 +55 \u2103")]
                 :nimi :lampotila :leveys-col 1
                 :hae (comp :lampotila :talvihoitomittaus) :aseta #(assoc-in %1 [:talvihoitomittaus :lampotila] %2)}))

(defn soratiemittaus []
  (let [kuntoluokka (fn [arvo _]
                      (when (and arvo (not (<= 1 arvo 5)))
                               "Anna arvo 1 - 5"))]
    (lomake/ryhma "Soratietarkastus"
                  {:otsikko "Tasaisuus" :tyyppi :numero
                   :nimi :tasaisuus :leveys-col 1
                   :hae (comp :tasaisuus :soratiemittaus) :aseta #(assoc-in %1 [:soratiemittaus :tasaisuus] %2)
                   :validoi [kuntoluokka]}

                  {:otsikko "Kiinteys" :tyyppi :numero
                   :nimi :kiinteys :leveys-col 1
                   :hae (comp :kiinteys :soratiemittaus) :aseta #(assoc-in %1 [:soratiemittaus :kiinteys] %2)
                   :validoi [kuntoluokka]}

                  {:otsikko "Pölyävyys" :tyyppi :numero
                   :nimi :polyavyys :leveys-col 1
                   :hae (comp :polyavyys :soratiemittaus) :aseta #(assoc-in %1 [:soratiemittaus :polyavyys] %2)
                   :validoi [kuntoluokka]}

                  {:otsikko "Sivukaltevuus" :tyyppi :numero :yksikko "%"
                   :nimi :sivukaltevuus :leveys-col 1
                   :hae (comp :sivukaltevuus :soratiemittaus) :aseta #(assoc-in %1 [:soratiemittaus :sivukaltevuus] %2)
                   :validoi [[:ei-tyhja "Anna sivukaltevuus%"]]})))
                 
(defn tarkastus [tarkastus-atom]
  (let [tarkastus @tarkastus-atom]
    [:div.tarkastus
     [napit/takaisin "Takaisin tarkastusluetteloon" #(reset! tarkastus nil)]

     [lomake/lomake
      {:luokka :horizontal
       :muokkaa! #(reset! tarkastus-atom %)}

      [{:otsikko "Pvm ja aika" :nimi :aika :tyyppi :pvm-aika :pakollinen? true}
       {:otsikko "Tierekisteriosoite" :nimi :tr
        :tyyppi :tierekisteriosoite
        :sijainti (r/wrap (:sijainti tarkastus)
                          #(swap! tarkastus-atom assoc :sijainti %))}
       {:otsikko "Tarkastus" :nimi :tyyppi
        :pakollinen? true
        :tyyppi :valinta
        :valinnat +tarkastystyyppi+
        :valinta-nayta #(case %
                          :tiesto "Tiestötarkastus"
                          :talvihoito "Talvihoitotarkastus"
                          :soratie "Soratien tarkastus"
                          "- valitse -")
        :leveys-col 2}
       
       {:otsikko "Tarkastaja" :nimi :tarkastaja
        :tyyppi :string :pituus-max 256
        :validoi [[:ei-tyhja "Anna tarkastajan nimi"]]
        :leveys-col 4}

       (case (:tyyppi tarkastus)
         :talvihoito (talvihoitomittaus)
         :soratie (soratiemittaus)
         nil)
       
       (when-not (= :soratie (:tyyppi tarkastus))
         {:otsikko "Mittaaja" :nimi :mittaaja
          :tyyppi :string :pituus-max 256
          :leveys-col 4})
       ]
      
      tarkastus]

     [havainnot/havainto {:osa-tarkastusta? true}
      (r/wrap (:havainto tarkastus)
              #(swap! tarkastus-atom assoc :havainto %))]

     [:div.row
      [:div.col-sm-2]
      [:div.col-sm-2
       [napit/palvelinkutsu-nappi
        "Tallenna tarkastus"
        (fn []
          (laadunseuranta/tallenna-tarkastus (:id @nav/valittu-urakka) tarkastus))
        {:disabled (not (validi-tarkastus? tarkastus))}]]]
     ]))

(defn tarkastukset
  "Tarkastuksien pääkomponentti"
  []
  (if @valittu-tarkastus
    [tarkastus valittu-tarkastus]
    [tarkastuslistaus]))
