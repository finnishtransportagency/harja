(ns harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-toteumat
  (:require [cljs.core.async :refer [<! timeout]]
            [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [clojure.set :as set]
            [clojure.string :as str]

            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.loki :refer [log]]

            [harja.domain.paikkaus :as paikkaus]
            [harja.domain.tierekisteri :as tierekisteri]
            [harja.domain.roolit :as roolit]
            [harja.domain.muokkaustiedot :as muokkaustiedot]

            [harja.tiedot.urakka.paikkaukset-toteumat :as tiedot]
            [harja.tiedot.urakka.paikkaukset-yhteinen :as yhteiset-tiedot]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-toteumalomake :as t-toteumalomake]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet :as t-paikkauskohteet]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.kartta :as kartta-tiedot]

            [harja.views.kartta :as kartta]
            [harja.views.kartta.tasot :as kartta-tasot]
            [harja.views.urakka.yllapitokohteet :as yllapitokohteet]
            [harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-yhteinen :as yhteinen-view]
            [harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-toteumalomake :as v-toteumalomake]
            [harja.ui.debug :as debug]
            [harja.ui.nakymasiirrin :as siirrin]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.grid :as grid]
            [harja.ui.kentat :as kentat]
            [harja.ui.komponentti :as komp]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.modal :as modal]
            [harja.ui.viesti :as viesti]
            [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]
            [harja.ui.lomake :as lomake]
            [harja.ui.sivupalkki :as sivupalkki]
            [harja.ui.valinnat :as valinnat]
            [harja.domain.tierekisteri :as tr-domain]
            [harja.asiakas.kommunikaatio :as komm]
            [harja.ui.liitteet :as liitteet])
  (:require-macros [harja.tyokalut.ui :refer [for*]]))


(defn- pinta-alojen-summa [paikkaukset tehty-koneellisesti?]
  (let [pinta-ala-avain (if tehty-koneellisesti?
                          :suirun-pinta-ala
                          ::paikkaus/pinta-ala)]
    (->> paikkaukset
         (map pinta-ala-avain)
         (reduce +))))

(defn- juoksumetri-summa [paikkaukset]
  (->> paikkaukset
       (map ::paikkaus/juoksumetri)
       (reduce +)))

(defn- kpl-summa [paikkaukset]
  (->> paikkaukset
       (map ::paikkaus/kpl)
       (reduce +)))

(defn- kokonaismassamenekki [pinta-ala massamaara]
  (when (and (pos? pinta-ala) (pos? massamaara))
    (/ (* 1000 massamaara)
      pinta-ala)))

(defn- massamaaran-summa [paikkaukset]
  (->> paikkaukset
       (map ::paikkaus/massamaara)
       (reduce +)))

(defn ilmoita-virheesta-modal
  "Modaali, jossa kerrotaan paikkaustoteumassa olevasta virheestä."
  [e! app]
  (let [paikkaukset (::paikkaus/paikkaukset (:modalin-paikkauskohde app))
        {::paikkaus/keys [kohde-id urakka-id nimi pinta-ala massamenekki tyomenetelma] :as paikkaus} (first paikkaukset)
        tyomenetelma-nimi (paikkaus/tyomenetelma-id->nimi tyomenetelma (get-in app [:valinnat :tyomenetelmat]))
        rivien-lkm (count paikkaukset)
        ;pinta-ala (* 0.01 (Math/round (* 100 (pinta-alojen-summa paikkaukset))))
        ;massamenekki (massamenekin-keskiarvo paikkaukset)
        lomakedata (:lomakedata app)]
    [modal/modal
     {:otsikko (str "Lähetä sähköposti")
      :luokka "ilmoita-virheesta-modal"
      :nakyvissa? true
      :sulje-fn #(e! (tiedot/->SuljeVirheModal))
      :footer [:div
               [napit/peruuta
                "Peruuta"
                #(e! (tiedot/->SuljeVirheModal))]

               [napit/palvelinkutsu-nappi
                "Ilmoita"
                #(tiedot/ilmoita-virheesta-paikkaustiedoissa (merge paikkaus
                                                                    {::paikkaus/saate (:saate lomakedata)
                                                                     ::paikkaus/tyomenetelma tyomenetelma-nimi
                                                                     ;::paikkaus/pinta-ala-summa pinta-ala
                                                                     ;::paikkaus/massamenekki-summa massamenekki
                                                                     ::paikkaus/rivien-lukumaara rivien-lkm
                                                                     ::paikkaus/kopio-itselle? (:kopio-itselle? lomakedata)
                                                                     ::paikkaus/muut-vastaanottajat (yleiset/sahkopostiosoitteet-str->set
                                                                                                      (:muut-vastaanottajat lomakedata))}))
                {:disabled (not (lomake/voi-tallentaa? lomakedata))
                 :luokka "nappi-myonteinen"
                 :ikoni (ikonit/check)
                 :kun-onnistuu (fn [vastaus]
                                 (e! (tiedot/->VirheIlmoitusOnnistui vastaus))
                                 (viesti/nayta! "Virheilmoitus lähetetty onnistuneesti!" :success viesti/viestin-nayttoaika-keskipitka))
                 :kun-virhe #(viesti/nayta! "Sähköpostiviestin lähetys epäonnistui. Kokeile hetken päästä uudelleen." :warning viesti/viestin-nayttoaika-keskipitka)}]]}

     [:div
      [:p
       "Harja lähettää sähköpostin urakoitsijalle: "
       [:span.bold (-> @tila/yleiset :urakka :urakoitsija :nimi)]]
      [lomake/lomake {:otsikko "Sähköpostiviestin sisältö"
                      :muokkaa! #(e! (tiedot/->PaivitaLomakedata %))}
       [{:nimi :tiedot :muokattava? (constantly false)
         :tyyppi :komponentti :palstoja 3
         :komponentti (fn []
                        [:div {:style {:padding "10px" :border "1px solid #f0f0f0"}}
                         [:p "Virhe kohteen " [:b nimi] " paikkaustoteumassa. Tarkista ja korjaa tiedot urakoitsijan järjestelmässä ja lähetä kohteen tiedot uudelleen Harjaan."]
                         [:h4 "Kohteen tiedot"]
                         ;; Aiemmin paikkaukset olivat vain levittäjällä tehtyjä ja urem paikkauksia
                         ;; Nyt työmenetelmiä on monia ja niiden yksiköt vaihtelevat. Joten rajoitetaan
                         ;; tietoa, mikä näytetään, jotta se on kaikille työmenetelmille yhdenmukainen

                         (yleiset/tietoja {:class "modal-ilmoita-virheesta-tiedot"}
                                          "Kohde" nimi
                                          "Työmenetelmä" tyomenetelma-nimi
                                          "Rivejä" rivien-lkm)
                         ;"Pinta-ala yht. " (str pinta-ala "m\u00B2")
                         ;"Massamenekki " (str massamenekki "kg/m\u00B2")
                         ])}
        varmista-kayttajalta/modal-muut-vastaanottajat
        (merge varmista-kayttajalta/modal-saateviesti {:otsikko "Lisätietoa virheestä"
                                                       :pakollinen? true
                                                       :validoi [[:ei-tyhja "Anna tarkempi kuvaus virheistä."]]})
        varmista-kayttajalta/modal-sahkopostikopio]
       lomakedata]
      [yleiset/vihje "Huom! Lähetetyn sähköpostiviestin sisältö tallennetaan Harjaan ja se saatetaan näyttää Harjassa paikkauskohteen tietojen yhteydessä."]]]))

(defn excel-tuonti-virhe-modal
  [e! {:keys [urem-excel-virheet] :as app}]
  (let [{urem-kokonaismassamaaravirhe "urem-kokonaismassamaaravirhe"
         validointivirheet "paikkausten-validointivirheet"
         paikkauskohteen-tila-virhe "paikkauskohteen-tila-virhe"
         excel-luku-virhe "excel-luku-virhe"} urem-excel-virheet]
    [modal/modal
     {:otsikko "Virheitä urapaikkausten tuonnissa excelillä"
      :nakyvissa? urem-excel-virheet
      :sulje-fn #(e! (tiedot/->SuljeUremLatausVirhe))
      :footer [:div
               [napit/sulje #(e! (tiedot/->SuljeUremLatausVirhe))]]}
     [:div
      (when validointivirheet
        [:<>
         [:p "Tuotua exceliä ei voitu lukea. Varmista, että käytät HARJAsta ladattua pohjaa, jonka sarakkeita A-P ei ole muokattu, ja paikkaukset alkavat riviltä 8."]
         [:<>
          [:br]
          (for* [[rivi virheet] validointivirheet]
            [:<>
             [:p "Rivi " rivi ":"]
             [:ul
              (for* [virhe virheet]
                [:li virhe])]])]])
      (when urem-kokonaismassamaaravirhe
        [:<>
         [:br]
         [:p urem-kokonaismassamaaravirhe]])
      (when paikkauskohteen-tila-virhe
        [:<>
         [:br]
         [:p paikkauskohteen-tila-virhe]])
      (when excel-luku-virhe
        [:<>
         [:br]
         [:p "Tuotu excel ei näytä oikeanlaiselta. Varmista, että käytät HARJAsta ladattua pohjaa, jonka sarakkeita A-Q tai otsikkorivejä ei ole muokattu."]])]]))


(def ohje-teksti-tilaajalle
  "Tarkista toteumat ja valitse Merkitse tarkistetuksi, jolloin tiettyjen työmenetelmien tiedot lähtevät YHA:an. Valitse Ilmoita virhe lähettääksesi virhetiedot sähköpostitse urakoitsijalle.")

(def ohje-teksti-urakoitsijalle
  "Tarkista toteumatiedoista mahdolliset tilaajan raportoimat virheet. Virheet on raportoitu myös sähköpostitse urakan vastuuhenkilölle.")

(def lahetyksen-tilan-teksti
  {"lahetetty" "Lähetetty YHA:an"
   "virhe" "Virhe lähetyksessä"
   "odottaa_vastausta" "Odottaa vastausta"
   nil "Ei lähetetty YHA:an"})

(defn- avaa-paikkauskohde-rivi-rn
  "Avataan paikkauskohteen rivi, jonka alle listataan paikkauskohteen toteumat. Samalla zoomataan
  kartta juuri klikattuun paikkauskohteeseen ja resetoidaan mahdollisesti valittu yksittäinen toteuma kartalta pois."
  [avain  e! kohde]
  (do
    ;; Zoomataan kartta valitun paikkaustoteuman toteumiin
    (let [_ (reset! tiedot/paikkaustoteumat-kartalla [kohde])
          _ (reset! tiedot/valitut-kohteet-atom #{})]
      (e! (yhteiset-tiedot/->AvaaToteumaOtsikko avain)))))

(defn urem? [tyomenetelma tyomenetelmat]
  (= (::paikkaus/tyomenetelma-lyhenne (paikkaus/id->tyomenetelma tyomenetelma tyomenetelmat)) "UREM"))

(defn- luo-uusi-toteuma-kohteelle
  [e! r]
  (let [paikkauskohde (::paikkaus/paikkauskohde r)
        toteumalomake (-> r
                          (set/rename-keys paikkaus/speqcl-avaimet->paikkaus)
                          (set/rename-keys paikkaus/speqcl-avaimet->tierekisteri)
                          (assoc :tyyppi :uusi-toteuma)
                          (assoc :paikkauskohde-id (::paikkaus/id paikkauskohde))
                          (dissoc ::paikkaus/paikkauskohde))]
    (do
      (e! (t-toteumalomake/->SuljeToteumaLomake))
      (e! (t-toteumalomake/->AvaaToteumaLomake toteumalomake {:alkupvm (::paikkaus/alkupvm paikkauskohde)
                                                              :loppupvm (::paikkaus/loppupvm paikkauskohde)
                                                              :tie (:tie paikkauskohde)
                                                              :aosa (:aosa paikkauskohde)
                                                              :losa (:losa paikkauskohde)
                                                              :yksikko (::paikkaus/yksikko paikkauskohde)})))))

(defn- avaa-toteuma-sivupalkkiin
  [e! tyomenetelmat paikkauskohde r]
  (let [_ (if (not (nil? (::paikkaus/sijainti r)))
            ;; Jos sijainti on annettu, zoomaa valitulle reitille
            (let [alue (harja.geo/extent (::paikkaus/sijainti r))]
              (do
                (reset! tiedot/valitut-kohteet-atom #{(::paikkaus/id r)})
                (js/setTimeout #(kartta-tiedot/keskita-kartta-alueeseen! alue) 200)))
            ;; Muussa tapauksessa poista valittu reitti kartalta (zoomaa kauemmaksi)
            (reset! tiedot/valitut-kohteet-atom #{}))
        toteumalomake (-> r
                          (set/rename-keys paikkaus/speqcl-avaimet->paikkaus)
                          (set/rename-keys paikkaus/speqcl-avaimet->tierekisteri))
        toteumalomake (merge toteumalomake
                             {:ajorata (::paikkaus/ajorata toteumalomake)
                              :kaista (::paikkaus/kaista toteumalomake)
                              :ajouravalit (::paikkaus/ajouravalit toteumalomake)
                              ;; Ajourat laitetaan tietokannassa vectoriin, jota toteumalomake ei tue. Otetaan niistä ensimmäinen
                              :ajourat (first (::paikkaus/ajourat toteumalomake))
                              :reunat (first (::paikkaus/reunat toteumalomake))})
        ;; Toteuman tyyppi
        tyyppi (if (urem? (:tyomenetelma toteumalomake) tyomenetelmat)
                 :toteuman-luku
                 :toteuman-muokkaus)
        pinta-ala (if (urem? (:tyomenetelma toteumalomake) tyomenetelmat)
                    (:suirun-pinta-ala toteumalomake)
                    (:pinta-ala toteumalomake))

        toteumalomake (-> toteumalomake
                          (assoc :pinta-ala pinta-ala)
                          (assoc :tyyppi tyyppi)
                          (assoc :paikkauskohde-nimi (::paikkaus/nimi r))
                          (assoc :paikkauskohde-tila (::paikkaus/paikkauskohteen-tila paikkauskohde))
                          (assoc :tyomenetelma (:tyomenetelma toteumalomake))
                          (assoc :kohteen-yksikko (::paikkaus/yksikko r))
                          (assoc :paikkauskohde-id (::paikkaus/paikkauskohde-id r))
                          (assoc :pituus (:suirun-pituus toteumalomake))
                          (dissoc ::paikkaus/paikkauskohde :sijainti
                                  :suirun-pituus ::paikkaus/nimi :suirun-pinta-ala
                                  ::paikkaus/paikkauskohde ::paikkaus/yksikko ::paikkaus/paikkauskohde-id
                                  ::paikkaus/reunat ::paikkaus/ajourat ::paikkaus/ajorata ::paikkaus/keskisaumat
                                  ::paikkaus/tienkohta-id ::paikkaus/ajouravalit))]
    (do
      (e! (t-toteumalomake/->SuljeToteumaLomake))
      ;; Tässä hallitaan app-statea olemassa olevien tuck eventtien kautta ja niiden app-staten päivitys
      ;;ottaa muutaman millisekunnin. Joten lisätään pieni viive, jotta saadaan varmasti päivitetty lomake auki
      (js/setTimeout #(e! (t-toteumalomake/->AvaaToteumaLomake toteumalomake nil)) 5))))

(defn- yksikko-avain [yksikko]
  (cond
    (= "t" yksikko) ::paikkaus/massamaara
    (= "jm" yksikko) ::paikkaus/juoksumetri
    (= "m2" yksikko) ::paikkaus/pinta-ala
    (= "kpl" yksikko) ::paikkaus/kpl
    :else ::paikkaus/massamaara))

(defn- skeema-menetelmalle
  "Taulukon skeema työmenetelmän perusteella. Levittimellä tehdyt ja UREM paikkaukset sisältävät enemmän dataa kuin
  muut, joten näille menetelmille lisätään useampia kenttiä."
  [tyomenetelma tyomenetelmat yksikko]
  (let [desimaalien-maara 2
        urapaikkaus? (urem? tyomenetelma tyomenetelmat)
        levittimella-tehty? (paikkaus/levittimella-tehty? {:tyomenetelma tyomenetelma} tyomenetelmat)
        excelista-tuotu?-fn #(= "excel" (::paikkaus/lahde %))]
    (concat
      [{:otsikko "Pvm." :leveys 6 :nimi ::paikkaus/pvm
        :hae #(select-keys % [::paikkaus/alkuaika ::paikkaus/loppuaika])
        :fmt #(fmt/pvm-vali [(::paikkaus/alkuaika %)
                             (::paikkaus/loppuaika %)]
                            false)}
       {:otsikko "Tie\u00ADosoi\u00ADte" :nimi ::tierekisteri/tieosoite
        :hae #(select-keys % [::tierekisteri/tie
                              ::tierekisteri/aosa
                              ::tierekisteri/aet
                              ::tierekisteri/losa
                              ::tierekisteri/let])
        :tyyppi :string :leveys 7
        :fmt (fn [r]
               (tr-domain/tr-osoite-moderni-fmt (::tierekisteri/tie r)
                                                (::tierekisteri/aosa r)
                                                (::tierekisteri/aet r)
                                                (::tierekisteri/losa r)
                                                (::tierekisteri/let r)))}
       {:otsikko "Ajor." :nimi ::paikkaus/ajorata :kokonaisluku? true
        :tyyppi :positiivinen-numero :leveys 3 :tasaa :oikea}]
      (when levittimella-tehty?
        [{:otsikko "Kais\u00ADta" :nimi ::paikkaus/kaista :kokonaisluku? true
          :tyyppi :positiivinen-numero :leveys 3 :tasaa :oikea}])
      (when (or urapaikkaus? levittimella-tehty?)
        [{:otsikko "Pit. (m)" :nimi :suirun-pituus :leveys 3 :tyyppi :positiivinen-numero
          :tasaa :oikea :kokonaisluku? true}
         {:otsikko "Leveys\u00AD (m)"
          :leveys 3 :tasaa :oikea
          :nimi ::paikkaus/leveys}])
      ;; Näytetään yksikkö muille paitsi uremille ja levittimellä tehdyille
      (when-not (or levittimella-tehty? urapaikkaus?)
        [{:otsikko yksikko
          :leveys 5
          :nimi (yksikko-avain yksikko)}])
      (when urapaikkaus?
        [{:otsikko "Ajo\u00ADurat" :nimi ::paikkaus/ajourat
          :leveys 3 :fmt t-paikkauskohteet/urapaikkauksen-sijainti-fmt :tasaa :oikea}
         {:otsikko "Reu\u00ADnat" :nimi ::paikkaus/reunat
          :leveys 3 :fmt t-paikkauskohteet/urapaikkauksen-sijainti-fmt :tasaa :oikea}
         {:otsikko "Urien välit" :nimi ::paikkaus/ajouravalit
          :leveys 3 :fmt t-paikkauskohteet/urapaikkauksen-sijainti-fmt :tasaa :oikea}
         {:otsikko "Keski\u00ADsauma" :nimi ::paikkaus/keskisaumat
          :leveys 3 :fmt t-paikkauskohteet/urapaikkauksen-sijainti-fmt :tasaa :oikea}])
      (when (or levittimella-tehty? urapaikkaus?)
        [{:otsikko "Massa\u00ADtyyp\u00ADpi"
          :leveys 7
          :nimi ::paikkaus/massatyyppi}
         {:otsikko "Raek." :leveys 3 :tasaa :oikea
          :nimi ::paikkaus/raekoko}
         {:otsikko "Kuula\u00ADmylly"
          :leveys 4
          :nimi ::paikkaus/kuulamylly}
         {:otsikko "m\u00B2"
          :leveys 5 :tasaa :oikea
          :fmt #(fmt/desimaaliluku-opt % desimaalien-maara)
          :hae #(if (excelista-tuotu?-fn %)
                  (::paikkaus/pinta-ala %)
                  (:suirun-pinta-ala %))
          :nimi :suirun-pinta-ala}
         {:otsikko "kg/m²"
          :leveys 5 :tasaa :oikea
          :nimi ::paikkaus/massamenekki}
         {:otsikko "t" :fmt #(fmt/desimaaliluku-opt % 3)
          :leveys 5 :tasaa :oikea
          :nimi ::paikkaus/massamaara}]))))

(defn- gridien-gridi
  [{:keys [ladataan-tietoja? otsikkokomponentti e! tyomenetelmat] :as app} paikkauskohteet gridien-tilat]
  (let [app (:app app)]
    [:div {:style {:display "flex"
                   :flex-direction "column"}}
     (if (and
           (not ladataan-tietoja?)
           (some? paikkauskohteet)
           (not (empty? paikkauskohteet)))
       (into [:<>]
             (map (fn [{paikkaukset ::paikkaus/paikkaukset :as kohde}]
                    (let [avain (::paikkaus/id kohde)]
                      [:<>
                       [otsikkokomponentti e! {:toteumien-maara (count paikkaukset)
                                               :ladataan-tietoja? ladataan-tietoja?
                                               :auki? (contains? (::paikkaus/toteumataulukon-tilat app) (::paikkaus/id kohde))
                                               :avaa! (r/partial avaa-paikkauskohde-rivi-rn avain e! kohde)
                                               :tyomenetelmat tyomenetelmat}
                        kohde]
                       (when (get gridien-tilat avain)
                         (when (> (count paikkaukset) 0)
                           [grid/grid
                            {:otsikko (when ladataan-tietoja?
                                        [yleiset/ajax-loader-pieni "Päivitetään listaa.."])
                             :salli-valiotsikoiden-piilotus? true
                             :valiotsikoiden-alkutila :kaikki-kiinni
                             :tunniste ::paikkaus/id
                             :sivuta 100
                             :reunaviiva? true
                             :tyhja (if ladataan-tietoja?
                                      [yleiset/ajax-loader "Haku käynnissä"]
                                      "Ei paikkauksia")
                             :rivi-klikattu (r/partial avaa-toteuma-sivupalkkiin e! tyomenetelmat kohde)}
                            (skeema-menetelmalle (::paikkaus/tyomenetelma (first paikkaukset)) tyomenetelmat (::paikkaus/yksikko kohde))
                            paikkaukset]))])))
             paikkauskohteet)
       (if ladataan-tietoja?
         [otsikkokomponentti e! {:ladataan-tietoja? ladataan-tietoja?} {}]
         [:div "Ei näytettäviä paikkauskohteita"]))]))

(defn- otsikkokomponentti
  "Paikkauskohteen tiedot wräpätään uille tässä otsikkokomponentissa. Toteutettu flexillä, että saadaan
  ui-speksin mukaisesti muotoiltua. Klikkaamalla paikkauskohde auki, listataan paikkauskohteelle kuuluvat
  paikkaustoteumat omaan taulukkoonsa."
  [_ _]
  (let [fmt-fn (fn [arvo]
                 (let [tila (case arvo
                              "tilattu" "kesken"
                              "hylatty" "hylätty"
                              nil ""
                              arvo)]
                   (str/capitalize tila)))
        class-skeema {"tilattu" "tila-ehdotettu"
                      "valmis" "tila-valmis"
                      "hylatty" "tila-hylatty"
                      "ehdotettu" "tila-ehdotettu"}]
    (fn [e! {:keys [avaa! auki? toteumien-maara tyomenetelmat ladataan-tietoja?]}
         {paikkaukset ::paikkaus/paikkaukset
          toteutus-alkuaika :toteutus-alkuaika
          toteutus-loppuaika :toteutus-loppuaika
          tarkistettu ::paikkaus/tarkistettu
          tyomenetelma ::paikkaus/tyomenetelma
          ilmoitettu-virhe ::paikkaus/ilmoitettu-virhe
          yha-lahetyksen-tila ::paikkaus/yhalahetyksen-tila
          paikkauskohteen-tila ::paikkaus/paikkauskohteen-tila
          yksikko ::paikkaus/yksikko :as paikkauskohde}]
      (let [urapaikkaus? (urem? tyomenetelma tyomenetelmat)
            tyomenetelma (or tyomenetelma (::paikkaus/tyomenetelma (first paikkaukset))) ; tarviikohan, en tiedä. jos vanhoilla kohteilla ei ole tuota kenttää?
            levittimella-tehty? (paikkaus/levittimella-tehty? paikkauskohde tyomenetelmat)
            urakoitsija-kayttajana? (t-paikkauskohteet/kayttaja-on-urakoitsija? (roolit/urakkaroolit @istunto/kayttaja (-> @tila/tila :yleiset :urakka :id)))
            tilaaja? (roolit/kayttaja-on-laajasti-ottaen-tilaaja?
                       (roolit/urakkaroolit @istunto/kayttaja (-> @tila/tila :yleiset :urakka :id))
                       @istunto/kayttaja)
            arvo-kpl (kpl-summa paikkaukset)
            arvo-pinta-ala (pinta-alojen-summa paikkaukset (or urapaikkaus? levittimella-tehty?))
            arvo-juoksumetri (juoksumetri-summa paikkaukset)
            arvo-massamaara (massamaaran-summa paikkaukset)
            arvo-massamenekki (kokonaismassamenekki arvo-pinta-ala arvo-massamaara)
            tilattu? (= "tilattu" paikkauskohteen-tila)]
        (if ladataan-tietoja?
          [:div.flex-row.venyta.otsikkokomponentti
           [:div.basis512.growfill
            [:div {:style {:text-align "center"}} "Odota, päivitetään tietoja "
             [yleiset/ajax-loader "Päivitetään listaa.."]]]]
          [:div
           ;; Luodaan ankkurilinkki
           [:div {:id (str "ankkuri-" (::paikkaus/id paikkauskohde))}]
           [:div.flex-row.venyta.otsikkokomponentti {:class (str "" (when (> toteumien-maara 0) " klikattava"))
                                                     :on-click #(when (> (count paikkaukset) 0) (avaa!))}
            [:div.basis48.nogrow
             (when (> toteumien-maara 0)
               (if auki?
                 [ikonit/navigation-ympyrassa :down]
                 [ikonit/navigation-ympyrassa :right]))]
            [:div.basis256.nogrow.shrink3
             [:h3.ei-marginia (str (::paikkaus/nimi paikkauskohde))]
             [:div.body-text.harmaa (str "Päivitetty: "
                                         (or (pvm/pvm-aika-klo-suluissa
                                               (::muokkaustiedot/muokattu paikkauskohde))
                                             "-"))]
             [yleiset/tila-indikaattori paikkauskohteen-tila {:fmt-fn fmt-fn
                                                              :class-skeema class-skeema
                                                              :luokka "body-text"}]]
            [:div.basis256.grow2.shrink3.rajaus
             [:div.body-text.strong.musta (str (paikkaus/tyomenetelma-id->nimi tyomenetelma tyomenetelmat))]
             [:div.body-text.harmaa (if (= 0 toteumien-maara)
                                      "Ei toteumia"
                                      (str toteumien-maara " toteuma" (when (not= 1 toteumien-maara) "a")))]
             [:div.body-text
              ;; Näytetään kellonaika vain urapaikkauksille
              (if urapaikkaus?
                (str (pvm/pvm-aika-klo-suluissa toteutus-alkuaika) " - " (pvm/pvm-aika-klo-suluissa toteutus-loppuaika))
                (str (pvm/pvm-opt toteutus-alkuaika) " - " (pvm/pvm-opt toteutus-loppuaika)))]]
            ;; Muut kuin urem ja levittimellä tehdyt
            (when (and (not urapaikkaus?) (not levittimella-tehty?))
              [:div.basis512.growfill.body-text.riviksi.shrink4.rajaus
               (when (= "m2" yksikko) [:span
                                       [:strong "Pinta-ala"]
                                       [:div (str (fmt/desimaaliluku-opt arvo-pinta-ala) " m²")]])
               (when (= "jm" yksikko) [:span
                                       [:strong "Jm"]
                                       [:div (str (fmt/desimaaliluku-opt arvo-juoksumetri) " jm")]])
               (when (= "t" yksikko) [:span
                                      [:strong "Ton\u00ADnia"]
                                      [:div (str (fmt/desimaaliluku-opt arvo-massamaara 2) " t")]])
               (when (= "kpl" yksikko) [:span
                                        [:strong "Kpl"]
                                        [:div (str (fmt/desimaaliluku-opt arvo-kpl) " kpl")]])])
            (when (or urapaikkaus? levittimella-tehty?)
              [:div.basis512.growfill.body-text.riviksi.shrink4.rajaus
               (when (not= 0 arvo-pinta-ala) [:span
                                              [:strong "Pinta-ala"]
                                              [:div (str (fmt/desimaaliluku-opt arvo-pinta-ala) " m²")]])
               (when (not= 0 arvo-massamenekki) [:span
                                                 [:strong "Massa\u00ADmenekki"]
                                                 [:div (str (fmt/desimaaliluku-opt arvo-massamenekki) " kg/m²")]])
               (when (not= 0 arvo-massamaara) [:span
                                               [:strong "Ton\u00ADnia"]
                                               [:div (str (fmt/desimaaliluku-opt arvo-massamaara 2) " t")]])])
            [:div.basis192.nogrow.body-text.shrink2.rajaus.items-start
             (if urapaikkaus?
               [liitteet/lataa-tiedosto
                {:urakka-id (-> @tila/tila :yleiset :urakka :id)
                 :paikkauskohde-id (::paikkaus/id paikkauskohde)}
                {:nappi-teksti "Tuo Excelillä"
                 :disabled? (not tilattu?)
                 :url "lue-urapaikkaukset-excelista"
                 :lataus-epaonnistui #(e! (tiedot/->UremPaikkausLatausEpaonnistui %))
                 :tiedosto-ladattu #(e! (tiedot/->UremPaikkausLatausOnnistui %))}]
               [napit/yleinen-toissijainen "Lisää toteuma"
                #(luo-uusi-toteuma-kohteelle
                   e!
                   {::paikkaus/tyomenetelma tyomenetelma
                    ::paikkaus/paikkauskohde paikkauskohde})
                {:disabled (not tilattu?)
                 :ikoni (ikonit/livicon-plus)
                 :luokka "nappi-reunaton"}])
             ;; Näytetään virheen ilmoitus vain tilaajalle
             (when tilaaja?
               [napit/yleinen-reunaton
                "Ilmoita virhe"
                #(e! (tiedot/->AvaaVirheModal paikkauskohde))
                {:ikoni (ikonit/harja-icon-action-send-email)
                 :disabled urakoitsija-kayttajana?}])
             (when ilmoitettu-virhe
               [:span.pieni-teksti
                [:div "Ilmoitettu virhe:"]
                [:p ilmoitettu-virhe]])]
            (let [tarkistettu? (boolean tarkistettu)]
              [:div.basis192.nogrow.shrink1.body-text
               {:class (str (when tarkistettu? "tarkistettu"))}
               (if tarkistettu?
                 [:div.body-text.harmaa [ikonit/livicon-check] "Tarkistettu"]
                 ;; Annetaan vain tilaajan merkitä kohde tarkistetuksi
                 (when tilaaja?
                   [napit/yleinen-reunaton "Merkitse tarkistetuksi"
                    #(e! (tiedot/->PaikkauskohdeTarkistettu
                           {::paikkaus/paikkauskohde paikkauskohde}))
                    {:disabled (or
                                 urakoitsija-kayttajana?
                                 tarkistettu?)
                     :ikoni (ikonit/livicon-check)}]))
               [:div.small-text.harmaa
                ;; Täsmätään vihjetekstin sisennys napin tekstiin
                (when (and (not tarkistettu) tilaaja?) {:style {:margin-left "34px"}})
                (cond
                  (= yha-lahetyksen-tila "lahetetty") "Lähetetty YHAan"

                  (true? tarkistettu?) "Tarkistettu"

                  (and
                    (false? tarkistettu?)
                    (paikkaus/pitaako-paikkauskohde-lahettaa-yhaan? (paikkaus/tyomenetelma-id->lyhenne tyomenetelma tyomenetelmat))) "Lähetys YHAan"

                  :else
                  "Ko. toimenpidettä ei lähetetä YHA:an")]])]])))))


(defn paikkaukset [e! {:keys [paikkaukset-grid
                              paikkauksien-haku-kaynnissa?
                              paikkauksien-haku-tulee-olemaan-kaynnissa?]
                       gridien-tilat ::paikkaus/toteumataulukon-tilat :as app}]
  [:div
   ;; Siirrytään valittuun otsikkoelementtiin vain jos niitä on yksi auki
   (when (and (::paikkaus/toteumataulukon-tilat app) (= 1 (count (::paikkaus/toteumataulukon-tilat app))))
     [:div.hidden (.setTimeout js/window
                               (fn []
                                 (siirrin/kohde-elementti-id (str "ankkuri-" (first (::paikkaus/toteumataulukon-tilat app)))))
                               150)])
   ;; Ei näytetä vihjeitä, mikäli paikkauksia ei löydetty
   (when-not (empty? paikkaukset-grid)
     (if (= :urakoitsija (roolit/osapuoli @istunto/kayttaja))
       [yleiset/vihje ohje-teksti-urakoitsijalle]
       [yleiset/vihje ohje-teksti-tilaajalle]))
   [gridien-gridi
    {:e! e!
     :app app
     :ladataan-tietoja? (or paikkauksien-haku-kaynnissa? paikkauksien-haku-tulee-olemaan-kaynnissa?)
     :ryhmittele #{::paikkaus/nimi ::paikkaus/tyomenetelma}
     :otsikkokomponentti otsikkokomponentti
     :tyomenetelmat (get-in app [:valinnat :tyomenetelmat])}
    paikkaukset-grid
    gridien-tilat]
   (when (:toteumalomake app)
     [sivupalkki/oikea
      {:leveys "600px" :jarjestys 1}
      [v-toteumalomake/toteumalomake e! app]])])

(defn- lataa-urem-excel []
  [yleiset/tiedoston-lataus-linkki
   "Lataa urapaikkaustoteumien tuonti-Excel"
   (str (when-not (komm/kehitysymparistossa-yhteiset? (.-host js/location)) "/harja") "/excel/harja_urapaikkaustoteumien_tuonti_pohja.xlsx")
   {:luokat ["pull-right" "margin-top-16"]}])

(defn view [e! app]
  [:div
   [:div.row.filtterit {:style {:padding "16px"}}
    [yhteinen-view/hakuehdot
     {:tila-atomi app
      :nakyma :toteumat
      :urakka (-> @tila/yleiset :urakka :id)
      :palvelukutsu-onnistui-fn #(e! (tiedot/->PaikkauksetHaettu %))}]]

   [debug/debug app]
   (when (:modalin-paikkauskohde app)
     [ilmoita-virheesta-modal e! app])
   [excel-tuonti-virhe-modal e! app]
   [:div.row
    [kartta/kartan-paikka]]
   ;; Näytetään Urapaikkaustoteumien tuonti-Excel vain ylläpito/päällystys tyyppisille urakoille.
   (when (= :paallystys (:arvo @nav/urakkatyyppi))
     [:div.row
      [lataa-urem-excel]])
   [:div.row
    [paikkaukset e! app]]])

(defn toteumat* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(do
                         (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
                         (nav/vaihda-kartan-koko! :S) ;oletuksena piilossa
                         (kartta-tasot/taso-pois! :organisaatio)
                         (kartta-tasot/taso-pois! :paikkaukset-paikkauskohteet)
                         (kartta-tasot/taso-paalle! :paikkaukset-toteumat)
                         (e! (tiedot/->AsetaPostPaivitys))
                         (e! (tiedot/->HaePaikkauskohteet))
                         (when (empty? (get-in app [:valinnat :tyomenetelmat])) (e! (yhteiset-tiedot/->HaeTyomenetelmat))))
                      #(do (e! (tiedot/->NakymastaPois))
                           (kartta-tasot/taso-pois! :paikkaukset-toteumat)))
    (fn [e! app]
      [view e! app])))

(defn toteumat [ur]
  (fn [_]
    [tuck/tuck tila/paikkaustoteumat toteumat*]))
