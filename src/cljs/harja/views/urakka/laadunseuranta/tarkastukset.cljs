(ns harja.views.urakka.laadunseuranta.tarkastukset
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<! >! chan]]

            [harja.pvm :as pvm]
            [harja.loki :refer [log]]

            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.laadunseuranta.tarkastukset :as tarkastukset]
            [harja.tiedot.istunto :as istunto]

            [harja.ui.grid :as grid]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.liitteet :as liitteet]
            
            [harja.views.kartta :as kartta]
            [harja.views.urakka.valinnat :as valinnat]

            [harja.domain.laadunseuranta :refer [Tarkastus validi-tarkastus?]]
            [harja.tiedot.urakka.laadunseuranta.tarkastukset-kartalla :as tarkastukset-kartalla]
            [harja.tiedot.urakka.laadunseuranta.laatupoikkeamat :as tiedot-laatupoikkeamat])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]))



(def +tarkastustyyppi+ [:tiesto :talvihoito :soratie :laatu :pistokoe])

(defn tarkastustyypit-tekijalle [tekija]
  (case tekija
    :tilaaja [:laatu :pistokoe]
    :urakoitsija [:tiesto :talvihoito :soratie]
    +tarkastustyyppi+))

(defn uusi-tarkastus []
  {:uusi?      true
   :aika       (pvm/nyt)
   :tarkastaja @istunto/kayttajan-nimi})

(defn valitse-tarkastus [tarkastus]
  (go
    (reset! tarkastukset/valittu-tarkastus
            (<! (tarkastukset/hae-tarkastus (:id @nav/valittu-urakka) (:id tarkastus))))))

(defn tarkastuslistaus
  "Tarkastuksien listauskomponentti"
  []
   (fn []
     (let [urakka @nav/valittu-urakka]
       [:div.tarkastukset

        [yleiset/taulukko2 "col-md-6" "col-md-6" "350px" "350px"
         
         [valinnat/urakan-hoitokausi urakka]
         [valinnat/aikavali]

         [valinnat/tienumero tarkastukset/tienumero]

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
            tarkastukset/tarkastustyyppi]]]]

        (when @tiedot-laatupoikkeamat/voi-kirjata?
          [napit/uusi "Uusi tarkastus"
                           #(reset! tarkastukset/valittu-tarkastus (uusi-tarkastus))
           {:luokka "alle-marginia"}])

        [grid/grid
         {:otsikko "Tarkastukset"
          :tyhja "Ei tarkastuksia"
          :rivi-klikattu #(valitse-tarkastus %)}
         
         [{:otsikko "Pvm ja aika"
           :tyyppi :pvm-aika :fmt pvm/pvm-aika :leveys 1
           :nimi :aika}

          {:otsikko "Tyyppi"
           :nimi :tyyppi :fmt tarkastukset/+tarkastustyyppi->nimi+ :leveys 1}

          {:otsikko "TR osoite"
           :nimi :tr
           :fmt #(apply yleiset/tierekisteriosoite
                        (map (fn [kentta] (get % kentta))
                             [:numero :alkuosa :alkuetaisyys :loppuosa :loppuetaisyys]))
           :leveys 2}]
         @tarkastukset/urakan-tarkastukset]])))

(defn talvihoitomittaus []
  (lomake/ryhma "Talvihoitomittaus"
                {:otsikko "Lumimäärä" :tyyppi :numero :yksikko "cm"
                 :nimi :lumimaara :leveys-col 2
                 :hae (comp :lumimaara :talvihoitomittaus) :aseta #(assoc-in %1 [:talvihoitomittaus :lumimaara] %2)}
                {:otsikko "Tasaisuus" :tyyppi :numero :yksikko "cm"
                 :nimi :tasaisuus :leveys-col 2
                 :hae (comp :tasaisuus :talvihoitomittaus) :aseta #(assoc-in %1 [:talvihoitomittaus :tasaisuus] %2)}
                {:otsikko "Kitka" :tyyppi :numero
                 :nimi :kitka :leveys-col 2
                 :hae (comp :kitka :talvihoitomittaus) :aseta #(assoc-in %1 [:talvihoitomittaus :kitka] %2)}
                {:otsikko "Lämpötila" :tyyppi :numero :yksikko "\u2103"
                 :validoi [#(when-not (<= -55 %1 55)
                              "Anna lämpotila välillä -55 \u2103 \u2014 +55 \u2103")]
                 :nimi :lampotila :leveys-col 2
                 :hae (comp :lampotila :talvihoitomittaus) :aseta #(assoc-in %1 [:talvihoitomittaus :lampotila] %2)}))

(defn soratiemittaus []
  (let [kuntoluokka (fn [arvo _]
                      (when (and arvo (not (<= 1 arvo 5)))
                               "Anna arvo 1 - 5"))]
    (lomake/ryhma "Soratietarkastus"
                  {:otsikko "Tasaisuus" :tyyppi :numero
                   :nimi :tasaisuus :leveys-col 2
                   :hae (comp :tasaisuus :soratiemittaus) :aseta #(assoc-in %1 [:soratiemittaus :tasaisuus] %2)
                   :validoi [kuntoluokka]}

                  {:otsikko "Kiinteys" :tyyppi :numero
                   :nimi :kiinteys :leveys-col 2
                   :hae (comp :kiinteys :soratiemittaus) :aseta #(assoc-in %1 [:soratiemittaus :kiinteys] %2)
                   :validoi [kuntoluokka]}

                  {:otsikko "Pölyävyys" :tyyppi :numero
                   :nimi :polyavyys :leveys-col 2
                   :hae (comp :polyavyys :soratiemittaus) :aseta #(assoc-in %1 [:soratiemittaus :polyavyys] %2)
                   :validoi [kuntoluokka]}

                  {:otsikko "Sivukaltevuus" :tyyppi :numero :yksikko "%"
                   :nimi :sivukaltevuus :leveys-col 2
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
      {:muokkaa! #(reset! tarkastus-atom %)
       :voi-muokata? @tiedot-laatupoikkeamat/voi-kirjata?}
      [{:otsikko "Pvm ja aika" :nimi :aika :tyyppi :pvm-aika :pakollinen? true
        :varoita [[:urakan-aikana-ja-hoitokaudella]]}
       {:otsikko "Tie\u00ADrekisteri\u00ADosoite" :nimi :tr
        :tyyppi :tierekisteriosoite
        :pakollinen? true
        :sijainti (r/wrap (:sijainti tarkastus)
                          #(swap! tarkastus-atom assoc :sijainti %))}
       {:otsikko "Tar\u00ADkastus" :nimi :tyyppi
        :pakollinen? true
        :tyyppi :valinta
        :valinnat (tarkastustyypit-tekijalle (:tekija tarkastus))
        :valinta-nayta #(case %
                          :tiesto "Tiestötarkastus"
                          :talvihoito "Talvihoitotarkastus"
                          :soratie "Soratien tarkastus"
                          :laatu "Laaduntarkastus"
                          :pistokoe "Pistokoe"
                          "- valitse -")
        :leveys-col 4}
       
       {:otsikko "Tar\u00ADkastaja" :nimi :tarkastaja
        :tyyppi :string :pituus-max 256
        :pakollinen? true
        :validoi [[:ei-tyhja "Anna tarkastajan nimi"]]
        :leveys-col 6}

       {:otsikko "Havain\u00ADnot" :nimi :havainnot
        :koko [80 :auto]
        :tyyppi :text :pakollinen? true
        :validoi [[:ei-tyhja "Kirjaa havainnot"]]
        :leveys-col 6}
       
       (case (:tyyppi tarkastus)
         :talvihoito (talvihoitomittaus)
         :soratie (soratiemittaus)
         nil)

       {:otsikko     "Liitteet" :nimi :liitteet
        :komponentti [liitteet/liitteet {:urakka-id         (:id @nav/valittu-urakka)
                                         :uusi-liite-atom   (r/wrap (:uusi-liite tarkastus)
                                                                    #(swap! tarkastus-atom assoc :uusi-liite %))
                                         :uusi-liite-teksti "Lisää liite tarkastukseen"}
                      (:liitteet tarkastus)]}]
      
      tarkastus]

     

     [:div.row
      [:div.col-sm-2]
      [:div.col-sm-2
       [napit/palvelinkutsu-nappi
        "Tallenna tarkastus"
        (fn []
          (tarkastukset/tallenna-tarkastus (:id @nav/valittu-urakka) tarkastus))
        
        {:disabled (let [validi? (validi-tarkastus? tarkastus)]
                     (log "tarkastus: " (pr-str tarkastus) " :: validi? " validi?)
                     (not validi?))
         :kun-onnistuu (fn [tarkastus]
                         (reset! tarkastukset/valittu-tarkastus nil)
                         (tarkastukset/paivita-tarkastus-listaan! tarkastus))}]]]]))


(defn tarkastukset
  "Tarkastuksien pääkomponentti"
  []
  (komp/luo

    ;; Laitetaan laadunseurannan karttataso päälle kun ollaan
    ;; tarkastuslistauksessa
    (komp/lippu tarkastukset-kartalla/karttataso-tarkastukset kartta/kartta-kontentin-vieressa?)
    (komp/kuuntelija :tarkastus-klikattu #(reset! tarkastukset/valittu-tarkastus %2))
    (komp/ulos (kartta/kuuntele-valittua! tarkastukset/valittu-tarkastus))
    (komp/sisaan-ulos #(do
                        (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
                        (nav/vaihda-kartan-koko! :XL))
                      #(nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko))

    (fn []
      [:span.tarkastukset
       [kartta/sisalto-ja-kartta-2-palstana (if @tarkastukset/valittu-tarkastus
                                       [tarkastus tarkastukset/valittu-tarkastus]
                                       [tarkastuslistaus])]])))
