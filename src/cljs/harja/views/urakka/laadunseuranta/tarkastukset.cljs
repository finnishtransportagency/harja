(ns harja.views.urakka.laadunseuranta.tarkastukset
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<! >! chan]]

            [harja.pvm :as pvm]
            [harja.loki :refer [log]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.laadunseuranta.tarkastukset :as tarkastukset]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.tiedot.istunto :as istunto]
            [harja.domain.tierekisteri :as tierekisteri]

            [harja.ui.grid :as grid]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.ui.kentat :refer [tee-otsikollinen-kentta]]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.liitteet :as liitteet]
            [harja.ui.ikonit :as ikonit]
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
            [harja.domain.hoitoluokat :as hoitoluokat])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]))

(def +tarkastustyyppi-hoidolle+ [:tiesto :talvihoito :soratie :laatu])
(def +tarkastustyyppi-yllapidolle+ [:katselmus :pistokoe :vastaanotto :takuu])

(defn tarkastustyypit-hoidon-tekijalle [tekija]
  (case tekija
    :tilaaja [:laatu]
    :urakoitsija [:tiesto :talvihoito :soratie]
    +tarkastustyyppi-hoidolle+))

(defn tarkastustyypit-urakkatyypille-ja-tekijalle [urakkatyyppi tekija]
  (if (some #(= urakkatyyppi %) [:paallystys :paikkaus :tiemerkinta])
    ;; FIXME Ei vielä varmaa tietoa meneekö tarkastustyypit ylläpidon puolella näin vai pitääkö
    ;; tekijäkin huomioida. Toistaiseksi mennään tällä.
    +tarkastustyyppi-yllapidolle+
    (tarkastustyypit-hoidon-tekijalle tekija)))

(defn tarkastustyypit-urakkatyypille [urakkatyyppi]
  (if (some #(= urakkatyyppi %) [:paallystys :paikkaus :tiemerkinta])
    +tarkastustyyppi-yllapidolle+
    +tarkastustyyppi-hoidolle+))

(defn uusi-tarkastus []
  {:uusi? true
   :aika (pvm/nyt)
   :tarkastaja @istunto/kayttajan-nimi
   :nayta-urakoitsijalle (= (roolit/osapuoli @istunto/kayttaja) :urakoitsija)
   :laadunalitus false})

(defn valitse-tarkastus [tarkastus-id]
  (go
    (reset! tarkastukset/valittu-tarkastus
            (<! (tarkastukset/hae-tarkastus (:id @nav/valittu-urakka) tarkastus-id)))))

(defn- formatoi-talvihoitomittaukset
  [thm]
  (let [{kitka :kitka lumimaara :lumimaara tasaisuus :tasaisuus
         {tie :tie ilma :ilma} :lampotila} thm]
    (when (or kitka lumimaara tasaisuus)
      (str "Talvihoitomittaukset: "
           (str/replace
             (str/join ", "
                       (keep #(if (and (some? (val %))
                                       (not= "" (val %))) ;; tyhjä hoitoluokka pois
                                (if (= :lampotila (key %))
                                  (when (or tie ilma)
                                    (str (when tie (str "tie: " tie "°C"))
                                         (when (and tie ilma) ", ")
                                         (when ilma (str "ilma: " ilma "°C"))))
                                  (str (name (key %)) ": "
                                       (if (= :hoitoluokka (key %))
                                         (hoitoluokat/talvihoitoluokan-nimi-str (val %))
                                         (if (or
                                               (= :lumimaara (key %))
                                               (= :tasaisuus (key %)))
                                           (str (val %) "cm")
                                           (val %)))))
                                nil)
                             (select-keys
                               thm
                               [:hoitoluokka :kitka :lumimaara :tasaisuus :lampotila])))
             "lumimaara" "lumimäärä")))))

(defn- formatoi-soratiemittaukset
  [stm]
  (let [{tasaisuus :tasaisuus kiinteys :kiinteys polyavyys :polyavyys
         sivukaltevuus :sivukaltevuus hoitoluokka :hoitoluokka} stm]
    (when (or tasaisuus kiinteys polyavyys sivukaltevuus hoitoluokka)
      (str "Soratiemittaukset: "
           (str/replace
             (str/join ", "
                       (keep #(if (and (some? (val %))
                                       (not= "" (val %))) ;; tyhjä hoitoluokka pois
                                (str (name (key %)) ": " (val %))
                                nil)
                             (select-keys
                               stm
                               [:hoitoluokka :tasaisuus :kiinteys :polyavyys :sivukaltevuus])))
             "polyavyys" "pölyävyys")))))

(defn tarkastuslistaus
  "Tarkastuksien listauskomponentti"
  ([] (tarkastuslistaus {}))
  ([optiot]
   (fn [optiot]
     (let [urakka @nav/valittu-urakka
           tarkastukset (reverse (sort-by :aika @tarkastukset/urakan-tarkastukset))]
       [:div.tarkastukset

        [valinnat/aikavali-nykypvm-taakse urakka tarkastukset/valittu-aikavali]

        [tee-otsikollinen-kentta "Tyyppi"
         {:tyyppi :valinta :valinnat (conj (tarkastustyypit-urakkatyypille (:tyyppi urakka)) nil)
          :valinta-nayta #(or (tarkastukset/+tarkastustyyppi->nimi+ %) "Kaikki")}
         tarkastukset/tarkastustyyppi]

        [tee-otsikollinen-kentta "Näytä"
         {:tyyppi :valinta :valinnat tarkastukset/+naytettevat-tarkastukset-valinnat+
          :valinta-nayta second}
         tarkastukset/naytettavat-tarkastukset]
        [valinnat/tienumero tarkastukset/tienumero]

        (let [oikeus? (oikeudet/voi-kirjoittaa?
                        oikeudet/urakat-laadunseuranta-tarkastukset
                        (:id @nav/valittu-urakka))]
          (yleiset/wrap-if
            (not oikeus?)
            [yleiset/tooltip {} :%
             (oikeudet/oikeuden-puute-kuvaus :kirjoitus
                                             oikeudet/urakat-laadunseuranta-tarkastukset)]
            [napit/uusi "Uusi tarkastus"
             #(reset! tarkastukset/valittu-tarkastus (uusi-tarkastus))
             {:disabled (not oikeus?)
              :luokka "alle-marginia"}]))

        [grid/grid
         {:otsikko "Tarkastukset"
          :tyhja (if (nil? @tarkastukset/urakan-tarkastukset)
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
           :nimi :tyyppi :fmt tarkastukset/+tarkastustyyppi->nimi+ :leveys 1}

          (when (or (= :paallystys (:nakyma optiot))
                    (= :paikkaus (:nakyma optiot))
                    (= :tiemerkinta (:nakyma optiot)))
            {:otsikko "Koh\u00ADde" :nimi :kohde :leveys 2
             :hae (fn [rivi]
                    (tierekisteri/yllapitokohde-tekstina {:kohdenumero (get-in rivi [:yllapitokohde :numero])
                                                          :nimi (get-in rivi [:yllapitokohde :nimi])}))})
          {:otsikko "TR-osoite"
           :nimi :tr
           :leveys 2
           :fmt tierekisteri/tierekisteriosoite-tekstina}
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
                                vakiohavainnot (str/join ", " (:vakiohavainnot rivi))
                                talvihoitomittaukset (formatoi-talvihoitomittaukset (:talvihoitomittaus rivi))
                                soratiemittaukset (formatoi-soratiemittaukset (:soratiemittaus rivi))]
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
     :validoi [[:rajattu-numero-tai-tyhja nil 0 100 "Arvon tulee olla välillä 0-100"]]
     :hae (comp :lumimaara :talvihoitomittaus) :aseta #(assoc-in %1 [:talvihoitomittaus :lumimaara] %2)}
    {:otsikko "Tasaisuus" :tyyppi :numero :yksikko "cm"
     :nimi :talvihoito-tasaisuus
     :validoi [[:rajattu-numero-tai-tyhja nil 0 100 "Arvon tulee olla välillä 0-100"]]
     :hae (comp :tasaisuus :talvihoitomittaus) :aseta #(assoc-in %1 [:talvihoitomittaus :tasaisuus] %2)}
    {:otsikko "Kitka" :tyyppi :numero
     :nimi :kitka
     :validoi [[:rajattu-numero-tai-tyhja nil 0.01 0.99 "Arvon tulee olla välillä 0.01-0.99"]]
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

(defn tarkastuslomake [tarkastus-atom optiot]
  (let [urakka-id (:id @nav/valittu-urakka)
        urakkatyyppi (:tyyppi @nav/valittu-urakka)
        tarkastus @tarkastus-atom
        jarjestelmasta? (:jarjestelma tarkastus)
        voi-kirjoittaa? (oikeudet/voi-kirjoittaa? oikeudet/urakat-laadunseuranta-tarkastukset
                                                  urakka-id)
        voi-muokata? (and voi-kirjoittaa?
                          (not jarjestelmasta?))
        kohde-muuttui? (fn [vanha uusi] (not= vanha uusi))
        yllapitokohteet (:yllapitokohteet optiot)
        yllapitokohdeurakka? @tiedot-urakka/yllapitokohdeurakka?]
    (if (and yllapitokohdeurakka? (nil? yllapitokohteet))
      [yleiset/ajax-loader "Ladataan..."]
      [:div.tarkastus
       [napit/takaisin "Takaisin tarkastusluetteloon" #(reset! tarkastus-atom nil)]

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
                           (tarkastukset/tallenna-tarkastus (:id @nav/valittu-urakka) tarkastus (:nakyma optiot)))
                         {:disabled (not (lomake/voi-tallentaa? tarkastus))
                          :kun-onnistuu (fn [tarkastus]
                                          (reset! tarkastukset/valittu-tarkastus nil)
                                          (tarkastukset/paivita-tarkastus-listaan! tarkastus))
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
            :pakollinen? true
            :valinnat yllapitokohteet
            :jos-tyhja "Ei valittavia kohteita"
            :valinta-arvo :id
            :valinta-nayta (fn [arvo muokattava?]
                             (if arvo
                               (tierekisteri/yllapitokohde-tekstina
                                 arvo {:osoite {:tr-numero (:tr-numero arvo)
                                                :tr-alkuosa (:tr-alkuosa arvo)
                                                :tr-alkuetaisyys (:tr-alkuetaisyys arvo)
                                                :tr-loppuosa (:tr-loppuosa arvo)
                                                :tr-loppuetaisyys (:tr-loppuetaisyys arvo)}})
                               (if muokattava?
                                 "- Valitse kohde -"
                                 "")))
            :validoi [[:ei-tyhja "Anna laatupoikkeaman kohde"]]})

         {:otsikko "Tar\u00ADkastus" :nimi :tyyppi
          :pakollinen? true
          :tyyppi :valinta
          :valinnat (tarkastustyypit-urakkatyypille-ja-tekijalle urakkatyyppi (:tekija tarkastus))
          :valinta-nayta #(or (tarkastukset/+tarkastustyyppi->nimi+ %) "- valitse -")
          :palstoja 1}

         {:tyyppi :tierekisteriosoite
          :nimi :tr
          :pakollinen? true
          :ala-nayta-virhetta-komponentissa? true
          :validoi [[:validi-tr "Reittiä ei saada tehtyä" [:sijainti]]]
          :sijainti (r/wrap (:sijainti tarkastus)
                            #(swap! tarkastus-atom assoc :sijainti %))}

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

         (when (not= (roolit/osapuoli @istunto/kayttaja) :urakoitsija)
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
                           [liitteet/liitteet urakka-id (:liitteet tarkastus)
                            {:uusi-liite-atom (r/wrap (:uusi-liite tarkastus)
                                                      #(swap! tarkastus-atom assoc :uusi-liite %))
                             :uusi-liite-teksti "Lisää liite tarkastukseen"}])})
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
                                (let [tarkastus (<! (tarkastukset/tallenna-tarkastus urakka-id tarkastus (:nakyma optiot)))
                                      tarkastus-ja-laatupoikkeama (if (k/virhe? tarkastus)
                                                                    tarkastus
                                                                    (<! (tarkastukset/lisaa-laatupoikkeama tarkastus)))]
                                  tarkastus-ja-laatupoikkeama)))
                            {:disabled (not (lomake/voi-tallentaa? tarkastus))
                             :kun-onnistuu (fn [tarkastus]
                                             (reset! tarkastus-atom tarkastus)
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
        @tarkastukset/urakan-tarkastukset))

(defn tarkastukset
  "Tarkastuksien pääkomponentti"
  [optiot]
  (komp/luo
    (komp/lippu tarkastukset-kartalla/karttataso-tarkastukset)
    (komp/kuuntelija :tarkastus-klikattu #(reset! tarkastukset/valittu-tarkastus %2))
    (komp/ulos (kartta-tiedot/kuuntele-valittua! tarkastukset/valittu-tarkastus))
    (komp/sisaan-ulos #(do
                         (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
                         (kartta-tiedot/kasittele-infopaneelin-linkit!
                           {:tarkastus {:toiminto (fn [klikattu-tarkastus] ;; asiat-pisteessa -asia joka on tyypiltään tarkastus
                                                    (reset! tarkastukset/valittu-tarkastus (vastaava-tarkastus klikattu-tarkastus)))
                                        :teksti "Valitse tarkastus"}
                            })
                         (nav/vaihda-kartan-koko! :M))
                      #(do (nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko)
                           (kartta-tiedot/kasittele-infopaneelin-linkit! nil)))
    (fn [optiot]
      [:span.tarkastukset
       [kartta/kartan-paikka]
       (if @tarkastukset/valittu-tarkastus
         [tarkastuslomake tarkastukset/valittu-tarkastus
          (merge optiot
                 {:yllapitokohteet @laadunseuranta/urakan-yllapitokohteet-lomakkeelle})]
         [tarkastuslistaus optiot])])))
