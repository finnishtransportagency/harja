(ns harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-toteumat
  (:require [cljs.core.async :refer [<! timeout]]
            [reagent.core :as r]
            [tuck.core :as tuck]
            [clojure.set :as set]

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
            [harja.tiedot.istunto :as istunto]

            [harja.views.kartta :as kartta]
            [harja.views.kartta.tasot :as kartta-tasot]
            [harja.views.urakka.yllapitokohteet :as yllapitokohteet]
            [harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-yhteinen :as yhteinen-view]
            [harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohdelomake :as kohdelomake]
            [harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-toteumalomake :as v-toteumalomake]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-toteumalomake :as t-toteumalomake]


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
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet-kartalle :as t-paikkauskohteet-kartalle])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(defn- pinta-alojen-summa [paikkaukset]
  (->> paikkaukset
       (map :suirun-pinta-ala)
       (reduce +)))

(defn- massamenekin-keskiarvo [paikkaukset]
  (let [menekit (map ::paikkaus/massamenekki paikkaukset)]
    (if (not= 0 (count menekit)) 
      (/ (reduce + menekit) (count menekit))
      0)))

(defn- massamaaran-summa [paikkaukset]
  (->> paikkaukset
       (map :massamaara)
       (reduce +)))

(defn ilmoita-virheesta-modal
  "Modaali, jossa kerrotaan paikkaustoteumassa olevasta virheestä."
  [e! app]
  (let [paikkaukset (:modalin-paikkaus app)
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
       [:span.bold (get-in @yhteiset-tiedot/tila [:urakka :urakoitsija :nimi])]]
      [lomake/lomake {:otsikko "Sähköpostiviestin sisältö"
                      :muokkaa! #(e! (tiedot/->PaivitaLomakedata %))}
       [{:nimi :tiedot :muokattava? (constantly false)
         :tyyppi :komponentti :palstoja 3
         :komponentti (fn []
                        [:div {:style {:padding "10px" :border "1px solid #f0f0f0"}}
                         [:p "Virhe kohteen " [:b nimi ] " paikkaustoteumassa. Tarkista ja korjaa tiedot urakoitsijan järjestelmässä ja lähetä kohteen tiedot uudelleen Harjaan." ]
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


(defn paikkaukset-vetolaatikko
  [e! {tienkohdat ::paikkaus/tienkohdat materiaalit ::paikkaus/materiaalit id ::paikkaus/id :as rivi}
   app]
  (let [nayta-numerot #(apply str (interpose ", " %))
        tienkohdat-skeema [{:otsikko "Ajo\u00ADrata"
                            :leveys 1
                            :nimi ::paikkaus/ajorata}
                           {:otsikko "Reu\u00ADnat"
                            :leveys 1
                            :nimi ::paikkaus/reunat
                            :fmt nayta-numerot}
                           {:otsikko "Ajo\u00ADurat"
                            :leveys 1
                            :nimi ::paikkaus/ajourat
                            :fmt nayta-numerot}
                           {:otsikko "Ajoura\u00ADvälit"
                            :leveys 1
                            :nimi ::paikkaus/ajouravalit
                            :fmt nayta-numerot}
                           {:otsikko "Kes\u00ADkisau\u00ADmat"
                            :leveys 1
                            :nimi ::paikkaus/keskisaumat
                            :fmt nayta-numerot}]
        materiaalit-skeema [{:otsikko "Esiin\u00ADtymä"
                             :leveys 1
                             :nimi ::paikkaus/esiintyma}
                            {:otsikko "Kuu\u00ADlamyl\u00ADly\u00ADarvo"
                             :leveys 2
                             :nimi ::paikkaus/kuulamylly-arvo}
                            {:otsikko "Muoto\u00ADarvo"
                             :leveys 2
                             :nimi ::paikkaus/muotoarvo}
                            {:otsikko "Side\u00ADaine\u00ADtyyp\u00ADpi"
                             :leveys 2
                             :nimi ::paikkaus/sideainetyyppi}
                            {:otsikko "Pitoi\u00ADsuus"
                             :leveys 1
                             :nimi ::paikkaus/pitoisuus}
                            {:otsikko "Lisä\u00ADaineet"
                             :leveys 2
                             :nimi ::paikkaus/lisa-aineet}]]
    [:div
     [:div "Veto"]
     [grid/grid
      {:otsikko "Tienkohdat"
       :tunniste ::paikkaus/tienkohta-id
       :sivuta grid/vakiosivutus
       :tyhja "Ei tietoja"}
      tienkohdat-skeema
      tienkohdat]
     [grid/grid
      {:otsikko "Materiaalit"
       :tunniste ::paikkaus/materiaali-id
       :sivuta grid/vakiosivutus
       :tyhja "Ei tietoja"}
      materiaalit-skeema
      materiaalit]]))

(def ohje-teksti-tilaajalle
  "Tarkista toteumat ja valitse Merkitse tarkistetuksi, jolloin tiettyjen työmenetelmien tiedot lähtevät YHA:an. Valitse Ilmoita virhe lähettääksesi virhetiedot sähköpostitse urakoitsijalle.")

(def ohje-teksti-urakoitsijalle
  "Tarkista toteumatiedoista mahdolliset tilaajan raportoimat virheet. Virheet on raportoitu myös sähköpostitse urakan vastuuhenkilölle.")

(def otsikkokomp-vasen-margin "20px")

(def lahetyksen-tilan-teksti
  {"lahetetty" "Lähetetty YHA:an"
   "virhe" "Virhe lähetyksessä"
   "odottaa_vastausta" "Odottaa vastausta"
   nil "Ei lähetetty YHA:an"})

(def ilmoitettu-virhe-max-merkkimaara 100)

(defn- aukaisu-fn 
  [avain arvo e!]
  (e! (tuck-apurit/->MuutaTila [::paikkaus/toteumataulukon-tilat avain] arvo)))

(defn- luo-uusi-toteuma-kohteelle 
  [e! r]
  (let [toteumalomake (-> r
                          (set/rename-keys paikkaus/speqcl-avaimet->paikkaus)
                          (set/rename-keys paikkaus/speqcl-avaimet->tierekisteri)
                          (assoc :tyyppi :uusi-toteuma)
                          (assoc :paikkauskohde-id (get-in r [:harja.domain.paikkaus/paikkauskohde :harja.domain.paikkaus/id]))
                          (dissoc ::paikkaus/paikkauskohde))]
   (do 
     (e! (t-toteumalomake/->SuljeToteumaLomake))
     (e! (t-toteumalomake/->AvaaToteumaLomake toteumalomake)))))

(defn- avaa-toteuma-sivupalkkiin
  [e! r]
  (let [toteumalomake (set/rename-keys r paikkaus/speqcl-avaimet->paikkaus)
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
        tyyppi (if (= (:tyomenetelma toteumalomake) "UREM")
                 :toteuman-luku
                 :toteuman-muokkaus)
        pinta-ala (if (= (:tyomenetelma toteumalomake) "UREM")
                    (:suirun-pinta-ala toteumalomake)
                    (:pinta-ala toteumalomake))

        toteumalomake (-> toteumalomake
                          (assoc :pinta-ala pinta-ala)
                          (assoc :tyyppi tyyppi)
                          (assoc :maara (:massamaara toteumalomake))
                          (assoc :paikkauskohde-nimi (get-in toteumalomake [:harja.domain.paikkaus/paikkauskohde :harja.domain.paikkaus/nimi]))
                          (assoc :tyomenetelma (:tyomenetelma toteumalomake))
                          (assoc :kohteen-yksikko (get-in toteumalomake [:harja.domain.paikkaus/paikkauskohde :harja.domain.paikkaus/yksikko]))
                          (assoc :paikkauskohde-id (get-in toteumalomake [:harja.domain.paikkaus/paikkauskohde :harja.domain.paikkaus/id]))
                          (assoc :pituus (:suirun-pituus toteumalomake))
                          (dissoc :massamaara :harja.domain.paikkaus/paikkauskohde :sijainti
                                  :suirun-pituus :harja.domain.paikkaus/nimi :suirun-pinta-ala
                                  :harja.domain.paikkaus/paikkauskohde))]
    (do
      (e! (t-toteumalomake/->SuljeToteumaLomake))
      (e! (t-toteumalomake/->AvaaToteumaLomake toteumalomake)))))

(defn- skeema-menetelmalle
  [tyomenetelma]
  (let [desimaalien-maara 2
        tierekisteriosoite-sarakkeet [nil
                                      {:nimi ::tierekisteri/tie}
                                      nil nil
                                      {:nimi ::tierekisteri/aosa}
                                      {:nimi ::tierekisteri/aet}
                                      {:nimi ::tierekisteri/losa}
                                      {:nimi ::tierekisteri/let}
                                      {:nimi :suirun-pituus}]
        urapaikkaus? (paikkaus/urapaikkaus? {:tyomenetelma tyomenetelma})
        levittimella-tehty? (paikkaus/levittimella-tehty? {:tyomenetelma tyomenetelma})] 
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
          (when (or 
                 levittimella-tehty?
                 urapaikkaus?)
            [{:otsikko "Leveys\u00AD (m)"
              :leveys 5
              :nimi ::paikkaus/leveys}])
[{:otsikko "Työ\u00ADmene\u00ADtelmä"
             :leveys 10
            :nimi ::paikkaus/tyomenetelma}
           {:otsikko "Massa\u00ADtyyp\u00ADpi"
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
            :nimi :massamaara}
           {:otsikko "Raekoko"
            :leveys 5
            :nimi ::paikkaus/raekoko}
           {:otsikko "Kuula\u00ADmylly"
            :leveys 5
            :nimi ::paikkaus/kuulamylly}]))))

(defn- gridien-gridi
  [{:keys [ladataan-tietoja? ryhmittele otsikkokomponentti e!] {:keys [paikkauket-vetolaatikko]} :app :as app} paikkauskohteet gridien-tilat] 
  [:div {:style {:display "flex"
                 :flex-direction "column"}}
   (when (and 
          (some? paikkauskohteet)
          (not (empty? paikkauskohteet)))
     (into [:<>] 
           (map (fn [{paikkaukset ::paikkaus/paikkaukset :as kohde}]                   
                  (let [avain (keyword (::paikkaus/nimi kohde))]
                    [:<>
                     [otsikkokomponentti e! {:toteumien-maara (count paikkaukset)
                                             :auki? (get gridien-tilat avain)
                                             :avaa! (r/partial aukaisu-fn avain (not (get gridien-tilat avain)) e!)}
                      kohde] 
                     (when (get gridien-tilat avain) 
                       (if (> (count paikkaukset) 0) 
                         [grid/grid
                          {:otsikko (if ladataan-tietoja? 
                                      [yleiset/ajax-loader-pieni "Päivitetään listaa.."]
                                      "Paikkauksien toteumat")
                           :salli-valiotsikoiden-piilotus? true
                           :valiotsikoiden-alkutila :kaikki-kiinni
                           :tunniste ::paikkaus/id
                           :sivuta 100
                           :tyhja (if ladataan-tietoja?
                                    [yleiset/ajax-loader "Haku käynnissä"]
                                    "Ei paikkauksia")
                           :rivi-klikattu (r/partial avaa-toteuma-sivupalkkiin e!)}
                          (skeema-menetelmalle (::paikkaus/tyomenetelma (first paikkaukset)))
                          paikkaukset]
                         [:div "Ei toteumia"]))])))
           paikkauskohteet))])

(defn- otsikkokomponentti
  [e! {:keys [avaa! auki? toteumien-maara]} {paikkaukset ::paikkaus/paikkaukset
                                             alkuaika ::paikkaus/alkupvm
                                             tarkistettu ::paikkaus/tarkistettu
                                             tyomenetelma ::paikkaus/tyomenetelma
                                             ilmoitettu-virhe ::paikkaus/ilmoitettu-virhe
                                             lahetyksen-tila ::paikkaus/yhalahetyksen-tila
                                             loppuaika ::paikkaus/loppupvm :as paikkauskohde}]
  (let [urapaikkaus? (paikkaus/urapaikkaus? paikkauskohde)
        tyomenetelma (or tyomenetelma (::paikkaus/tyomenetelma (first paikkaukset))) ; tarviikohan, en tiedä. jos vanhoilla kohteilla ei ole tuota kenttää?
        levittimella-tehty? (paikkaus/levittimella-tehty? paikkauskohde)
        urakoitsija-kayttajana? (= (roolit/osapuoli @istunto/kayttaja) :urakoitsija)
        arvo-pinta-ala (pinta-alojen-summa paikkaukset)
        arvo-massamenekki (massamenekin-keskiarvo paikkaukset)
        arvo-massamaara (massamaaran-summa paikkaukset)] 
    [:div.flex-row.otsikkokomponentti {:class (str "" (when (> toteumien-maara 0) " klikattava"))
                                       :on-click #(when (> (count paikkaukset) 0) (avaa!))}
     [:div.grow0 
      (when (> toteumien-maara 0) 
        (if auki? 
          [ikonit/navigation-ympyrassa :down]
          [ikonit/navigation-ympyrassa :right]))]
     [:div.grow4
      [:div.caption.lihavoitu.musta (str (::paikkaus/nimi paikkauskohde))]
      [:div.small-text.harmaa (str "Päivitetty: " 
                                   (or (pvm/pvm-aika-klo-suluissa 
                                        (::muokkaustiedot/muokattu paikkauskohde)) 
                                       "-"))]]
     [:div.grow4 
      [:div.caption.lihavoitu.musta (str (paikkaus/kuvaile-tyomenetelma tyomenetelma))]
      [:div.small-text.harmaa (if (= 0 toteumien-maara) 
                                "Ei toteumia" 
                                (str toteumien-maara " toteuma" (when (not= 1 toteumien-maara) "a")))]
      [:div (str (pvm/pvm-aika-klo-suluissa alkuaika) " - " (pvm/pvm-aika-klo-suluissa loppuaika))]]
     [:div.grow4.growfill.small-text 
      (when (not= 0 arvo-pinta-ala) [:div.grow2.small-text (str arvo-pinta-ala " m2")])
      (when (not= 0 arvo-massamenekki) [:div.grow2.small-text (str arvo-massamenekki " t")])
      (when (not= 0 arvo-massamaara) (str arvo-massamaara " kg/m2"))]
     [:div.grow3.body-text 
      (when-not urapaikkaus?
        [yleiset/linkki "Lisää toteuma" 
         #(luo-uusi-toteuma-kohteelle 
           e! 
           {::paikkaus/tyomenetelma tyomenetelma
            ::paikkaus/paikkauskohde paikkauskohde})
         {:stop-propagation true
          :ikoni (ikonit/livicon-plus)
          :block? true}])
      (if-not urakoitsija-kayttajana?
        [yleiset/linkki "Ilmoita virhe"
         #(e! (tiedot/->AvaaVirheModal paikkauskohde))
         {:style {}
          :block? true
          :ikoni (ikonit/envelope)
          :stop-propagation true}])]
     (when-not urakoitsija-kayttajana?
       [:div.tarkistettu.grow3
        [kentat/vayla-checkbox {:arvo (boolean tarkistettu) :teksti "Tarkistettu" 
                                :checkbox-style {:margin-top "0px"}
                                :disabled (boolean tarkistettu)
                                :valitse! #(e! (tiedot/->PaikkauskohdeTarkistettu 
                                                {::paikkaus/paikkauskohde paikkauskohde}))}]
        [:div.small-text.harmaa {:style {:margin-left "35px"}} "Lähetys YHAan"]])]))

(defn paikkaukset
  [e! {:keys [paikkauksien-haku-kaynnissa? paikkauksien-haku-tulee-olemaan-kaynnissa?
                   paikkaukset-grid filtterit] 
       gridien-tilat ::paikkaus/toteumataulukon-tilat :as app}] 
  [:div
   [:span (pr-str filtterit)]
   (if (= :urakoitsija (roolit/osapuoli @istunto/kayttaja))
     [yleiset/vihje ohje-teksti-urakoitsijalle]
     [yleiset/vihje ohje-teksti-tilaajalle])
   [gridien-gridi
    {:e! e!
     :app app
     :ladataan-tietoja? (or paikkauksien-haku-kaynnissa? paikkauksien-haku-tulee-olemaan-kaynnissa?)
     :ryhmittele #{::paikkaus/nimi ::paikkaus/tyomenetelma}
     :otsikkokomponentti otsikkokomponentti}
    paikkaukset-grid
    gridien-tilat]       
   (when (:toteumalomake app)
     [sivupalkki/oikea 
      {:leveys "600px" :jarjestys 1}
      [v-toteumalomake/toteumalomake e! (:toteumalomake app)]])])


(defn toteumat* [e! app]
  (komp/luo
   (komp/sisaan-ulos #(do (e! (tiedot/->Nakymaan))
                          (reset! tiedot/taso-nakyvissa? true))
                     #(do (e! (tiedot/->NakymastaPois))
                          (reset! tiedot/taso-nakyvissa? false)))
   (fn [e! app]
     [:div
      [yhteinen-view/hakuehdot
        {:tila-atomi tiedot/filtterit
         :nakyma :toteumat
         :urakka urakka
         :palvelukutsu-onnistui-fn #(e! (tiedot/->PaikkauksetHaettu %))}] 
      [kartta/kartan-paikka]
      [debug/debug app]
      (when (:modalin-paikkaus app)
        [ilmoita-virheesta-modal e! app])
      [paikkaukset e! app]])))

(defn toteumat [ur]
  (komp/luo
   (komp/sisaan #(do
                   (yhteiset-tiedot/nakyman-urakka tiedot/filtterit ur)
                   (kartta-tasot/taso-pois! :paikkaukset-paikkauskohteet)
                   (kartta-tasot/taso-paalle! :paikkaukset-toteumat)
                   (reset! t-paikkauskohteet-kartalle/karttataso-nakyvissa? false)))
   (fn [_]
     [tuck/tuck tiedot/app toteumat*])))
