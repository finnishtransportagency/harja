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
     (let [urakka @nav/valittu-urakka
           tarkastukset (reverse (sort-by :aika @tarkastukset/urakan-tarkastukset))]
       [:div.tarkastukset

        
        [valinnat/urakan-hoitokausi urakka]
        [valinnat/aikavali]

                
        [:span.label-ja-kentta
         [:span.kentan-otsikko "Tyyppi"]
         [:div.kentta
          [tee-kentta {:tyyppi        :valinta :valinnat (conj +tarkastustyyppi+ nil)
                       :valinta-nayta #(case %
                                        nil "Kaikki"
                                        :tiesto "Tiestötarkastukset"
                                        :talvihoito "Kelitarkastukset"
                                        :soratie "Soratien tarkastukset"
                                        :laatu "Laaduntarkastus"
                                        :pistokoe "Pistokoe")}
           tarkastukset/tarkastustyyppi]]]
        [valinnat/tienumero tarkastukset/tienumero]


        (when @tiedot-laatupoikkeamat/voi-kirjata?
          [napit/uusi "Uusi tarkastus"
                           #(reset! tarkastukset/valittu-tarkastus (uusi-tarkastus))
           {:luokka "alle-marginia"}])

        [grid/grid
         {:otsikko "Tarkastukset"
          :tyhja "Ei tarkastuksia"
          :rivi-klikattu #(valitse-tarkastus %)
          :jarjesta :aika}
         
         [{:otsikko "Pvm ja aika"
           :tyyppi :pvm-aika :fmt pvm/pvm-aika :leveys 1
           :nimi :aika}

          {:otsikko "Tyyppi"
           :nimi :tyyppi :fmt tarkastukset/+tarkastustyyppi->nimi+ :leveys 1}

          {:otsikko "TR osoite"
           :nimi :tr
           :leveys 2
           :fmt #(apply yleiset/tierekisteriosoite
                        (map (fn [kentta] (get % kentta))
                             [:numero :alkuosa :alkuetaisyys :loppuosa :loppuetaisyys]))}

          {:otsikko "Havainnot"
           :nimi :havainnot
           :leveys 4
           :tyyppi :string
           :fmt #(if (> (count %) 50)
                   (str (.substring % 0 50) "...")
                   %)}]
         @tarkastukset/urakan-tarkastukset]])))


(defn talvihoitomittaus []
  (lomake/ryhma {:otsikko "Talvihoitomittaus"
                 :rivi? true}
                {:otsikko "Lumimäärä" :tyyppi :numero :yksikko "cm"
                 :nimi :lumimaara
                 :hae (comp :lumimaara :talvihoitomittaus) :aseta #(assoc-in %1 [:talvihoitomittaus :lumimaara] %2)}
                {:otsikko "Tasaisuus" :tyyppi :numero :yksikko "cm"
                 :nimi :tasaisuus
                 :hae (comp :tasaisuus :talvihoitomittaus) :aseta #(assoc-in %1 [:talvihoitomittaus :tasaisuus] %2)}
                {:otsikko "Kitkakerroin" :tyyppi :numero
                 :nimi :kitka
                 :hae (comp :kitka :talvihoitomittaus) :aseta #(assoc-in %1 [:talvihoitomittaus :kitka] %2)}
                {:otsikko "Ilman lämpötila" :tyyppi :numero :yksikko "\u2103"
                 :validoi [#(when-not (<= -55 %1 55)
                              "Anna lämpotila välillä -55 \u2103 \u2014 +55 \u2103")]
                 :nimi :lampotila_ilma
                 :hae (comp :ilma :lampotila :talvihoitomittaus) :aseta #(assoc-in %1 [:talvihoitomittaus :lampotila :ilma] %2)}
                {:otsikko "Tien lämpötila" :tyyppi :numero :yksikko "\u2103"
                 :validoi [#(when-not (<= -55 %1 55)
                             "Anna lämpotila välillä -55 \u2103 \u2014 +55 \u2103")]
                 :nimi :lampotila_tie
                 :hae (comp :tie :lampotila :talvihoitomittaus) :aseta #(assoc-in %1 [:talvihoitomittaus :lampotila :tie] %2)}))

(defn soratiemittaus []
  (let [kuntoluokka (fn [arvo _]
                      (when (and arvo (not (<= 1 arvo 5)))
                               "Anna arvo 1 - 5"))]
    (lomake/ryhma {:otsikko "Soratiemittaus"
                   :rivi? true}
                  {:otsikko "Tasaisuus" :tyyppi :numero
                   :nimi :tasaisuus :palstoja 1
                   :hae (comp :tasaisuus :soratiemittaus) :aseta #(assoc-in %1 [:soratiemittaus :tasaisuus] %2)
                   :validoi [kuntoluokka]}

                  {:otsikko "Kiinteys" :tyyppi :numero
                   :nimi :kiinteys :palstoja 1
                   :hae (comp :kiinteys :soratiemittaus) :aseta #(assoc-in %1 [:soratiemittaus :kiinteys] %2)
                   :validoi [kuntoluokka]}

                  {:otsikko "Pölyävyys" :tyyppi :numero
                   :nimi :polyavyys :palstoja 1
                   :hae (comp :polyavyys :soratiemittaus) :aseta #(assoc-in %1 [:soratiemittaus :polyavyys] %2)
                   :validoi [kuntoluokka]}

                  {:otsikko "Sivukaltevuus" :tyyppi :numero :yksikko "%"
                   :nimi :sivukaltevuus :palstoja 1
                   :hae (comp :sivukaltevuus :soratiemittaus) :aseta #(assoc-in %1 [:soratiemittaus :sivukaltevuus] %2)
                   :validoi [[:ei-tyhja "Anna sivukaltevuus%"]]}

                  {:otsikko "Soratiehoitoluokka" :tyyppi :valinta
                   :nimi :hoitoluokka :palstoja 1
                   :hae (comp :hoitoluokka :soratiemittaus) :aseta #(assoc-in %1 [:soratiemittaus :hoitoluokka] %2)
                   :valinnat [1 2]})))
                 
(defn tarkastus [tarkastus-atom]
  (let [tarkastus @tarkastus-atom
        jarjestelmasta? (:jarjestelma tarkastus)]
    (log (pr-str @tarkastus-atom))
    [:div.tarkastus
     [napit/takaisin "Takaisin tarkastusluetteloon" #(reset! tarkastus-atom nil)]
     
     [lomake/lomake
      {:otsikko (if (:id tarkastus) "Muokkaa tarkastuksen tietoja" "Uusi tarkastus")
       :muokkaa!     #(reset! tarkastus-atom %)
       :voi-muokata? (and @tiedot-laatupoikkeamat/voi-kirjata?
                          (not jarjestelmasta?))
       :footer [napit/palvelinkutsu-nappi
                "Tallenna tarkastus"
                (fn []
                  (tarkastukset/tallenna-tarkastus (:id @nav/valittu-urakka) tarkastus))
                
                {:disabled (let [validi? (lomake/voi-tallentaa? tarkastus)]
                             (log "tarkastus: " (pr-str tarkastus) " :: validi? " validi?)
                             (not validi?))
                 :kun-onnistuu (fn [tarkastus]
                                 (reset! tarkastukset/valittu-tarkastus nil)
                                 (tarkastukset/paivita-tarkastus-listaan! tarkastus))}]}
      [(when jarjestelmasta?
         {:otsikko     "Lähde" :nimi :luoja :tyyppi :string
          :hae         (fn [rivi] (str "Järjestelmä (" (:kayttajanimi rivi) " / " (:organisaatio rivi) ")"))
          :muokattava? (constantly false)
          :vihje       "Tietojärjestelmästä tulleen tiedon muokkaus ei ole sallittu."})

       {:otsikko "Pvm ja aika" :nimi :aika :tyyppi :pvm-aika :pakollinen? true
        :varoita [[:urakan-aikana-ja-hoitokaudella]]}
       
       {:otsikko "Tar\u00ADkastus" :nimi :tyyppi
        :pakollinen? true
        :tyyppi :valinta
        :valinnat (tarkastustyypit-tekijalle (:tekija tarkastus))
        :valinta-nayta #(case %
                          :tiesto "Tiestötarkastus"
                          :talvihoito "Kelitarkastus"
                          :soratie "Soratien tarkastus"
                          :laatu "Laaduntarkastus"
                          :pistokoe "Pistokoe"
                          "- valitse -")
        :palstoja 1}

       {:tyyppi :tierekisteriosoite
        :nimi :tr
        :pakollinen? true
        :sijainti (r/wrap (:sijainti tarkastus)
                          #(swap! tarkastus-atom assoc :sijainti %))}
       
       {:otsikko "Tar\u00ADkastaja" :nimi :tarkastaja
        :tyyppi :string :pituus-max 256
        :pakollinen? true
        :validoi [[:ei-tyhja "Anna tarkastajan nimi"]]
        :palstoja 1}
       
       (case (:tyyppi tarkastus)
         :talvihoito (talvihoitomittaus)
         :soratie (soratiemittaus)
         nil)
       
       {:otsikko "Havain\u00ADnot" :nimi :havainnot
        :koko [80 :auto]
        :tyyppi :text
        :palstoja 2}

       

       {:otsikko     "Liitteet" :nimi :liitteet
        :tyyppi :komponentti
        :komponentti [liitteet/liitteet {:urakka-id         (:id @nav/valittu-urakka)
                                         :uusi-liite-atom   (r/wrap (:uusi-liite tarkastus)
                                                                    #(swap! tarkastus-atom assoc :uusi-liite %))
                                         :uusi-liite-teksti "Lisää liite tarkastukseen"}
                      (:liitteet tarkastus)]}]
      
      tarkastus]]))


(defn tarkastukset
  "Tarkastuksien pääkomponentti"
  []
  (komp/luo

    ;; Laitetaan laadunseurannan karttataso päälle kun ollaan
    ;; tarkastuslistauksessa
    (komp/lippu tarkastukset-kartalla/karttataso-tarkastukset)
    (komp/kuuntelija :tarkastus-klikattu #(reset! tarkastukset/valittu-tarkastus %2))
    (komp/ulos (kartta/kuuntele-valittua! tarkastukset/valittu-tarkastus))
    (komp/sisaan-ulos #(do
                        (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
                        (nav/vaihda-kartan-koko! :M))
                      #(nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko))

    (fn []
      [:span.tarkastukset
       [kartta/kartan-paikka]
       (if @tarkastukset/valittu-tarkastus
         [tarkastus tarkastukset/valittu-tarkastus]
         [tarkastuslistaus])])))
