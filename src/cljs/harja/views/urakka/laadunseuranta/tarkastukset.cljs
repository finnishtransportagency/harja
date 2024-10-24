(ns harja.views.urakka.laadunseuranta.tarkastukset
  (:require [reagent.core :as r]
            [cljs.core.async :refer [<!]]

            [harja.pvm :as pvm]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.laadunseuranta.tarkastukset :as tiedot]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.tiedot.istunto :as istunto]
            [harja.domain.tierekisteri :as tierekisteri]
            [harja.domain.urakka :as u-domain]

            [harja.ui.grid :as grid]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.ui.kentat :refer [tee-otsikollinen-kentta]]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.liitteet :as liitteet]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.valinnat :as ui-valinnat]
            [harja.views.kartta :as kartta]
            [harja.views.urakka.valinnat :as valinnat]

            [harja.tiedot.urakka.laadunseuranta.tarkastukset-kartalla :as tarkastukset-kartalla]
            [harja.tiedot.urakka.laadunseuranta.laatupoikkeamat :as tiedot-laatupoikkeamat]
            [clojure.string :as str]
            [harja.tiedot.navigaatio.reitit :as reitit]
            [harja.asiakas.kommunikaatio :as k]
            [harja.fmt :as fmt]
            [harja.tiedot.urakka.laadunseuranta :as laadunseuranta]
            [harja.domain.roolit :as roolit]
            [harja.tiedot.kartta :as kartta-tiedot]
            [harja.domain.laadunseuranta.tarkastus :as domain-tarkastukset]
            [harja.domain.yllapitokohde :as yllapitokohde-domain]
            [harja.ui.viesti :as viesti])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def +tarkastustyyppi-hoidolle+ [:tieturvallisuus :tiesto :talvihoito :soratie :laatu])
(def +tarkastustyyppi-yllapidolle+ [:katselmus :pistokoe :vastaanotto :takuu])

(defn hoito-tarkastustyypit []
  +tarkastustyyppi-hoidolle+)

(defn tarkastustyypit-hoidon-tekijalle [tekija]
  (case tekija
    :tilaaja [:laatu]
    :urakoitsija [:tieturvallisuus :tiesto :talvihoito :soratie]
    (hoito-tarkastustyypit)))

(defn tarkastustyypit-urakkatyypille-ja-tekijalle [urakkatyyppi tekija]
  (if (some #(= urakkatyyppi %) [:paallystys :paikkaus :tiemerkinta])
    ;; FIXME Ei vielä varmaa tietoa meneekö tarkastustyypit ylläpidon puolella näin vai pitääkö
    ;; tekijäkin huomioida. Toistaiseksi mennään tällä.
    +tarkastustyyppi-yllapidolle+
    (tarkastustyypit-hoidon-tekijalle tekija)))

(defn tarkastustyypit-urakkatyypille [urakkatyyppi]
  (if (some #(= urakkatyyppi %) [:paallystys :paikkaus :tiemerkinta])
    +tarkastustyyppi-yllapidolle+
    (hoito-tarkastustyypit)))

(defn uusi-tarkastus [urakkatyyppi]
  {:uusi? true
   :aika (pvm/nyt)
   :tyyppi (if (u-domain/vesivaylaurakkatyyppi? urakkatyyppi) :katselmus @tiedot/tarkastustyyppi)
   :tarkastaja @istunto/kayttajan-nimi
   :nayta-urakoitsijalle (or (= (roolit/osapuoli @istunto/kayttaja) :urakoitsija) (nav/yllapitourakka-valittu?))
   :laadunalitus false})

(defn valitse-tarkastus [tarkastus-id]
  (go
    (reset! tiedot/valittu-tarkastus
            (<! (tiedot/hae-tarkastus (:id @nav/valittu-urakka) tarkastus-id)))))

(defn tarkastuslistaus
  "Tarkastuksien listauskomponentti"
  []
   (komp/luo
     (komp/sisaan #(when (u-domain/vesivaylaurakka? @nav/valittu-urakka)
                     ;; VV-urakassa nämä filtterit eivät saa vaikuttaa.
                     (reset! tiedot/tienumero nil)
                     (reset! tiedot/tarkastustyyppi nil)))
     (fn []
       (let [urakka @nav/valittu-urakka
             urakkatyyppi (:tyyppi urakka)
             vesivaylaurakka? (u-domain/vesivaylaurakka? urakka)
             tarkastukset (reverse (sort-by :aika @tiedot/urakan-tarkastukset))]
         [:div.tarkastukset
          [ui-valinnat/urakkavalinnat {:urakka urakka}
           ^{:key "aikavali"}
           [valinnat/aikavali-nykypvm-taakse urakka tiedot/valittu-aikavali {:vayla-tyyli? true}]

           (when-not vesivaylaurakka?
             ^{:key "tyyppi"}
             [tee-otsikollinen-kentta
              {:otsikko "Tyyppi"
               :kentta-params {:tyyppi :valinta
                               :valinnat (conj (tarkastustyypit-urakkatyypille urakkatyyppi) nil)
                               :valinta-nayta #(or (tiedot/+tarkastustyyppi->nimi+ %) "Kaikki")}
               :arvo-atom tiedot/tarkastustyyppi}])

           ^{:key "tarkastusvalinnat"}
           [tee-otsikollinen-kentta
            {:otsikko "Näytä"
             :kentta-params {:tyyppi :valinta
                             :valinnat tiedot/+naytettevat-tarkastukset-valinnat+
                             :valinta-nayta second}
             :arvo-atom tiedot/naytettavat-tarkastukset}]

           ^{:key "tekija"}
           [tee-otsikollinen-kentta
            {:otsikko "Tekijä"
             :kentta-params {:tyyppi :valinta
                             :valinnat tiedot/+tarkastuksen-tekija-valinnat+
                             :valinta-nayta second}
             :arvo-atom tiedot/tarkastuksen-tekija}]

           (when-not vesivaylaurakka?
             ^{:key "tienumero"}
             [valinnat/tienumero tiedot/tienumero])

           ^{:key "urakkatoiminnot"}
           [ui-valinnat/urakkatoiminnot {:urakka urakka}
            (let [oikeus? (oikeudet/voi-kirjoittaa?
                            oikeudet/urakat-laadunseuranta-tarkastukset
                            (:id urakka))]
              (yleiset/wrap-if
                (not oikeus?)
                [yleiset/tooltip {} :%
                 (oikeudet/oikeuden-puute-kuvaus :kirjoitus oikeudet/urakat-laadunseuranta-tarkastukset)]
                ^{:key "uusi-tarkastus"}
                [napit/uusi "Uusi tarkastus"
                 #(reset! tiedot/valittu-tarkastus (uusi-tarkastus urakkatyyppi))
                 {:disabled (not oikeus?)}]))]]

          [grid/grid
           {:otsikko "Tarkastukset"
            :tyhja (if (nil? @tiedot/urakan-tarkastukset)
                     [yleiset/ajax-loader "Tarkastuksia ladataan"]
                     "Ei tarkastuksia")
            :rivi-klikattu #(valitse-tarkastus (:id %))
            :jarjesta :aika
            :max-rivimaara 500
            :max-rivimaaran-ylitys-viesti
            "Tarkastuksia yli 500, tarkenna aikaväliä tai muita hakuehtoja."}

           [{:otsikko "Pvm ja aika"
             :tyyppi :pvm-aika :fmt pvm/pvm-aika :leveys 1
             :nimi :aika}

            {:otsikko "Tyyppi"
             :nimi :tyyppi
             :fmt tiedot/+tarkastustyyppi->nimi+
             :leveys 1}

            (when (or (= :paallystys urakkatyyppi)
                    (= :paikkaus urakkatyyppi)
                    (= :tiemerkinta urakkatyyppi))
              {:otsikko "Koh\u00ADde" :nimi :kohde :leveys 2
               :hae (fn [rivi]
                      (yllapitokohde-domain/yllapitokohde-tekstina {:kohdenumero (get-in rivi [:yllapitokohde :numero])
                                                                    :nimi (get-in rivi [:yllapitokohde :nimi])}))})
            (when-not (u-domain/vesivaylaurakka? urakka)
              {:otsikko "Tieosoite"
               :nimi :tr
               :leveys 2
               :fmt tierekisteri/tierekisteriosoite-tekstina})
            {:otsikko "Havainnot"
             :nimi :havainnot
             :leveys 4
             :tyyppi :komponentti
             :komponentti (fn [rivi]
                            (let [havainnot (:havainnot rivi)
                                  havainnot-max-pituus 50
                                  havainnot-rajattu (if (> (count havainnot) havainnot-max-pituus)
                                                      (str (.substring havainnot 0 havainnot-max-pituus) "...")
                                                      havainnot)
                                  vakiohavainnot (domain-tarkastukset/formatoi-vakiohavainnot (:vakiohavainnot rivi))
                                  talvihoitomittaukset (domain-tarkastukset/formatoi-talvihoitomittaukset (:talvihoitomittaus rivi))
                                  soratiemittaukset (domain-tarkastukset/formatoi-soratiemittaukset (:soratiemittaus rivi))]
                              [:ul.tarkastuksen-havaintolista
                               (when-not (str/blank? vakiohavainnot)
                                 [:li.tarkastuksen-vakiohavainnot vakiohavainnot])
                               (when-not (str/blank? talvihoitomittaukset)
                                 [:li.tarkastuksen-talvihoitomittaukset talvihoitomittaukset])
                               (when-not (str/blank? soratiemittaukset)
                                 [:li.tarkastuksen-soratiemittaukset soratiemittaukset])
                               (when-not (str/blank? havainnot-rajattu)
                                 [:li.tarkastuksen-havainnot havainnot-rajattu])]))}
            {:otsikko "Liit\u00ADteet" :nimi :liitteet :leveys 1 :tyyppi :komponentti
             :komponentti (fn [rivi]
                            (liitteet/liitteet-numeroina (:liitteet rivi)))}]
           tarkastukset]]))))

(defn talvihoitomittaus []
  (lomake/ryhma
    {:otsikko "Talvihoitomittaus"
     :rivi? true}
    {:otsikko "Lumi" :tyyppi :numero :yksikko "cm"
     :nimi :lumimaara
     :validoi [[:rajattu-numero-tai-tyhja 0 100 "Arvon tulee olla välillä 0-100"]]
     :hae (comp :lumimaara :talvihoitomittaus) :aseta #(assoc-in %1 [:talvihoitomittaus :lumimaara] %2)}
    {:otsikko "Tasaisuus" :tyyppi :numero :yksikko "cm"
     :nimi :talvihoito-tasaisuus
     :validoi [[:rajattu-numero-tai-tyhja 0 100 "Arvon tulee olla välillä 0-100"]]
     :hae (comp :tasaisuus :talvihoitomittaus) :aseta #(assoc-in %1 [:talvihoitomittaus :tasaisuus] %2)}
    {:otsikko "Kitka" :tyyppi :numero
     :nimi :kitka
     :validoi [[:rajattu-numero-tai-tyhja 0.01 0.99 "Arvon tulee olla välillä 0.01-0.99"]]
     :hae (comp :kitka :talvihoitomittaus) :aseta #(assoc-in %1 [:talvihoitomittaus :kitka] %2)}
    {:otsikko "Ilma" :tyyppi :numero :yksikko "\u2103"
     :validoi [#(when-not (<= -55 %1 55)
                  "Anna lämpotila välillä -55 \u2103 \u2014 +55 \u2103")]
     :nimi :lampotila_ilma
     :hae (comp :ilma :lampotila :talvihoitomittaus) :aseta #(assoc-in %1 [:talvihoitomittaus :lampotila :ilma] %2)}
    {:otsikko "Tie" :tyyppi :numero :yksikko "\u2103"
     :validoi [#(when-not (<= -55 %1 55)
                  "Anna lämpotila välillä -55 \u2103 \u2014 +55 \u2103")]
     :nimi :lampotila_tie
     :hae (comp :tie :lampotila :talvihoitomittaus) :aseta #(assoc-in %1 [:talvihoitomittaus :lampotila :tie] %2)}))

(defn soratiemittaus []
  (let [kuntoluokka (fn [arvo _]
                      (when (and arvo (not (<= 1 arvo 5)))
                        "Anna arvo 1 - 5"))
        prosentti (fn [arvo _]
                    (when (and arvo (not (<= 0 arvo 100)))
                      "Anna arvo 0 - 100"))]
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

                  {:otsikko "Sivukalt." :tyyppi :numero :yksikko "%"
                   :nimi :sivukaltevuus :palstoja 1 :validoi [prosentti]
                   :hae (comp :sivukaltevuus :soratiemittaus) :aseta #(assoc-in %1 [:soratiemittaus :sivukaltevuus] %2)}

                  {:otsikko "Soratiehoitoluokka" :tyyppi :valinta
                   :nimi :hoitoluokka :palstoja 1
                   :hae (comp :hoitoluokka :soratiemittaus) :aseta #(assoc-in %1 [:soratiemittaus :hoitoluokka] %2)
                   :valinnat [1 2 3]})))

(defn avaa-tarkastuksen-laatupoikkeama [laatupoikkeama-id]
  (reset! tiedot-laatupoikkeamat/valittu-laatupoikkeama-id laatupoikkeama-id)
  (reset! (reitit/valittu-valilehti-atom :laadunseuranta) :laatupoikkeamat))

(defn siirtymanapin-vihjeteksti [tarkastus]
  (let [huom-teksti (when (roolit/tilaajan-kayttaja? @istunto/kayttaja)
                      " HUOM! Laatupoikkeamat näkyvät aina urakoitsijalle")]
    (str
      (cond
        (:laatupoikkeamaid tarkastus)
        "Tallentaa muutokset ja avaa tarkastuksen pohjalta luodun laatupoikkeaman."

        (not (:laatupoikkeamaid tarkastus))
        "Tallentaa muutokset ja kirjaa tarkastuksen pohjalta uuden laatupoikkeaman.")
      huom-teksti)))

(defn tarkastuslomake [tarkastus-atom yllapitokohteet]
  (let [valittu-urakka @nav/valittu-urakka
        urakka-id (:id valittu-urakka)
        urakkatyyppi (:tyyppi valittu-urakka)
        tarkastus @tarkastus-atom
        jarjestelmasta? (:jarjestelma tarkastus)
        voi-kirjoittaa? (oikeudet/voi-kirjoittaa? oikeudet/urakat-laadunseuranta-tarkastukset
                                                  urakka-id)
        voi-muokata? (and voi-kirjoittaa?
                          (not jarjestelmasta?))
        kohde-muuttui? (fn [vanha uusi] (not= vanha uusi))
        yllapitokohdeurakka? @tiedot-urakka/yllapitokohdeurakka?
        yllapidon-palvelusopimus? (tiedot-urakka/yllapidon-palvelusopimus? valittu-urakka)
        vesivaylaurakka? (u-domain/vesivaylaurakkatyyppi? urakkatyyppi)]
    (if (and yllapitokohdeurakka? (nil? yllapitokohteet))
      [yleiset/ajax-loader "Ladataan..."]
      [:div.tarkastus
       [napit/takaisin "Takaisin tarkastusluetteloon" #(reset! tarkastus-atom nil)]

       (when (and jarjestelmasta?
                  (not (:nayta-urakoitsijalle tarkastus))
                  (oikeudet/on-muu-oikeus? "aseta-näkyviin-urakoitsijalle"
                                           oikeudet/urakat-laadunseuranta-tarkastukset
                                           urakka-id @istunto/kayttaja))
         [:div {:style {:margin-top "1em" :margin-bottom "1em"}}
          [napit/yleinen-ensisijainen "Näytä tarkastus urakoitsijalle"
           (fn []
             (go (reset! tiedot/tarkastuksen-avaaminen-urakoitsijalle-kaynnissa? true)
                 (let [vastaus (<! (tiedot/aseta-tarkastus-nakymaan-urakoitsijalle urakka-id (:id tarkastus)))]
                   (reset! tiedot/tarkastuksen-avaaminen-urakoitsijalle-kaynnissa? false)
                   (if (k/virhe? vastaus)
                     (viesti/nayta! "Tarkastuksen asettaminen näkyviin urakoitsijalle epäonnistui." :danger)
                     (reset! tarkastus-atom vastaus)))))
           {:tallennus-kaynnissa? @tiedot/tarkastuksen-avaaminen-urakoitsijalle-kaynnissa?
            :disabled @tiedot/tarkastuksen-avaaminen-urakoitsijalle-kaynnissa?}]])

       [lomake/lomake
        {:otsikko (if (:id tarkastus) "Muokkaa tarkastuksen tietoja" "Uusi tarkastus")
         :muokkaa! #(let [uusi-tarkastus
                          (if (kohde-muuttui? (:yllapitokohde @tarkastus-atom) (:yllapitokohde %))
                            (tiedot-laatupoikkeamat/paivita-yllapitokohteen-tr-tiedot % yllapitokohteet)
                            %)]
                      (reset! tarkastus-atom uusi-tarkastus))
         :voi-muokata? voi-muokata?
         :footer-fn (fn [tarkastus]
                      (when voi-kirjoittaa?
                        [napit/palvelinkutsu-nappi
                         "Tallenna tarkastus"
                         (fn []
                           (tiedot/tallenna-tarkastus
                             (:id valittu-urakka)
                             (lomake/ilman-lomaketietoja tarkastus)
                             urakkatyyppi))
                         {:disabled (not (lomake/voi-tallentaa? tarkastus))
                          :kun-onnistuu (fn [tarkastus]
                                          (reset! tiedot/valittu-tarkastus nil)
                                          (tiedot/paivita-tarkastus-listaan! tarkastus))
                          :virheviesti "Tarkastuksen tallennus epäonnistui."
                          :ikoni (ikonit/tallenna)}]))}

        [(when jarjestelmasta?
           {:otsikko "Lähde" :nimi :luoja :tyyppi :string
            :hae (fn [rivi] (str "Järjestelmä (" (:kayttajanimi rivi) " / " (:organisaatio rivi) ")"))
            :muokattava? (constantly false)
            :vihje "Tietojärjestelmästä tulleen tiedon muokkaus ei ole sallittu."})


         {:otsikko "Pvm ja aika" :nimi :aika :tyyppi :pvm-aika :pakollinen? true
          :huomauta [[:urakan-aikana-ja-hoitokaudella]]}

         (when yllapitokohdeurakka?
           {:otsikko "Ylläpito\u00ADkohde" :tyyppi :valinta :nimi :yllapitokohde
            :palstoja 1
            :pakollinen? (not yllapidon-palvelusopimus?)
            :valinnat yllapitokohteet
            :jos-tyhja "Ei valittavia kohteita"
            :valinta-arvo :id
            :valinta-nayta (fn [arvo muokattava?]
                             (if arvo
                               (yllapitokohde-domain/yllapitokohde-tekstina
                                 arvo {:osoite {:tr-numero (:tr-numero arvo)
                                                :tr-alkuosa (:tr-alkuosa arvo)
                                                :tr-alkuetaisyys (:tr-alkuetaisyys arvo)
                                                :tr-loppuosa (:tr-loppuosa arvo)
                                                :tr-loppuetaisyys (:tr-loppuetaisyys arvo)}})
                               (if muokattava?
                                 "- Valitse kohde -"
                                 "")))
            :validoi (when-not yllapidon-palvelusopimus?
                       [[:ei-tyhja "Anna tarkastuksen kohde"]])})

         (if vesivaylaurakka?
           {:otsikko "Tar\u00ADkastus" 
            :nimi :tyyppi 
            :tyyppi :string
            :hae (constantly "Katselmus") :muokattava? (constantly false)}
           {:otsikko "Tar\u00ADkastus" 
            :nimi :tyyppi
            :pakollinen? true
            :tyyppi :valinta
            :valinnat (tarkastustyypit-urakkatyypille-ja-tekijalle urakkatyyppi (:tekija tarkastus))
            :valinta-nayta #(or (tiedot/+tarkastustyyppi->nimi+ %) "- valitse -")
            :palstoja 1})

         (if vesivaylaurakka?
           {:nimi :sijainti
            :otsikko "Sijainti"
            :tyyppi :sijaintivalitsin
            :paikannus? false
            :pakollinen? true
            ;; FIXME Paikannus olisi kiva, mutta ei toiminut turpoissa, joten ei toimine tässäkään
            :karttavalinta-tehty-fn #(swap! tarkastus-atom assoc :sijainti %)}
           {:tyyppi :tierekisteriosoite
            :nimi :tr
            :pakollinen? true
            :ala-nayta-virhetta-komponentissa? true
            :varoita [[:validi-tr "Reittiä ei saada tehtyä" [:sijainti]]]
            :sijainti (r/wrap (:sijainti tarkastus)
                              #(swap! tarkastus-atom assoc :sijainti %))})

         {:otsikko "Tar\u00ADkastaja"
          :nimi :tarkastaja
          :tyyppi :string :pituus-max 256
          :pakollinen? true
          :validoi [[:ei-tyhja "Anna tarkastajan nimi"]]
          :palstoja 1}

         (when (= :hoito urakkatyyppi)
           (case (:tyyppi tarkastus)
             :talvihoito (talvihoitomittaus)
             :soratie (soratiemittaus)
             nil))

         (when (and
                 (= :hoito urakkatyyppi)
                 (= :laatu (:tyyppi tarkastus)))
           (talvihoitomittaus))

         (when (and
                 (= :hoito urakkatyyppi)
                 (= :laatu (:tyyppi tarkastus)))
           (soratiemittaus))

         {:otsikko "Havain\u00ADnot"
          :nimi :havainnot
          :koko [80 :auto]
          :pakollinen? (when (:laadunalitus tarkastus) true)
          :tyyppi :text
          :palstoja 2
          :validoi (when (:laadunalitus tarkastus)
                     [[:ei-tyhja "Kirjaa laadunalituksen havainnot"]])}

         {:otsikko (when-not voi-muokata?
                     ;; Näytä otsikko näyttömuodossa
                     "Laadun alitus")
          :teksti "Laadun alitus"
          :nayta-rivina? true
          :nimi :laadunalitus
          :tyyppi :checkbox
          :palstoja 2
          :fmt fmt/totuus}

         (when (and (not= (roolit/osapuoli @istunto/kayttaja) :urakoitsija)
                    (not (and (nav/yllapitourakka-valittu?)
                              (= (:tyyppi tarkastus) :katselmus))))
           {:otsikko (when-not voi-muokata?
                       ;; Näytä otsikko näyttömuodossa
                       "Näytä urakoitsijalle")
            :teksti "Näytä urakoitsijalle"
            :nimi :nayta-urakoitsijalle
            :nayta-rivina? true
            :tyyppi :checkbox
            :palstoja 2
            :fmt fmt/totuus})

         (when (not (empty? (:vakiohavainnot tarkastus)))
           {:otsikko "Vakio\u00ADhavainnot"
            :nimi :vakiohavainnot
            :tyyppi :komponentti
            :komponentti (fn [_]
                           [:span (str/join ", " (:vakiohavainnot tarkastus))])
            :palstoja 2})

         (when (oikeudet/voi-lukea? oikeudet/urakat-liitteet urakka-id)
           {:otsikko "Liitteet" :nimi :liitteet
            :tyyppi :komponentti
            :komponentti (fn [_]
                           [liitteet/liitteet-ja-lisays urakka-id (:liitteet tarkastus)
                            {:uusi-liite-atom (r/wrap (:uusi-liite tarkastus)
                                                      #(swap! tarkastus-atom assoc :uusi-liite %))
                             :uusi-liite-teksti "Lisää liite tarkastukseen"
                             :salli-poistaa-lisatty-liite? true
                             :poista-lisatty-liite-fn #(swap! tarkastus-atom dissoc :uusi-liite)
                             :salli-poistaa-tallennettu-liite? true
                             :poista-tallennettu-liite-fn
                             (fn [liite-id]
                               (liitteet/poista-liite-kannasta
                                 {:urakka-id urakka-id
                                  :domain :tarkastus
                                  :domain-id (:id tarkastus)
                                  :liite-id liite-id
                                  :poistettu-fn (fn []
                                                  (swap! tarkastus-atom assoc :liitteet
                                                         (filter (fn [liite]
                                                                   (not= (:id liite) liite-id))
                                                                 (:liitteet tarkastus))))}))}])})
         (when voi-kirjoittaa?
           {:rivi? true
            :uusi-rivi? true
            :nimi :laatupoikkeama
            :tyyppi :komponentti
            :vihje (siirtymanapin-vihjeteksti tarkastus)
            :komponentti (fn [{tarkastus :data}]
                           [napit/palvelinkutsu-nappi
                            (if (:laatupoikkeamaid tarkastus) "Tallenna ja avaa laatupoikkeama" "Tallenna ja lisää laatupoikkeama")
                            (fn []
                              (go
                                (let [tarkastus (<! (tiedot/tallenna-tarkastus urakka-id tarkastus urakkatyyppi))
                                      tarkastus-ja-laatupoikkeama (if (k/virhe? tarkastus)
                                                                    tarkastus
                                                                    (<! (tiedot/lisaa-laatupoikkeama tarkastus)))]
                                  tarkastus-ja-laatupoikkeama)))
                            {:disabled (not (lomake/voi-tallentaa? tarkastus))
                             :kun-onnistuu (fn [tarkastus]
                                             (reset! tarkastus-atom nil)
                                             (avaa-tarkastuksen-laatupoikkeama (:laatupoikkeamaid tarkastus)))
                             :virheviesti "Tarkastuksen tallennus epäonnistui."
                             :ikoni (ikonit/livicon-arrow-right)
                             :luokka :nappi-toissijainen}])})]
        tarkastus]])))

(defn- vastaava-tarkastus [klikattu-tarkastus]
  ;; oletetaan että kartalla näkyvät tarkastukset ovat myös gridissä
  (some (fn [urakan-tarkastus]
          (when (= (:id urakan-tarkastus) (:id klikattu-tarkastus))
            urakan-tarkastus))
        @tiedot/urakan-tarkastukset))

(defn tarkastukset
  "Tarkastuksien pääkomponentti"
  []
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (komp/kuuntelija :tarkastus-klikattu #(reset! tiedot/valittu-tarkastus %2))
    (komp/ulos (kartta-tiedot/kuuntele-valittua! tiedot/valittu-tarkastus))
    (komp/sisaan-ulos #(do
                         (reset! tiedot/valittu-karttataso nil)
                         (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
                         (reset! tarkastukset-kartalla/karttataso-tarkastukset (and
                                                                                 (not @tarkastukset-kartalla/karttataso-tieturvallisuus-heatmap)
                                                                                 (not @tarkastukset-kartalla/karttataso-ei-kayty-tieturvallisuusverkko)))
                         (kartta-tiedot/kasittele-infopaneelin-linkit!
                           {:tarkastus {:toiminto (fn [klikattu-tarkastus] ;; asiat-pisteessa -asia joka on tyypiltään tarkastus
                                                    (reset! tiedot/valittu-tarkastus (vastaava-tarkastus klikattu-tarkastus)))
                                        :teksti "Valitse tarkastus"}})
                         (nav/vaihda-kartan-koko! :M))
                      #(do
                         (nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko)
                         (reset! tarkastukset-kartalla/karttataso-tarkastukset false)
                         (kartta-tiedot/kasittele-infopaneelin-linkit! nil)))
    (fn []
      [:span.tarkastukset
       (when @nav/kartta-nakyvissa?
         [ui-valinnat/segmentoitu-ohjaus
          [{:nimi :tarkastukset
            :teksti "Tarkastukset"
            :oletus? true}
           {:nimi :kayntimaarat
            :teksti "Käyntimäärät"
            :disabled? (not= :tieturvallisuus @tiedot/tarkastustyyppi)}
           {:nimi :ei-kayty
            :teksti "Ei käyty"
            :disabled? (not= :tieturvallisuus @tiedot/tarkastustyyppi)}]
          @tiedot/valittu-karttataso
          {:luokka [:margin-top-32
                    :margin-bottom-16]
           :kun-valittu #(do
                           (reset! tiedot/valittu-karttataso %)
                           (reset! tarkastukset-kartalla/karttataso-tarkastukset (and
                                                                                   (not= % :kayntimaarat)
                                                                                   (not= % :ei-kayty))))}])
       [kartta/kartan-paikka]
       (if @tiedot/valittu-tarkastus
         [tarkastuslomake tiedot/valittu-tarkastus @laadunseuranta/urakan-yllapitokohteet-lomakkeelle]
         [tarkastuslistaus])])))
