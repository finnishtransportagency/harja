(ns harja.views.kanavat.urakka.laadunseuranta.hairiotilanteet
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :refer [tuck]]

            [harja.tiedot.kanavat.urakka.laadunseuranta.hairiotilanteet :as tiedot]
            [harja.loki :refer [tarkkaile! log]]
            [harja.pvm :as pvm]

            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.ui.lomake :as lomake]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni tietoja]]
            [harja.ui.debug :refer [debug]]

            [harja.domain.kanavat.hairiotilanne :as hairiotilanne]
            [harja.domain.kanavat.kohde :as kohde]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.vesivaylat.materiaali :as materiaali]

            [harja.ui.valinnat :as valinnat]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.views.vesivaylat.urakka.materiaalit :as materiaali-view]
            [harja.ui.napit :as napit]
            [harja.fmt :as fmt]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.domain.kanavat.hairiotilanne :as hairio]
            [harja.ui.debug :as debug]
            [harja.domain.kayttaja :as kayttaja]
            [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]
            [harja.tiedot.kanavat.urakka.kanavaurakka :as kanavaurakka]
            [harja.domain.kanavat.kohteenosa :as kohteenosa]
            [harja.views.kartta.tasot :as tasot]
            [harja.views.kartta :as kartta]
            [harja.tiedot.kartta :as kartta-tiedot])
  (:require-macros
    [cljs.core.async.macros :refer [go]]
    [harja.makrot :refer [defc fnc]]
    [harja.tyokalut.ui :refer [for*]]))

(defn- suodattimet-ja-toiminnot [e! app]
  (let [valittu-urakka (get-in app [:valinnat :urakka])]
    [valinnat/urakkavalinnat {:urakka valittu-urakka}
     ^{:key "urakkavalinnat"}
     [:div
      [urakka-valinnat/urakan-sopimus-ja-hoitokausi-ja-aikavali
       valittu-urakka {:sopimus {:optiot {:kaikki-valinta? true}}}]
      [valinnat/vikaluokka
       (r/wrap (get-in app [:valinnat :vikaluokka])
               (fn [uusi]
                 (e! (tiedot/->PaivitaValinnat {:vikaluokka uusi}))))
       hairio/vikaluokat+kaikki
       #(if % (hairio/fmt-vikaluokka %) "Kaikki")]
      [valinnat/korjauksen-tila
       (r/wrap (get-in app [:valinnat :korjauksen-tila])
               (fn [uusi]
                 (e! (tiedot/->PaivitaValinnat {:korjauksen-tila uusi}))))
       hairio/korjauksen-tlat+kaikki
       #(if % (hairio/fmt-korjauksen-tila %) "Kaikki")]
      [valinnat/paikallinen-kaytto
       (r/wrap (get-in app [:valinnat :paikallinen-kaytto?])
               (fn [uusi]
                 (e! (tiedot/->PaivitaValinnat {:paikallinen-kaytto? uusi}))))
       [nil true false]
       #(if (some? %) (fmt/totuus %) "Kaikki")]
      [valinnat/numerovali
       (r/wrap (get-in app [:valinnat :odotusaika-h])
               (fn [uusi]
                 (e! (tiedot/->PaivitaValinnat {:odotusaika-h uusi}))))
       {:otsikko "Odotusaika (h)"
        :vain-positiivinen? true}]
      [valinnat/numerovali
       (r/wrap (get-in app [:valinnat :korjausaika-h])
               (fn [uusi]
                 (e! (tiedot/->PaivitaValinnat {:korjausaika-h uusi}))))
       {:otsikko "Korjausaika (h)"
        :vain-positiivinen? true}]]
     ^{:key "urakkatoiminnot"}
     [valinnat/urakkatoiminnot {:urakka valittu-urakka}
      (let [oikeus? (oikeudet/voi-kirjoittaa? oikeudet/urakat-laadunseuranta-hairiotilanteet (:id valittu-urakka))]
        ^{:key "lisaysnappi"}
        [napit/uusi "Lisää häiriötilanne"
         #(e! (tiedot/->LisaaHairiotilanne))
         {:disabled (not oikeus?)}])]]))

(defn- hairiolista [e! {:keys [hairiotilanteet hairiotilanteiden-haku-kaynnissa?] :as app}]
  [grid/grid
   {:otsikko (if (and (some? hairiotilanteet) hairiotilanteiden-haku-kaynnissa?)
               [ajax-loader-pieni "Päivitetään listaa"]
               "Häiriötilanteet")
    :tunniste ::hairiotilanne/id
    :tyhja (if (nil? hairiotilanteet)
             [ajax-loader "Haetaan häiriötilanteita"]
             "Häiriötilanteita ei löytynyt")
    :rivi-klikattu #(e! (tiedot/->ValitseHairiotilanne %))}
   [{:otsikko "Havaintoaika"
     :nimi
     ::hairiotilanne/havaintoaika
     :tyyppi :pvm-aika
     :fmt pvm/pvm-aika-opt
     :leveys 4}
    {:otsikko "Kohde"
     :nimi :hairiotilanteen-kohde
     :hae (juxt ::hairiotilanne/kohde ::hairiotilanne/kohteenosa)
     :tyyppi :string
     :fmt (fn [[kohde osa]] (kohde/fmt-kohde-ja-osa-nimi kohde osa))
     :leveys 10}
    {:otsikko "Vika\u00ADluokka" :nimi ::hairiotilanne/vikaluokka :tyyppi :string :leveys 6
     :fmt hairio/fmt-vikaluokka}
    {:otsikko "Syy" :nimi ::hairiotilanne/syy :tyyppi :string :leveys 6}
    {:otsikko "Odo\u00ADtus\u00ADaika (h)" :nimi ::hairiotilanne/odotusaika-h :tyyppi :numero :leveys 2}
    {:otsikko "Am\u00ADmat\u00ADti\u00ADlii\u00ADkenne lkm" :nimi ::hairiotilanne/ammattiliikenne-lkm :tyyppi :numero :leveys 2}
    {:otsikko "Hu\u00ADvi\u00ADlii\u00ADkenne lkm" :nimi ::hairiotilanne/huviliikenne-lkm :tyyppi :numero :leveys 2}
    {:otsikko "Kor\u00ADjaus\u00ADtoimenpide" :nimi ::hairiotilanne/korjaustoimenpide :tyyppi :string :leveys 10}
    {:otsikko "Kor\u00ADjaus\u00ADaika" :nimi ::hairiotilanne/korjausaika-h :tyyppi :numero :leveys 2}
    {:otsikko "Kor\u00ADjauk\u00ADsen tila" :nimi ::hairiotilanne/korjauksen-tila :tyyppi :string :leveys 3
     :fmt hairio/fmt-korjauksen-tila}
    {:otsikko "Paikal\u00ADlinen käyt\u00ADtö" :nimi ::hairiotilanne/paikallinen-kaytto?
     :tyyppi :string :fmt fmt/totuus :leveys 2}]
   hairiotilanteet])

(defn varaosataulukko [e! {:keys [materiaalit valittu-hairiotilanne] :as app}]
  (let [voi-muokata? (boolean (oikeudet/voi-kirjoittaa? oikeudet/urakat-laadunseuranta-hairiotilanteet (get-in app [:valinnat :urakka :id])))
        virhe-atom (r/wrap (:varaosat-taulukon-virheet valittu-hairiotilanne)
                           (fn [virhe] (e! (tiedot/->LisaaVirhe virhe))))
        sort-fn (fn [materiaalin-kirjaus]
                  (if (and (get-in materiaalin-kirjaus [:varaosa ::materiaali/nimi])
                           (nil? (:jarjestysnumero materiaalin-kirjaus)))
                    [nil (get-in materiaalin-kirjaus [:varaosa ::materiaali/nimi])]
                    [(:jarjestysnumero materiaalin-kirjaus) nil]))]
    [grid/muokkaus-grid
     {:voi-muokata? voi-muokata?
      :voi-lisata? false
      :voi-poistaa? (constantly voi-muokata?)
      :voi-kumota? false
      :virheet virhe-atom
      :piilota-toiminnot? false
      :tyhja "Ei varaosia"
      :otsikko "Varaosat"
      :muutos #(materiaali-view/hoida-varaosataulukon-yksikko %)}
     [{:otsikko "Varaosa"
       :nimi :varaosa
       :leveys 3
       :validoi [[:ei-tyhja "Tieto puuttuu"]]
       :tyyppi :valinta
       :valinta-nayta #(or (::materiaali/nimi %) "- Valitse varaosa -")
       :valinnat materiaalit}
      {:otsikko "Käytettävä määrä"
       :nimi :maara
       :leveys 3
       :validoi [[:ei-tyhja "Tieto puuttuu"]]
       :tyyppi :positiivinen-numero
       :kokonaisluku? true}
      {:otsikko "Yksikkö"
       :nimi :yksikko
       :leveys 1
       :muokattava? (constantly false)}]
     (r/wrap
       (zipmap (range)
               (sort-by sort-fn (::materiaali/materiaalit valittu-hairiotilanne)))
       #(e! (tiedot/->MuokkaaMateriaaleja (sort-by sort-fn (vals %)))))]))

(defn odottavan-liikenteen-kentat []
  (lomake/ryhma
    {:otsikko "Odottava liikenne"
     :rivi? true
     :uusi-rivi? true}
    {:otsikko "Odotusaika"
     :nimi ::hairiotilanne/odotusaika-h
     :tyyppi :positiivinen-numero
     :desimaalien-maara 2
     :yksikko-kentalle "h"}
    {:otsikko "Ammattiliikenne"
     :nimi ::hairiotilanne/ammattiliikenne-lkm
     :tyyppi :positiivinen-numero
     :kokonaisluku? true
     :yksikko-kentalle "h"}
    {:otsikko "Huviliikenne"
     :nimi ::hairiotilanne/huviliikenne-lkm
     :tyyppi :positiivinen-numero
     :kokonaisluku? true
     :yksikko-kentalle "h"}))

(defn korjauksen-kentat [e! app]
  (lomake/ryhma
    {:otsikko "Korjaus"
     :uusi-rivi? true}
    {:otsikko "Korjaustoimenpide"
     :nimi ::hairiotilanne/korjaustoimenpide
     :palstoja 2
     :tyyppi :text
     :koko [90 8]}
    (lomake/rivi
      {:tyyppi :positiivinen-numero
       :nimi ::hairiotilanne/korjausaika-h
       :yksikko-kentalle "h"
       :otsikko "Korjausaika"}
      {:otsikko "Korjauksen tila"
       :nimi ::hairiotilanne/korjauksen-tila
       :tyyppi :valinta
       :pakollinen? true
       :valinta-nayta #(or (:nimi %) "- Valitse korjauksen tila -")
       :valinta-arvo :arvo
       :valinnat [{:arvo :kesken
                   :nimi "Kesken"}
                  {:arvo :valmis
                   :nimi "Valmis"}]}
      {:tyyppi :checkbox
       :nimi ::hairiotilanne/paikallinen-kaytto?
       :teksti "Siirrytty paikalliskäyttöön"})
    {:nimi :varaosat
     :tyyppi :komponentti
     :palstoja 2
     :komponentti (fn [_]
                    [varaosataulukko e! app])}
    {:nimi :lisaa-varaosa
     :tyyppi :komponentti
     :uusi-rivi? true
     :komponentti (fn [_]
                    [napit/uusi "Lisää varaosa"
                     #(e! (tiedot/->LisaaMateriaali))
                     {:disabled (not (oikeudet/voi-kirjoittaa? oikeudet/urakat-laadunseuranta-hairiotilanteet (get-in app [:valinnat :urakka :id])))}])}))

(defn hairiolomake [e! {:keys [valittu-hairiotilanne valinnat tallennus-kaynnissa?] :as app} kohteet]
  [:div
   [napit/takaisin "Takaisin häiriölistaukseen"
    #(e! (tiedot/->TyhjennaValittuHairiotilanne))]
   [lomake/lomake
    {:otsikko "Uusi häiriötilanne"
     :voi-muokata? (oikeudet/voi-kirjoittaa? oikeudet/urakat-laadunseuranta-hairiotilanteet (get-in valinnat [:urakka :id]))
     :validoi-alussa? true
     :muokkaa! #(e! (tiedot/->AsetaHairiotilanteenTiedot %))
     :footer-fn (fn [hairiotilanne]
                  (let [oikeus? (oikeudet/voi-kirjoittaa? oikeudet/urakat-laadunseuranta-hairiotilanteet (get-in valinnat [:urakka :id]))]
                    [:div
                     [:div {:style {:width "100%"}}
                      [lomake/nayta-puuttuvat-pakolliset-kentat hairiotilanne]]
                     [napit/tallenna
                      "Tallenna"
                      #(e! (tiedot/->TallennaHairiotilanne hairiotilanne))
                      {:tallennus-kaynnissa? tallennus-kaynnissa?
                       :disabled (or
                                   (not oikeus?)
                                   (not (empty? (:varaosat-taulukon-virheet valittu-hairiotilanne)))
                                   (not (tiedot/voi-tallentaa? valittu-hairiotilanne))
                                   (not (lomake/voi-tallentaa? valittu-hairiotilanne)))}]

                     (when (not (nil? (::hairiotilanne/id valittu-hairiotilanne)))
                       [napit/poista
                        "Poista"
                        #(varmista-kayttajalta/varmista-kayttajalta
                           {:otsikko "Häiriötilanteen poistaminen"
                            :sisalto [:div "Haluatko varmasti poistaa häiriötilanteen?"]
                            :hyvaksy "Poista"
                            :toiminto-fn (fn [] (e! (tiedot/->PoistaHairiotilanne hairiotilanne)))
                            :disabled (not oikeus?)})])]))}
    (let [valittu-kohde-id (get-in valittu-hairiotilanne [::hairiotilanne/kohde ::kohde/id])
          valitun-kohteen-osat (::kohde/kohteenosat (kohde/kohde-idlla kohteet valittu-kohde-id))]
      [{:otsikko "Havaintoaika"
        :nimi ::hairiotilanne/havaintoaika
        :pakollinen? true
        :tyyppi :pvm-aika
        :fmt pvm/pvm-aika-opt}
       (lomake/ryhma
         {:otsikko "Sijainti tai kohde"}
         {:nimi ::hairiotilanne/sijainti
          :otsikko "Sijainti"
          :uusi-rivi? true
          :tyyppi :sijaintivalitsin
          :disabled? (not (nil? (::hairiotilanne/kohde valittu-hairiotilanne)))
          ;; Pitää tietää onko haku käynnissä vai ei, jotta voidaan estää kohteen valinta
          ;; haun aikana
          :paikannus-kaynnissa?-atom (r/wrap (:paikannus-kaynnissa? valittu-hairiotilanne)
                                             (fn [_]
                                               #(e! (tiedot/->KytkePaikannusKaynnissa))))
          :poista-valinta? true
          :karttavalinta-tehty-fn :kayta-lomakkeen-atomia}
         {:otsikko "Kohde"
          :disabled? (or (some? (::hairiotilanne/sijainti valittu-hairiotilanne))
                         (:paikannus-kaynnissa? valittu-hairiotilanne))
          :nimi ::hairiotilanne/kohde
          :tyyppi :valinta
          :uusi-rivi? true
          :valinta-nayta #(or (kohde/fmt-kohteen-nimi %) "Ei kohdetta")
          :valinnat (into [nil] kohteet)})
       (when (::hairiotilanne/kohde valittu-hairiotilanne)
         {:otsikko "Kohteen osa"
          :nimi ::hairiotilanne/kohteenosa
          :tyyppi :valinta
          :valinta-nayta #(or (kohteenosa/fmt-kohteenosa %) "Ei osaa")
          :valinnat (or (into [nil] valitun-kohteen-osat) [])})
       {:otsikko "Vikaluokka"
        :nimi ::hairiotilanne/vikaluokka
        :tyyppi :valinta
        :pakollinen? true
        :uusi-rivi? true
        :valinta-nayta #(or (:nimi %) "- Valitse vikaluokka -")
        :valinta-arvo :arvo
        :valinnat [{:arvo :sahkotekninen_vika
                    :nimi "Sähkötekninen vika"}
                   {:arvo :konetekninen_vika
                    :nimi "Konetekninen vika"}
                   {:arvo :liikennevaurio
                    :nimi "Liikennevaurio"}
                   {:arvo :ilkivalta
                    :nimi "Ilkivalta"}
                   {:arvo :sahkokatko
                    :nimi "Sähkökatko"}
                   {:arvo :muut_viat
                    :nimi "Muut viat"}]}
       {:otsikko "Häiriön syy"
        :nimi ::hairiotilanne/syy
        :palstoja 2
        :pakollinen? true
        :tyyppi :text
        :koko [90 8]
        :uusi-rivi? true}
       (odottavan-liikenteen-kentat)
       (korjauksen-kentat e! app)
       {:otsikko "Kuittaaja"
        :nimi ::hairiotilanne/kuittaaja
        :tyyppi :string
        :uusi-rivi? true
        :hae #(kayttaja/kokonimi (::hairiotilanne/kuittaaja %))
        :muokattava? (constantly false)}])
    valittu-hairiotilanne]])

(defn hairiotilanteet* [e! app]
  (komp/luo
    (komp/watcher tiedot/valinnat (fn [_ _ uusi]
                                    (e! (tiedot/->PaivitaValinnat uusi))))
    (komp/sisaan-ulos #(do (e! (tiedot/->NakymaAvattu))
                           (e! (tiedot/->PaivitaValinnat
                                 {:urakka @nav/valittu-urakka
                                  :sopimus-id (first @u/valittu-sopimusnumero)
                                  :aikavali @u/valittu-aikavali}))
                           (tasot/taso-paalle! :kan-kohteet)
                           (tasot/taso-paalle! :kan-hairiot)
                           (tasot/taso-pois! :organisaatio)
                           (kartta-tiedot/kasittele-infopaneelin-linkit!
                             {:kan-hairiotilanne {:toiminto (fn [ht]
                                                              (e! (tiedot/->ValitseHairiotilanne ht))
                                                              (kartta-tiedot/piilota-infopaneeli!))
                                                  :teksti "Avaa häiriötilanne"}}))
                      #(e! (tiedot/->NakymaSuljettu)
                           (tasot/taso-pois! :kan-kohteet)
                           (tasot/taso-pois! :kan-hairiot)
                           (tasot/taso-paalle! :organisaatio)
                           (kartta-tiedot/kasittele-infopaneelin-linkit! nil)))

    (fn [e! {valittu-hairiotilanne :valittu-hairiotilanne :as app}]
      @tiedot/valinnat                                      ;; Reaktio on luettava komponentissa, muuten se ei päivity
      [:span
       [kartta/kartan-paikka]
       [:div
        [debug/debug app]
        (if valittu-hairiotilanne
          [hairiolomake e! app @kanavaurakka/kanavakohteet]
          [:div
           [suodattimet-ja-toiminnot e! app]
           [hairiolista e! app]])]])))

(defn hairiotilanteet []
  [tuck tiedot/tila hairiotilanteet*])

