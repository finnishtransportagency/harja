(ns harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-toteumat
  (:require [cljs.core.async :refer [<! timeout]]
            [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [clojure.set :as set]
            [clojure.string :as str]

            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.loki :refer [log]]

            [harja.tyokalut.tuck :as tuck-apurit]

            [harja.domain.paikkaus :as paikkaus]
            [harja.domain.tierekisteri :as tierekisteri]
            [harja.domain.roolit :as roolit]
            [harja.domain.muokkaustiedot :as muokkaustiedot]

            [harja.tiedot.urakka.paikkaukset-toteumat :as tiedot]
            [harja.tiedot.urakka.paikkaukset-yhteinen :as yhteiset-tiedot]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-toteumalomake :as t-toteumalomake]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.kartta :as kartta-tiedot]

            [harja.views.kartta :as kartta]
            [harja.views.kartta.tasot :as kartta-tasot]
            [harja.views.urakka.yllapitokohteet :as yllapitokohteet]
            [harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-yhteinen :as yhteinen-view]
            [harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-toteumalomake :as v-toteumalomake]
            [harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet :as kohteet]
            [harja.ui.debug :as debug]
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
            [harja.ui.valinnat :as valinnat])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(defn- pinta-alojen-summa [paikkaukset]
  (->> paikkaukset
       (map :suirun-pinta-ala)
       (reduce +)))

(defn- juoksumetri-summa [paikkaukset]
  (->> paikkaukset
       (map ::paikkaus/juoksumetri)
       (reduce +)))

(defn- kpl-summa [paikkaukset]
  (->> paikkaukset
       (map ::paikkaus/kpl)
       (reduce +)))

(defn- massamenekin-keskiarvo [paikkaukset]
  (let [menekit (map ::paikkaus/massamenekki paikkaukset)]
    (if (not= 0 (count menekit))
      (/ (reduce + menekit) (count menekit))
      0)))

(defn- massamaaran-summa [paikkaukset]
  (->> paikkaukset
       (map ::paikkaus/massamaara)
       (reduce +)))


(defn ilmoita-virheesta-modal
  "Modaali, jossa kerrotaan paikkaustoteumassa olevasta virheestä."
  [e! app]
  (let [paikkaukset (::paikkaus/paikkaukset (:modalin-paikkauskohde app))
        {::paikkaus/keys [kohde-id urakka-id nimi pinta-ala massamenekki] :as paikkaus} (first paikkaukset)
        rivien-lkm (count paikkaukset)
        pinta-ala (* 0.01 (Math/round (* 100 (pinta-alojen-summa paikkaukset))))
        massamenekki (massamenekin-keskiarvo paikkaukset)
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
                                                                     ::paikkaus/pinta-ala-summa pinta-ala
                                                                     ::paikkaus/massamenekki-summa massamenekki
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
                         (yleiset/tietoja {:class "modal-ilmoita-virheesta-tiedot"}
                                          "Kohde" nimi
                                          "Rivejä" rivien-lkm
                                          "Pinta-ala yht. " (str pinta-ala "m\u00B2")
                                          "Massamenekki " (str massamenekki "kg/m\u00B2"))])}
        varmista-kayttajalta/modal-muut-vastaanottajat
        (merge varmista-kayttajalta/modal-saateviesti {:otsikko "Lisätietoa virheestä"
                                                       :pakollinen? true
                                                       :validoi [[:ei-tyhja "Anna tarkempi kuvaus virheistä."]]})
        varmista-kayttajalta/modal-sahkopostikopio]
       lomakedata]
      [yleiset/vihje "Huom! Lähetetyn sähköpostiviestin sisältö tallennetaan Harjaan ja se saatetaan näyttää Harjassa paikkauskohteen tietojen yhteydessä."]]]))

(def ohje-teksti-tilaajalle
  "Tarkista toteumat ja valitse Merkitse tarkistetuksi, jolloin tiettyjen työmenetelmien tiedot lähtevät YHA:an. Valitse Ilmoita virhe lähettääksesi virhetiedot sähköpostitse urakoitsijalle.")

(def ohje-teksti-urakoitsijalle
  "Tarkista toteumatiedoista mahdolliset tilaajan raportoimat virheet. Virheet on raportoitu myös sähköpostitse urakan vastuuhenkilölle.")

(def lahetyksen-tilan-teksti
  {"lahetetty" "Lähetetty YHA:an"
   "virhe" "Virhe lähetyksessä"
   "odottaa_vastausta" "Odottaa vastausta"
   nil "Ei lähetetty YHA:an"})

(defn- aukaisu-fn
  [avain arvo e! kohde]
  (do
    ;; Zoomataan kartta valitun paikkaustoteuman toteumiin
    (let [_ (reset! tiedot/paikkaustoteumat-kartalla [kohde])
          _ (reset! tiedot/valitut-kohteet-atom #{})]
      (e! (tuck-apurit/->MuutaTila [::paikkaus/toteumataulukon-tilat avain] arvo)))))

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
                                                              :losa (:losa paikkauskohde)})))))

(defn- avaa-toteuma-sivupalkkiin
  [e! tyomenetelmat r]
  (let [_ (if (not (nil? (::paikkaus/sijainti r)))
            ;; Jos sijainti on annettu, zoomaa valitulle reitille
            (let [alue (harja.geo/extent (::paikkaus/sijainti r))]
              (do
                (reset! tiedot/valitut-kohteet-atom #{(::paikkaus/id r)})
                (js/setTimeout #(kartta-tiedot/keskita-kartta-alueeseen! alue) 200)))
            ;; Muussa tapauksessa poista valittu reitti kartalta (zoomaa kauemmaksi)
            (reset! tiedot/valitut-kohteet-atom #{}))
        toteumalomake (set/rename-keys r paikkaus/speqcl-avaimet->paikkaus)
        toteumalomake (set/rename-keys toteumalomake paikkaus/speqcl-avaimet->tierekisteri)
        ;; Tienkohtia voi jostain syystä olla monta, mutta lomakkeella voidaan näyttää vain ensimmäisestä
        tienkohdat (first (get toteumalomake :harja.domain.paikkaus/tienkohdat))
        toteumalomake (merge toteumalomake
                             {:ajorata (::paikkaus/ajorata tienkohdat)
                              :ajouravalit (::paikkaus/ajorata tienkohdat)
                              :keskisaumat (::paikkaus/keskisaumat tienkohdat)
                              :reunat (::paikkaus/reunat tienkohdat)
                              :ajourat (::paikkaus/ajourat tienkohdat)})
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
                          (assoc :paikkauskohde-nimi (::paikkaus/nimi r) #_ (get-in toteumalomake [:harja.domain.paikkaus/paikkauskohde :harja.domain.paikkaus/nimi]))
                          (assoc :tyomenetelma (:tyomenetelma toteumalomake))
                          (assoc :kohteen-yksikko (::paikkaus/yksikko r) #_ (get-in toteumalomake [:harja.domain.paikkaus/paikkauskohde :harja.domain.paikkaus/yksikko]))
                          (assoc :paikkauskohde-id (::paikkaus/paikkauskohde-id r))
                          (assoc :pituus (:suirun-pituus toteumalomake))
                          (dissoc ::paikkaus/paikkauskohde :sijainti
                                  :suirun-pituus ::paikkaus/nimi :suirun-pinta-ala
                                  ::paikkaus/paikkauskohde ::paikkaus/yksikko ::paikkaus/paikkauskohde-id
                                  ::paikkaus/reunat ::paikkaus/ajourat ::paikkaus/ajorata ::paikkaus/keskisaumat
                                  ::paikkaus/tienkohta-id ::paikkaus/ajouravalit))]
    (do
      (e! (t-toteumalomake/->SuljeToteumaLomake))
      (e! (t-toteumalomake/->AvaaToteumaLomake toteumalomake nil)))))

(defn- yksikko-avain [yksikko]
  (cond
    (= "t" yksikko) ::paikkaus/massamaara
    (= "jm" yksikko) ::paikkaus/juoksumetri
    (= "m2" yksikko) ::paikkaus/pinta-ala
    (= "kpl" yksikko) ::paikkaus/kpl
    :default ::paikkaus/massamaara))

(defn- skeema-menetelmalle
  "Taulukon skeema työmenetelmän perusteella. Levittimellä tehdyt ja UREM paikkaukset sisältävät enemmän dataa kuin
  muut, joten näille menetelmille lisätään useampia kenttiä."
  [tyomenetelma tyomenetelmat yksikko]
  (let [desimaalien-maara 2
        tierekisteriosoite-sarakkeet [nil
                                      {:nimi ::tierekisteri/tie}
                                      {:nimi ::tierekisteri/ajorata}
                                      {:nimi ::tierekisteri/aosa}
                                      {:nimi ::tierekisteri/aet}
                                      {:nimi ::tierekisteri/losa}
                                      {:nimi ::tierekisteri/let}
                                      {:nimi :suirun-pituus}]
        urapaikkaus? (urem? tyomenetelma tyomenetelmat)
        levittimella-tehty? (paikkaus/levittimella-tehty? {:tyomenetelma tyomenetelma} tyomenetelmat)]
    (into []
          (keep identity)
          (concat
            [{:tyyppi :vetolaatikon-tila :leveys 1}]
            [{:otsikko "Alku\u00ADaika"
              :leveys 8
              :nimi ::paikkaus/alkuaika
              :fmt (if urapaikkaus?
                     pvm/pvm-aika-klo-suluissa
                     pvm/pvm-opt)}
             {:otsikko "Loppu\u00ADaika"
              :leveys 8
              :nimi ::paikkaus/loppuaika
              :fmt (if urapaikkaus?
                     pvm/pvm-aika-klo-suluissa
                     pvm/pvm-opt)}]
            (yllapitokohteet/tierekisteriosoite-sarakkeet 4 tierekisteriosoite-sarakkeet)
            (when (or levittimella-tehty? urapaikkaus?)
              [{:otsikko "Leveys\u00AD (m)"
                :leveys 5
                :nimi ::paikkaus/leveys}])
            [{:otsikko "Työ\u00ADmene\u00ADtelmä"
              :leveys 10
              :nimi ::paikkaus/tyomenetelma
              :fmt #(paikkaus/tyomenetelma-id->nimi % tyomenetelmat)}]
            ;; Näytetään yksikkö muille paitsi uremille ja levittimellä tehdyille
            (when-not (or levittimella-tehty? urapaikkaus?)
             [{:otsikko yksikko
               :leveys 5
               :nimi (yksikko-avain yksikko)}])
            (when (or levittimella-tehty? urapaikkaus?)
               [{:otsikko "Massa\u00ADtyyp\u00ADpi"
                 :leveys 10
                 :nimi ::paikkaus/massatyyppi}
                {:otsikko "m\u00B2"
                 :leveys 5
                 :fmt #(fmt/desimaaliluku-opt % desimaalien-maara)
                 :nimi :suirun-pinta-ala}
                {:otsikko "kg/m²"
                 :leveys 5
                 :nimi ::paikkaus/massamenekki}
                {:otsikko "t"
                 :leveys 5
                 :nimi ::paikkaus/massamaara}
                {:otsikko "Raekoko"
                 :leveys 5
                 :nimi ::paikkaus/raekoko}
                {:otsikko "Kuula\u00ADmylly"
                 :leveys 5
                 :nimi ::paikkaus/kuulamylly}])
             ))))

(defn- gridien-gridi
  [{:keys [ladataan-tietoja? ryhmittele otsikkokomponentti e! tyomenetelmat] {:keys [paikkauket-vetolaatikko]} :app :as app} paikkauskohteet gridien-tilat]
  [:div {:style {:display "flex"
                 :flex-direction "column"}}
   (when ladataan-tietoja?
     [:div "Odota, päivitetään tietoja "
      [yleiset/ajax-loader "Päivitetään listaa.."]])
   (if (and
          (some? paikkauskohteet)
          (not (empty? paikkauskohteet)))
     (into [:<>] 
           (map (fn [{paikkaukset ::paikkaus/paikkaukset :as kohde}]                   
                  (let [avain (keyword (::paikkaus/nimi kohde))]
                    [:<>
                     [otsikkokomponentti e! {:toteumien-maara (count paikkaukset)
                                             :ladataan-tietoja? ladataan-tietoja?
                                             :auki? (get gridien-tilat avain)
                                             :avaa! (r/partial aukaisu-fn avain (not (get gridien-tilat avain)) e! kohde)
                                             :tyomenetelmat tyomenetelmat}
                      kohde]
                     (when (get gridien-tilat avain)
                       (if (> (count paikkaukset) 0)
                         [grid/grid
                          {:otsikko (when ladataan-tietoja?
                                      [yleiset/ajax-loader-pieni "Päivitetään listaa.."])
                           :salli-valiotsikoiden-piilotus? true
                           :valiotsikoiden-alkutila :kaikki-kiinni
                           :tunniste ::paikkaus/id
                           :sivuta 100
                           :tyhja (if ladataan-tietoja?
                                    [yleiset/ajax-loader "Haku käynnissä"]
                                    "Ei paikkauksia")
                           :rivi-klikattu (r/partial avaa-toteuma-sivupalkkiin e! tyomenetelmat)}
                          (skeema-menetelmalle (::paikkaus/tyomenetelma (first paikkaukset)) tyomenetelmat (::paikkaus/yksikko kohde))
                          paikkaukset]
                         [:div "Ei toteumia"]))])))
           paikkauskohteet)
     [:div "Ei näytettäviä paikkauskohteita"])])

(defn- otsikkokomponentti
  [_ _]
  (let [fmt-fn (fn [arvo]
                 (let [tila (case arvo
                              "tilattu" "kesken"
                              "hylatty" "hylätty"
                              arvo)]
                   (str/capitalize tila)))
        class-skeema {"tilattu" "tila-ehdotettu"
                      "valmis" "tila-valmis"
                      "hylatty" "tila-hylatty"
                      "ehdotettu" "tila-ehdotettu"}] 
    (fn [e! {:keys [avaa! auki? toteumien-maara tyomenetelmat]}
         {paikkaukset ::paikkaus/paikkaukset
          alkuaika ::paikkaus/alkupvm
          tarkistettu ::paikkaus/tarkistettu
          tyomenetelma ::paikkaus/tyomenetelma
          ilmoitettu-virhe ::paikkaus/ilmoitettu-virhe
          lahetyksen-tila ::paikkaus/yhalahetyksen-tila
          loppuaika ::paikkaus/loppupvm 
          paikkauskohteen-tila ::paikkaus/paikkauskohteen-tila
          yksikko ::paikkaus/yksikko :as paikkauskohde}]
      (let [urapaikkaus? (urem? tyomenetelma tyomenetelmat)
            tyomenetelma (or tyomenetelma (::paikkaus/tyomenetelma (first paikkaukset))) ; tarviikohan, en tiedä. jos vanhoilla kohteilla ei ole tuota kenttää?
            levittimella-tehty? (paikkaus/levittimella-tehty? paikkauskohde tyomenetelmat)
            urakoitsija-kayttajana? (= (roolit/osapuoli @istunto/kayttaja) :urakoitsija)
            arvo-kpl (kpl-summa paikkaukset)
            arvo-pinta-ala (pinta-alojen-summa paikkaukset)
            arvo-massamenekki (massamenekin-keskiarvo paikkaukset)
            arvo-juoksumetri (juoksumetri-summa paikkaukset)
            arvo-massamaara (massamaaran-summa paikkaukset)]
        [:div.flex-row.venyta.otsikkokomponentti {:class (str "" (when (> toteumien-maara 0) " klikattava"))
                                                  :on-click #(when (> (count paikkaukset) 0) (avaa!))}
         [:div.basis48.nogrow 
          (when (> toteumien-maara 0) 
            (if auki? 
              [ikonit/navigation-ympyrassa :down]
              [ikonit/navigation-ympyrassa :right]))]
         [:div.basis256.nogrow.shrink3
          [:div.caption.lihavoitu.musta (str (::paikkaus/nimi paikkauskohde))]
          [:div.small-text.harmaa (str "Päivitetty: "
                                       (or (pvm/pvm-aika-klo-suluissa
                                            (::muokkaustiedot/muokattu paikkauskohde))
                                           "-"))]
          [yleiset/tila-indikaattori paikkauskohteen-tila {:fmt-fn fmt-fn 
                                                           :class-skeema class-skeema
                                                           :luokka "body-text"}]]
         [:div.basis256.grow2.shrink3.rajaus
          [:div.caption.lihavoitu.musta (str (paikkaus/tyomenetelma-id->nimi tyomenetelma tyomenetelmat))]
          [:div.small-text.harmaa (if (= 0 toteumien-maara)
                                    "Ei toteumia"
                                    (str toteumien-maara " toteuma" (when (not= 1 toteumien-maara) "a")))]
          [:div (str (pvm/pvm-aika-klo-suluissa alkuaika) " - " (pvm/pvm-aika-klo-suluissa loppuaika))]]
         ;; Muut kuin urem ja levittimellä tehdyt
         (when (and (not urapaikkaus?) (not levittimella-tehty?))
           [:div.basis512.growfill.small-text.riviksi.shrink4.rajaus
            (when (= "m2" yksikko) [:span.small-text.col-mimic (str (fmt/desimaaliluku-opt arvo-pinta-ala) " m2")])
            (when (= "jm" yksikko) [:span.small-text.col-mimic (str (fmt/desimaaliluku-opt arvo-juoksumetri) " jm")])
            (when (= "t" yksikko) [:span.small-text.col-mimic (str (fmt/desimaaliluku-opt arvo-massamaara) " t")])
            (when (= "kpl" yksikko) [:span.small-text.col-mimic (str (fmt/desimaaliluku-opt arvo-kpl) " kpl")])])
         (when (or urapaikkaus? levittimella-tehty?)
           [:div.basis512.growfill.small-text.riviksi.shrink4.rajaus
            (when (not= 0 arvo-pinta-ala) [:span.small-text.col-mimic (str (fmt/desimaaliluku-opt arvo-pinta-ala) " m2")])
            (when (not= 0 arvo-massamaara) [:span.small-text.col-mimic (str (fmt/desimaaliluku-opt arvo-massamaara) " t")])
            (when (not= 0 arvo-massamenekki) [:span.small-text.col-mimic (str (fmt/desimaaliluku-opt arvo-massamenekki) " kg/m2")])])
         [:div.basis192.nogrow.body-text.shrink2.rajaus
          [yleiset/linkki "Lisää toteuma"
           #(luo-uusi-toteuma-kohteelle
             e!
             {::paikkaus/tyomenetelma tyomenetelma
              ::paikkaus/paikkauskohde paikkauskohde})
           {:stop-propagation true
            :disabloitu? (or urapaikkaus?
                             (not= "tilattu" paikkauskohteen-tila)) 
            :ikoni (ikonit/livicon-plus)
            :block? true}]
          [yleiset/linkki "Ilmoita virhe"
           #(e! (tiedot/->AvaaVirheModal paikkauskohde))
           {:style {}
            :block? true
            :ikoni (ikonit/envelope)
            :disabloitu? urakoitsija-kayttajana?
            :stop-propagation true}]]
         [:div.basis192.nogrow.shrink1.body-text
          {:class (str (when (or true (boolean tarkistettu)) "tarkistettu"))}
          (if (boolean tarkistettu) 
            [:div.body-text.harmaa "Tarkistettu"]
            [yleiset/linkki "Tarkistettu"
             #(e! (tiedot/->PaikkauskohdeTarkistettu
                   {::paikkaus/paikkauskohde paikkauskohde}))
             {:disabloitu? (or
                            urakoitsija-kayttajana?
                            (boolean tarkistettu))
              :ikoni (ikonit/check)
              :style {:margin-top "0px"}
              :block? true
              :stop-propagation true}])
          [:div.small-text.harmaa "Lähetys YHAan"]]]))))

(defn paikkaukset
  [e! {:keys [paikkaukset-grid]
       {:keys [paikkauksien-haku-kaynnissa?
               paikkauksien-haku-tulee-olemaan-kaynnissa?] :as filtterit} :filtterit
       gridien-tilat ::paikkaus/toteumataulukon-tilat :as app}] 
  [:div
   (if (= :urakoitsija (roolit/osapuoli @istunto/kayttaja))
     [yleiset/vihje ohje-teksti-urakoitsijalle]
     [yleiset/vihje ohje-teksti-tilaajalle])
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

(defn view [e! app]
  [:div
   [:div.row.filtterit {:style {:padding "16px" :height "125px"}}
    [yhteinen-view/hakuehdot
     {:tila-atomi app
      :nakyma :toteumat
      :urakka (-> @tila/yleiset :urakka :id)
      :palvelukutsu-onnistui-fn #(e! (tiedot/->PaikkauksetHaettu %))}]]

   [debug/debug app]
   (when (:modalin-paikkauskohde app)
     [ilmoita-virheesta-modal e! app])
   [:div.row
    [kartta/kartan-paikka]]
   [:div.row
    [paikkaukset e! app]]])

(defn toteumat* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(do
                         (kartta-tasot/taso-paalle! :paikkaukset-toteumat)
                         (e! (tiedot/->AsetaPostPaivitys))
                         (e! (tiedot/->HaePaikkauskohteet))
                         (when (empty? (get-in app [:valinnat :tyomenetelmat])) (e! (yhteiset-tiedot/->HaeTyomenetelmat)))
                         (reset! tiedot/taso-nakyvissa? true)
                         )

                      #(do (e! (tiedot/->NakymastaPois))
                           ;(reset! tiedot/taso-nakyvissa? false)
                           (kartta-tasot/taso-pois! :paikkaukset-toteumat)))
    (fn [e! app]
      [view e! app])))

(defn toteumat [ur]
  (komp/luo
    (komp/sisaan #(do
                    (kartta-tasot/taso-pois! :paikkaukset-paikkauskohteet)
                    ;(kartta-tasot/taso-paalle! :paikkaukset-toteumat)
                    (kartta-tasot/taso-pois! :organisaatio)
                    #_ (reset! t-paikkauskohteet-kartalle/karttataso-nakyvissa? false)))
    (fn [_]
      [tuck/tuck tila/paikkaustoteumat toteumat*])))
