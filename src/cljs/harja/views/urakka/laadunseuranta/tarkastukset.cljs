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
            [harja.ui.yleiset :as yleiset]

            [harja.views.kartta :as kartta]
            [harja.views.urakka.valinnat :as valinnat]
            [harja.views.urakka.laadunseuranta.havainnot :as havainnot]

            [harja.domain.laadunseuranta :refer [Tarkastus validi-tarkastus?]]

            [clojure.string :as str]
            [harja.domain.roolit :as roolit])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]))

(def +tarkastustyyppi->nimi+ {:tiesto "Tiestötarkastus"
                              :talvihoito "Talvihoitotarkastus"
                              :soratie "Soratien tarkastus"
                              :laatu "Laaduntarkastus"
                              :pistokoe "Pistokoe"})

(def +tarkastustyyppi+ [:tiesto :talvihoito :soratie :laatu :pistokoe])

(defn tarkastustyypit-tekijalle [tekija]
  (case tekija
    :tilaaja [:laatu :pistokoe]
    :urakoitsija [:tiesto :talvihoito :soratie]
    +tarkastustyyppi+))

(defn uusi-tarkastus []
  {:uusi?      true
   :aika       (pvm/nyt)
   :tarkastaja @istunto/kayttajan-nimi
   :havainto {:tekija     (roolit/osapuoli @istunto/kayttaja (:id @nav/valittu-urakka))}})

(defn valitse-tarkastus [tarkastus]
  (go
    (reset! laadunseuranta/valittu-tarkastus
            (update-in (<! (laadunseuranta/hae-tarkastus (:id @nav/valittu-urakka) (:id tarkastus)))
                       [:havainto :sanktiot]
                       (fn [sanktiot]
                         (when sanktiot
                           (into {}
                                 (map (juxt :id identity)
                                      sanktiot))))))))

(defn tarkastuslistaus
  "Tarkastuksien listauskomponentti"
  []
   (fn []
     (let [urakka @nav/valittu-urakka]
       [:div.tarkastukset

        [yleiset/taulukko2 "col-md-6" "col-md-6" "350px" "350px"
         
         [valinnat/urakan-hoitokausi urakka]
         [valinnat/aikavali urakka]

         [:span.label-ja-kentta
          [:span.kentan-otsikko "Tienumero"]
          [:div.kentta
           [tee-kentta {:tyyppi :numero :placeholder "Rajaa tienumerolla" :kokonaisluku? true} laadunseuranta/tienumero]]]

         [:span.label-ja-kentta
          [:span.kentan-otsikko "Tyyppi"]
          [:div.kentta
           [tee-kentta {:tyyppi :valinta :valinnat (conj +tarkastustyyppi+ nil)
                        :valinta-nayta #(case %
                                          nil "Kaikki"
                                          :tiesto "Tiestötarkastukset"
                                          :talvihoito "Talvihoitotarkastukset"
                                          :soratie "Soratien tarkastukset"
                                          :laatu "Laaduntarkastus"
                                          :pistokoe "Pistokoe")}
            laadunseuranta/tarkastustyyppi]]]]

        (when @laadunseuranta/voi-kirjata?
          [napit/uusi "Uusi tarkastus"
                           #(reset! laadunseuranta/valittu-tarkastus (uusi-tarkastus))
           {:luokka "alle-marginia"}])

        [grid/grid
         {:otsikko "Tarkastukset"
          :tyhja "Ei tarkastuksia"
          :rivi-klikattu #(valitse-tarkastus %)}
         
         [{:otsikko "Pvm ja aika"
           :tyyppi :pvm-aika :fmt pvm/pvm-aika :leveys 1
           :nimi :aika}

          {:otsikko "Tyyppi"
           :nimi :tyyppi :fmt +tarkastustyyppi->nimi+ :leveys 1}

          {:otsikko "TR osoite"
           :nimi :tr
           :fmt #(apply yleiset/tierekisteriosoite
                        (map (fn [kentta] (get % kentta))
                             [:numero :alkuosa :alkuetaisyys :loppuosa :loppuetaisyys]))
           :leveys 2}]
         @laadunseuranta/urakan-tarkastukset]])))

(defn talvihoitomittaus []
  (lomake/ryhma "Talvihoitomittaus"
                {:otsikko "Lumimäärä" :tyyppi :numero :yksikko "cm"
                 :nimi :lumimaara :leveys-col 1
                 :hae (comp :lumimaara :talvihoitomittaus) :aseta #(assoc-in %1 [:talvihoitomittaus :lumimaara] %2)}
                {:otsikko "Tasaisuus" :tyyppi :numero :yksikko "cm"
                 :nimi :tasaisuus :leveys-col 1
                 :hae (comp :tasaisuus :talvihoitomittaus) :aseta #(assoc-in %1 [:talvihoitomittaus :tasaisuus] %2)}
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
                   :validoi [[:ei-tyhja "Anna sivukaltevuus%"]]}

                  {:otsikko "Soratiehoitoluokka" :tyyppi :valinta
                   :nimi :hoitoluokka :leveys-col 2
                   :hae (comp :hoitoluokka :soratiemittaus) :aseta #(assoc-in %1 [:soratiemittaus :hoitoluokka] %2)
                   :valinnat [1 2]})))
                 
(defn tarkastus [tarkastus-atom]
  (let [tarkastus @tarkastus-atom]
    (log (pr-str @tarkastus-atom))
    [:div.tarkastus
     [napit/takaisin "Takaisin tarkastusluetteloon" #(reset! tarkastus-atom nil)]

     [lomake/lomake
      {:luokka :default
       :muokkaa! #(reset! tarkastus-atom %)
       :voi-muokata? @laadunseuranta/voi-kirjata?}
      [{:otsikko "Pvm ja aika" :nimi :aika :tyyppi :pvm-aika :pakollinen? true
        :varoita [[:urakan-aikana-ja-hoitokaudella]]}
       {:otsikko "Tierekisteriosoite" :nimi :tr
        :tyyppi :tierekisteriosoite
        :pakollinen? true
        :sijainti (r/wrap (:sijainti tarkastus)
                          #(swap! tarkastus-atom assoc :sijainti %))}
       {:otsikko "Tarkastus" :nimi :tyyppi
        :pakollinen? true
        :tyyppi :valinta
        :valinnat (tarkastustyypit-tekijalle (get-in tarkastus [:havainto :tekija]))
        :valinta-nayta #(case %
                          :tiesto "Tiestötarkastus"
                          :talvihoito "Talvihoitotarkastus"
                          :soratie "Soratien tarkastus"
                          :laatu "Laaduntarkastus"
                          :pistokoe "Pistokoe"
                          "- valitse -")
        :leveys-col 2}
       
       {:otsikko "Tarkastaja" :nimi :tarkastaja
        :tyyppi :string :pituus-max 256
        :pakollinen? true
        :validoi [[:ei-tyhja "Anna tarkastajan nimi"]]
        :leveys-col 4}

       (case (:tyyppi tarkastus)
         :talvihoito (talvihoitomittaus)
         :soratie (soratiemittaus)
         nil)
       
       (when-not (= :soratie (:tyyppi tarkastus))
         {:otsikko "Mittaaja" :nimi :mittaaja
          :pakollinen? true
          :tyyppi :string :pituus-max 256
          :leveys-col 4})]
      
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
          (laadunseuranta/tallenna-tarkastus (:id @nav/valittu-urakka)
                                             (update-in tarkastus [:havainto :sanktiot]
                                                        (fn [sanktiot]
                                                          (when sanktiot
                                                            (vals sanktiot))))))
        
        {:disabled (let [validi? (validi-tarkastus? tarkastus)]
                     (log "tarkastus: " (pr-str tarkastus) " :: validi? " validi?)
                     (not validi?))
         :kun-onnistuu (fn [tarkastus]
                         (reset! laadunseuranta/valittu-tarkastus nil)
                         (laadunseuranta/paivita-tarkastus-listaan! tarkastus))
                         }]]]]))


(defn tarkastukset
  "Tarkastuksien pääkomponentti"
  []
  (komp/luo

    ;; Laitetaan laadunseurannan karttataso päälle kun ollaan
    ;; tarkastuslistauksessa
    (komp/lippu laadunseuranta/karttataso-tarkastukset kartta/kartta-kontentin-vieressa?)
    (komp/sisaan-ulos #(do
                        (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
                        (nav/vaihda-kartan-koko! :XL))
                      #(nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko))
    (komp/kuuntelija
      :tarkastus-klikattu
      (fn [e tarkastus]
        (log "KLIKKASIT TARKASTUSTA: " (pr-str tarkastus))
        (valitse-tarkastus tarkastus)))

    (fn []
      [:span.tarkastukset
       [kartta/sisalto-ja-kartta-2-palstana (if @laadunseuranta/valittu-tarkastus
                                       [tarkastus laadunseuranta/valittu-tarkastus]
                                       [tarkastuslistaus])]])))
