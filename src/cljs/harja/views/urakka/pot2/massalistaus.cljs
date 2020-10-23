(ns harja.views.urakka.pot2.massalistaus
  "POT2 massalistaukset"
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [cljs.core.async :refer [<! chan]]
            [cljs.spec.alpha :as s]
            [cljs-time.core :as t]
            [goog.events.EventType :as event-type]

            [harja.ui.grid :as grid]
            [harja.ui.debug :refer [debug]]
            [harja.ui.dom :as dom]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :as kentat]
            [harja.ui.komponentti :as komp]
            [harja.ui.modal :as modal]
            [harja.ui.napit :as napit]
            [harja.ui.validointi :as v]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.viesti :as viesti]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko virheen-ohje] :as yleiset]

            [harja.domain.paallystysilmoitus :as paallystysilmoitus-domain]
            [harja.domain.pot2 :as pot2-domain]
            [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]

            [harja.tiedot.urakka.paallystys :as paallystys]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.urakka.yhatuonti :as yha]
            [harja.tiedot.urakka.yllapito :as yllapito-tiedot]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.muokkauslukko :as lukko]
            [harja.tiedot.urakka.pot2.massat :as tiedot-massa]
            [harja.tiedot.urakka.urakka :as tila]

            [harja.fmt :as fmt]
            [harja.loki :refer [log logt tarkkaile!]]


            [harja.views.urakka.yllapitokohteet :as yllapitokohteet]
            [harja.pvm :as pvm]
            [harja.tyokalut.vkm :as vkm]
            [harja.ui.lomake :as ui-lomake]
            [clojure.string :as str])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

(def sideaineen-kayttotavat
  [{::pot2-domain/nimi "Lopputuotteen sideaine"
    ::pot2-domain/koodi :lopputuote}
   {::pot2-domain/nimi "Lisätty sideaine"
    ::pot2-domain/koodi :lisatty}])

(defn- aineen-otsikko-checkbox
  [e! {:keys [otsikko arvo label-luokka polku]}]
  [kentat/tee-kentta
   {:tyyppi :checkbox :label-luokka label-luokka
    :teksti otsikko :vayla-tyyli? true
    :nayta-rivina? true :iso-clickalue? true}
   (r/wrap
     arvo
     (fn [uusi-arvo]
       (e! (tiedot-massa/->PaivitaAineenTieto polku uusi-arvo))))])

(defn- otsikko-ja-kentta
  [e! {:keys [otsikko tyyppi arvo polku pakollinen? leveys placeholder
              valinnat valinta-nayta valinta-arvo validoi-kentta-fn]}]
  [:div.otsikko-ja-kentta.inline-block
   [:div
    [:span.kentan-label otsikko]
    (when pakollinen? [:span.required-tahti " *"])]
   [kentat/tee-kentta {:tyyppi tyyppi :teksti otsikko :nayta-rivina? false
                       :leveys leveys :placeholder placeholder
                       :valinnat valinnat :valinta-nayta valinta-nayta
                       :valinta-arvo valinta-arvo
                       :validoi-kentta-fn validoi-kentta-fn}
    (r/wrap
      arvo
      (fn [uusi-arvo]
        (e! (tiedot-massa/->PaivitaAineenTieto polku uusi-arvo))))]])

(defn- runkoaineiden-kentat [tiedot polun-avaimet]
  (let [{:keys [esiintyma fillerityyppi kuulamyllyarvo litteysluku
                massaprosentti kuvaus]} tiedot
        aineen-koodi (second polun-avaimet)]
    ;; Käyttöliittymäsuunnitelman mukaisesti tietyillä runkoaineilla on tietyt kentät
    ;; ja ainekohtaisia eroja käsitellään tässä contains? funktion avulla
    (remove
      nil?
      [(when-not (contains? #{3 7} aineen-koodi)
         {:otsikko "Kiviainesesiintymä"
          :tyyppi :string :pakollinen? true
          :arvo esiintyma :leveys "150px"
          :polku (conj polun-avaimet :esiintyma)})
       (when (contains? #{3} aineen-koodi)
         {:otsikko "Tyyppi" :valinnat pot2-domain/erikseen-lisattava-fillerikiviaines
          :tyyppi :valinta :pakollinen? true
          :arvo fillerityyppi :leveys "150px"
          :polku (conj polun-avaimet :fillerityyppi)})
       (when-not (contains? #{3 7} aineen-koodi)
         {:otsikko "kuulamyllyarvo"
          :tyyppi :numero :pakollinen? true
          :arvo kuulamyllyarvo :leveys "55px"
          :validoi-kentta-fn (fn [numero] (v/validoi-numero numero 0 40))
          :polku (conj polun-avaimet :kuulamyllyarvo)})
       (when-not (contains? #{3 7} aineen-koodi)
         {:otsikko "Litteysluku"
          :tyyppi :numero :pakollinen? true
          :arvo litteysluku :leveys "68px"
          :polku (conj polun-avaimet :litteysluku)})
       (when (contains? #{7} aineen-koodi)
         {:otsikko "Kuvaus" :placeholder "Anna ainetta kuvaava nimi"
          :tyyppi :string :pakollinen? true
          :arvo kuvaus :leveys "160px"
          :polku (conj polun-avaimet :kuvaus)})
       {:otsikko "Massa-%"
        :tyyppi :numero :pakollinen? true
        :arvo massaprosentti :leveys "55px"
        :validoi-kentta-fn (fn [numero] (v/validoi-numero numero 0 100))
        :polku (conj polun-avaimet :massaprosentti)}])))

(defn- sideaineiden-kentat [tiedot polun-avaimet idx]
  (let [{:keys [sideainetyypit tyyppi pitoisuus]} tiedot]
    [{:otsikko "Tyyppi" :valinnat sideainetyypit
      :tyyppi :valinta :pakollinen? true
      :valinta-nayta ::pot2-domain/nimi
      :valinta-arvo ::pot2-domain/koodi
      :arvo tyyppi :leveys "250px"
      :polku (conj polun-avaimet :aineet idx :tyyppi)}
     {:otsikko "Pitoisuus"
      :tyyppi :numero :pakollinen? true
      :arvo pitoisuus :leveys "55px"
      :validoi-kentta-fn (fn [numero] (v/validoi-numero numero 0 100))
      :polku (conj polun-avaimet :aineet idx :pitoisuus)}]))

(defn- lisaaineiden-kentat [tiedot polun-avaimet]
  (let [{:keys [pitoisuus]} tiedot]
    [{:otsikko "Pitoisuus"
      :tyyppi :numero :pakollinen? true
      :arvo pitoisuus :leveys "70px"
      :validoi-kentta-fn (fn [numero] (v/validoi-numero numero 0 100))
      :polku (conj polun-avaimet :pitoisuus)}]))

(defn- tyypin-kentat [tyyppi tiedot polun-avaimet]
  (case tyyppi
    :runkoaineet (runkoaineiden-kentat tiedot polun-avaimet)
    :sideaineet (sideaineiden-kentat tiedot polun-avaimet 0)
    :lisaaineet (lisaaineiden-kentat tiedot polun-avaimet)

    nil))


(defn- sideaineet-komponentti [e! rivi kayttotapa polun-avaimet sideainetyypit]
  (let [aineet (or (get-in rivi (cons :data [:sideaineet kayttotapa :aineet]))
                   {0 {:tyyppi nil :pitoisuus nil}})]
    [:div
     (map-indexed (fn [idx [_ {tyyppi :tyyppi
                               pitoisuus :pitoisuus} :as arvot]]
                    (do
                      (log "arvot " (pr-str arvot))
                      ^{:key (str idx)}
                      [:div
                       (for [sak (sideaineiden-kentat {:tyyppi tyyppi
                                                       :pitoisuus pitoisuus
                                                       :sideainetyypit (vec sideainetyypit)}
                                                      polun-avaimet idx)]

                         ^{:key (str idx (:otsikko sak))}
                         [:div.inline-block {:style {:margin-right "6px"}}
                          [otsikko-ja-kentta e! sak]])]))
                  aineet)
     [:div
      [napit/uusi "Lisää uusi"
       #(e! (tiedot-massa/->LisaaSideaine kayttotapa))
       {:luokka (str "napiton-nappi lisaa-sideaine")}]]]))

(defn- ainevalintalaatikot [tyyppi aineet]
  (if (= tyyppi :sideaineet)
    sideaineen-kayttotavat
    aineet))

(defn- ainevalinta-kentat [e! rivi tyyppi aineet]
  [:div.ainevalinta-kentat
   (for [t (ainevalintalaatikot tyyppi aineet)]
      (let [polun-avaimet [tyyppi (::pot2-domain/koodi t)]
            {:keys [valittu?] :as tiedot} (get-in rivi (cons :data polun-avaimet))]
        ^{:key t}
        [:div {:class (str "ainevalinta " (when valittu? "valittu"))}
         [aineen-otsikko-checkbox e! {:otsikko (::pot2-domain/nimi t)
                                      :arvo valittu? :label-luokka (when valittu? "bold")
                                      :polku (conj polun-avaimet :valittu?)}]

         (when valittu?
           [:div.kentat-haitari
            (case tyyppi
              (:runkoaineet :lisaaineet)
              (for [k (tyypin-kentat tyyppi tiedot polun-avaimet)]
                ^{:key (:otsikko k)}
                [:div.inline-block {:style {:margin-right "6px"}}
                 [otsikko-ja-kentta e! k]])

              :sideaineet
              [sideaineet-komponentti e! rivi (::pot2-domain/koodi t)
               polun-avaimet aineet])])]))])

(defn massa-lomake [e! {:keys [massa lomake materiaalikoodistot] :as app}]
  (let [{:keys [massatyypit runkoainetyypit sideainetyypit lisaainetyypit]} materiaalikoodistot
        massa (:massa app)
        lomake (:pot2-massa-lomake app)
        _ (js/console.log "massa-lomake :: lomake " (pr-str lomake))]
    [:div
     [ui-lomake/lomake
      {:muokkaa! #(e! (tiedot-massa/->PaivitaLomake (ui-lomake/ilman-lomaketietoja %)))
       :otsikko (if (:pot2-massa/id massa)
                  "Muokkaa massaa"
                  "Uusi massa")
       :footer-fn (fn [data]
                    [:div
                     (when-not (empty? (ui-lomake/puuttuvat-pakolliset-kentat data))
                       [:div
                        [:div "Seuraavat pakolliset kentät pitää täyttää ennen tallentamista: "]
                        [:ul
                         (for [puuttuva (ui-lomake/puuttuvat-pakolliset-kentat data)]
                           [:li (name puuttuva)])]])
                     [:div.flex-row.alkuun
                      [napit/tallenna
                       "Tallenna"
                       #(e! (tiedot-massa/->TallennaLomake data))
                       {:vayla-tyyli? true
                        :luokka "suuri"
                        :disabled (not (ui-lomake/voi-tallentaa? data))}]
                      [napit/peruuta
                       "Peruuta"
                       #(e! (tiedot-massa/->TyhjennaLomake data))
                       {:vayla-tyyli? true
                        :luokka "suuri"}]]])
       :vayla-tyyli? true}
      [{:otsikko "Massan nimi" :muokattava? (constantly false) :nimi ::pot2-domain/massan-nimi :tyyppi :string :palstoja 3
        :luokka "bold" :vayla-tyyli? true :kentan-arvon-luokka "placeholder"
        :hae (fn [rivi]
               (if-not (::pot2-domain/tyyppi rivi)
                 "Nimi muodostuu automaattisesti lomakkeeseen täytettyjen tietojen perusteella"
                 (pot2-domain/massatyypin-rikastettu-nimi massatyypit rivi)))}
       (ui-lomake/rivi
         {:otsikko "Massatyyppi"
          :nimi ::pot2-domain/tyyppi :tyyppi :valinta
          :valinta-nayta ::pot2-domain/nimi :valinta-arvo ::pot2-domain/koodi :valinnat massatyypit
          :pakollinen? true :vayla-tyyli? true}
         {:otsikko "Max raekoko" :nimi ::pot2-domain/max-raekoko
          :tyyppi :valinta
          :valinta-nayta (fn [rivi]
                           (str rivi))
          :vayla-tyyli? true
          :valinta-arvo identity
          :valinnat pot2-domain/massan-max-raekoko
          :pakollinen? true}
         {:otsikko "Nimen tarkenne" :nimi ::pot2-domain/nimen-tarkenne :tyyppi :string
          :vayla-tyyli? true})
       (ui-lomake/rivi
         {:otsikko "Kuulamyllyluokka"
          :nimi ::pot2-domain/kuulamyllyluokka
          :tyyppi :valinta :valinta-nayta (fn [rivi]
                                            (str (:nimi rivi)))
          :vayla-tyyli? true :valinta-arvo :nimi
          :valinnat paallystysilmoitus-domain/+kyylamyllyt-ja-nil+
          :pakollinen? true}
         {:otsikko "Litteyslukuluokka"
          :nimi ::pot2-domain/litteyslukuluokka :tyyppi :valinta
          :valinta-nayta (fn [rivi]
                           (str rivi))
          :vayla-tyyli? true
          :valinta-arvo identity
          :valinnat pot2-domain/litteyslukuluokat
          :pakollinen? true}
         {:otsikko "DoP nro" :nimi ::pot2-domain/dop-nro :tyyppi :string
          :validoi [[:ei-tyhja "Anna DoP nro"]]
          :vayla-tyyli? true :pakollinen? true})



       {:nimi :runkoaineet :otsikko "Runkoaineen materiaali" :tyyppi :komponentti
        :palstoja 2
        :komponentti (fn [rivi] [ainevalinta-kentat e! rivi :runkoaineet runkoainetyypit])}
       {:nimi :sideaineet :otsikko "Sideaineet" :tyyppi :komponentti
        :palstoja 2
        :komponentti (fn [rivi] [ainevalinta-kentat e! rivi :sideaineet sideainetyypit])}
       {:nimi :lisaaineet :otsikko "Lisäaineet" :tyyppi :komponentti
        :palstoja 2
        :komponentti (fn [rivi] [ainevalinta-kentat e! rivi :lisaaineet lisaainetyypit])}]

      lomake]
     [debug app {:otsikko "TUCK STATE"}]]))

(defn- massan-runkoaineet
  [rivi ainetyypit]
[:span
 (for [aine (reverse
              (sort-by :runkoaine/massaprosentti
                       (:harja.domain.pot2/runkoaineet rivi)))]
   ^{:key (:runkoaine/id aine)}
   [:span
    [:div
     (str (pot2-domain/ainetyypin-koodi->nimi ainetyypit (:runkoaine/tyyppi aine))
          " ("
          (:runkoaine/esiintyma aine) ")")
   [:span.pull-right (str (:runkoaine/massaprosentti aine) "%")]]
    (when (:runkoaine/kuulamyllyarvo aine)
      [:div "-kuulamyllyarvo " (:runkoaine/kuulamyllyarvo aine)])
    (when (:runkoaine/litteysluku aine)
      [:div "-litteysluku " (:runkoaine/litteysluku aine)])])])

(defn- massan-sideaineet [rivi ainetyypit]
  [:span
   (for [aine (reverse
                (sort-by :sideaine/pitoisuus
                         (:harja.domain.pot2/sideaineet rivi)))]
     ^{:key (:sideaine/id aine)}
     [:span
      [:div
       (str (pot2-domain/ainetyypin-koodi->nimi ainetyypit (:sideaine/tyyppi aine)))
       [:span.pull-right (str (:sideaine/pitoisuus aine) "%")]]])])

(defn- massan-lisaaineet [rivi ainetyypit]
  [:span
   (for [aine (reverse
                (sort-by :lisaaine/pitoisuus
                         (:harja.domain.pot2/lisa-aineet rivi)))]
     ^{:key (:lisaaine/id aine)}
     [:span
      [:div
       (str (pot2-domain/ainetyypin-koodi->nimi ainetyypit (:lisaaine/tyyppi aine)))
       [:span.pull-right (str (:lisaaine/pitoisuus aine) "%")]]])])

(defn- massan-toiminnot [e! rivi]
  [:span.pull-right
   [napit/nappi ""
    #(log "pen painettu")
    {:ikoninappi? true :luokka "klikattava"
     :ikoni (ikonit/livicon-pen)}]
   [napit/nappi ""
    #(log "duplicate painettu")
    {:ikoninappi? true :luokka "klikattava"
     :ikoni (ikonit/livicon-duplicate)}]])

(defn- paallystysmassat-taulukko [e! {:keys [massat materiaalikoodistot] :as app}]
  [grid/grid
   {:otsikko "Massat"
    :tunniste :pot2-massa/id
    :tyhja (if (nil? massat)
             [ajax-loader "Haetaan massatyyppejä..."]
             "Urakalle ei ole vielä lisätty massoja")
    :voi-lisata? false
    :voi-kumota? false
    :voi-poistaa? (constantly false)
    :voi-muokata? true
    :custom-toiminto {:teksti "Luo uusi massa"
                      :toiminto #(e! (tiedot-massa/->UusiMassa true))
                      :opts {:ikoni (ikonit/livicon-plus)
                             :luokka "napiton-nappi"}}}
   [{:otsikko "Massatyyppi" :tyyppi :string
     :hae (fn [rivi]
            (pot2-domain/massatyypin-rikastettu-nimi (:massatyypit materiaalikoodistot) rivi))
     :solun-luokka (constantly "bold") :leveys 8}
    {:otsikko "Runkoaineet" :nimi ::pot2-domain/runkoaineet :fmt #(or % "-") :tyyppi :komponentti :leveys 6
     :komponentti (fn [rivi]
                    [massan-runkoaineet rivi (:runkoainetyypit materiaalikoodistot)])}
    {:otsikko "Sideaineet" :nimi ::pot2-domain/sideaineet :fmt  #(or % "-") :tyyppi :komponentti :leveys 5
     :komponentti (fn [rivi]
                    [massan-sideaineet rivi (:sideainetyypit materiaalikoodistot)])}
    {:otsikko "Lisäaineet" :nimi ::pot2-domain/lisa-aineet :fmt  #(or % "-") :tyyppi :komponentti :leveys 4
     :komponentti (fn [rivi]
                    [massan-lisaaineet rivi (:lisaainetyypit materiaalikoodistot)])}
    {:otsikko "Toiminnot" :nimi ::pot2-domain/lisa-aineet :fmt #(or % "-") :tyyppi :komponentti :leveys 3
     :komponentti (fn [rivi]
                    [massan-toiminnot e! rivi])}]
   massat])

(defn- kantavan-kerroksen-materiaalit-taulukko [e! {:keys [murskeet] :as app}])

(defn- materiaalikirjasto [e! app]
  [:span
   [paallystysmassat-taulukko e! app]
   [kantavan-kerroksen-materiaalit-taulukko e! app]
   [napit/sulje #(swap! tiedot-massa/nayta-materiaalikirjasto? not)]])

(defn massat* [e! app]
  (komp/luo
    (komp/lippu tiedot-massa/pot2-nakymassa?)
    (komp/piirretty (fn [this]
                      (e! (tiedot-massa/->AlustaTila))
                      (e! (tiedot-massa/->HaePot2Massat))
                      (e! (tiedot-massa/->HaeKoodistot))))
    (fn [e! app]
      [:div
       (if (:avaa-massa-lomake? app)
         [massa-lomake e! app]
         [materiaalikirjasto e! app])])))



(defn materiaalikirjasto-modal [_ _]
  [modal/modal
   {:otsikko (str "Urakan materiaalikirjasto - " (:nimi @nav/valittu-urakka))
    :luokka "materiaalikirjasto-modal"
    :nakyvissa? @tiedot-massa/nayta-materiaalikirjasto?
    :sulje-fn #(swap! tiedot-massa/nayta-materiaalikirjasto? not)}
   [:div
    [tuck/tuck tila/pot2 massat*]]])


