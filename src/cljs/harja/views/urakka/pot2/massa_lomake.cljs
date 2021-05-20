(ns harja.views.urakka.pot2.massa-lomake
  "POT2 materiaalikirjaston massalomake"
  (:require [clojure.string :as str]
            [cljs.core.async :refer [<! chan]]
            [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]

            [harja.ui.grid :as grid]
            [harja.ui.debug :refer [debug]]
            [harja.ui.dom :as dom]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :as kentat]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko virheen-ohje] :as yleiset]
            [harja.domain.paallystysilmoitus :as paallystysilmoitus-domain]
            [harja.domain.pot2 :as pot2-domain]
            [harja.tiedot.urakka.pot2.validoinnit :as pot2-validoinnit]
            [harja.tiedot.urakka.pot2.materiaalikirjasto :as mk-tiedot]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.tiedot.urakka.pot2.pot2-tiedot :as pot2-tiedot]
            [harja.validointi :as v]
            [harja.views.urakka.pot2.massa-ja-murske-yhteiset :as mm-yhteiset]
            [harja.ui.komponentti :as komp]
            [harja.fmt :as fmt])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

(defn- aineen-otsikko-checkbox
  [e! {:keys [otsikko arvo label-luokka polku]}]
  [kentat/tee-kentta
   {:tyyppi :checkbox :label-luokka label-luokka
    :disabled? (= polku [:harja.domain.pot2/sideaineet :lopputuote :valittu?])
    :teksti otsikko :vayla-tyyli? true
    :nayta-rivina? true :iso-clickalue? true}
   (r/wrap
     arvo
     (fn [uusi-arvo]
       (e! (mk-tiedot/->PaivitaAineenTieto polku uusi-arvo))))])

(defn- otsikko-ja-kentta
  [e! {:keys [otsikko tyyppi arvo polku pakollinen? leveys placeholder
              valinnat valinta-nayta valinta-arvo validoi-kentta-fn
              desimaalien-maara kokonaisluku? elementin-id]}]
  [:div.otsikko-ja-kentta.inline-block
   [:div
    [:span.kentan-label otsikko]
    (when pakollinen? [:span.required-tahti " *"])]
   [kentat/tee-kentta {:tyyppi tyyppi :teksti otsikko :nayta-rivina? false
                       :leveys leveys :placeholder placeholder
                       :valinnat valinnat :valinta-nayta valinta-nayta
                       :valinta-arvo valinta-arvo
                       :vayla-tyyli? true
                       :validoi-kentta-fn validoi-kentta-fn
                       :desimaalien-maara desimaalien-maara
                       :kokonaisluku? kokonaisluku?
                       :elementin-id elementin-id}
    (r/wrap
      arvo
      (fn [uusi-arvo]
        (e! (mk-tiedot/->PaivitaAineenTieto polku uusi-arvo))))]])

(defn- runkoaineiden-kentat [tiedot polun-avaimet]
  (let [{:runkoaine/keys [esiintyma fillerityyppi kuulamyllyarvo litteysluku
                          massaprosentti kuvaus]} tiedot
        aineen-koodi (second polun-avaimet)
        kyseessa-filleri? (= aineen-koodi pot2-domain/+runkoainetyyppi-filleri+)]
    ;; Käyttöliittymäsuunnitelman mukaisesti tietyillä runkoaineilla on tietyt kentät
    ;; ja ainekohtaisia eroja käsitellään tässä contains? funktion avulla
    (remove
      nil?
      [(when-not (contains? #{pot2-domain/+runkoainetyyppi-filleri+ pot2-domain/+runkoainetyyppi-muu+}
                            aineen-koodi)
         {:otsikko (if (= aineen-koodi pot2-domain/+runkoainetyyppi-asfalttirouhe+)
                     "Esiintymä / Lähde"
                     "Kiviainesesiintymä")
          :tyyppi :string :pakollinen? true
          :arvo esiintyma :leveys "150px"
          :polku (conj polun-avaimet :runkoaine/esiintyma)})
       (when (contains? #{pot2-domain/+runkoainetyyppi-filleri+} aineen-koodi)
         {:otsikko "Tyyppi" :valinnat pot2-domain/erikseen-lisattava-fillerikiviaines-valinnat
          :tyyppi :valinta :pakollinen? true
          :valinta-nayta #(or % yleiset/valitse-text)
          :arvo fillerityyppi :leveys "150px"
          :polku (conj polun-avaimet :runkoaine/fillerityyppi)})
       (when-not (contains? #{pot2-domain/+runkoainetyyppi-filleri+ pot2-domain/+runkoainetyyppi-muu+}
                            aineen-koodi)
         {:otsikko "Kuulamyllyarvo" :pakollinen? true
          :tyyppi :numero :desimaalien-maara 1
          :arvo kuulamyllyarvo :leveys "55px"
          :validoi-kentta-fn (fn [numero] (v/validoi-numero numero 0 40 1))
          :polku (conj polun-avaimet :runkoaine/kuulamyllyarvo)})
       (when-not (contains? #{pot2-domain/+runkoainetyyppi-filleri+ pot2-domain/+runkoainetyyppi-muu+}
                            aineen-koodi)
         {:otsikko "Litteysluku" :pakollinen? true
          :tyyppi :numero :desimaalien-maara 1
          :arvo litteysluku :leveys "68px"
          :validoi-kentta-fn (fn [numero] (v/validoi-numero numero 0 40 1))
          :polku (conj polun-avaimet :runkoaine/litteysluku)})
       (when (contains? #{pot2-domain/+runkoainetyyppi-muu+} aineen-koodi)
         {:otsikko "Kuvaus" :placeholder "Aineen nimi"
          :tyyppi :string :pakollinen? true
          :arvo kuvaus :leveys "160px"
          :polku (conj polun-avaimet :runkoaine/kuvaus)})
       {:otsikko "Massa-%" :pakollinen? true
        :tyyppi :numero :kokonaisluku? (not kyseessa-filleri?)
        :arvo massaprosentti :leveys "55px"
        :desimaalien-maara (if kyseessa-filleri? 1 0)
        :validoi-kentta-fn (fn [numero]
                             (v/validoi-numero numero 0 100
                                               (if kyseessa-filleri?
                                                 1
                                                 0)))
        :polku (conj polun-avaimet :runkoaine/massaprosentti)}])))

(defn- sideaineiden-kentat [tiedot polun-avaimet idx]
  (let [{:sideaine/keys [tyyppi pitoisuus]
         sideainetyypit :sideainetyypit} tiedot]
    [{:otsikko "Tyyppi" :valinnat sideainetyypit
      :tyyppi :valinta :pakollinen? true
      :valinta-nayta (fn [%]
                       (if %
                         (::pot2-domain/nimi %)
                         yleiset/valitse-text))
      :valinta-arvo ::pot2-domain/koodi
      :vayla-tyyli? true
      :arvo tyyppi :leveys "250px"
      :elementin-id (str tyyppi idx)
      :polku (conj polun-avaimet :aineet idx :sideaine/tyyppi)}
     {:otsikko "Pitoisuus %"
      :tyyppi :numero :pakollinen? true
      :arvo pitoisuus :leveys "55px"
      :desimaalien-maara 1
      :validoi-kentta-fn (fn [numero] (v/validoi-numero numero 0 100 1))
      :polku (conj polun-avaimet :aineet idx :sideaine/pitoisuus)}]))

(defn- lisaaineiden-kentat [tiedot polun-avaimet]
  (let [{:lisaaine/keys [pitoisuus]} tiedot]
    [{:otsikko "Pitoisuus %" :pakollinen? true
      :tyyppi :numero :desimaalien-maara 1
      :arvo pitoisuus :leveys "70px"
      :validoi-kentta-fn (fn [numero] (v/validoi-numero numero 0 100 1))
      :polku (conj polun-avaimet :lisaaine/pitoisuus)}]))

(defn- tyypin-kentat [{:keys [tyyppi tiedot polun-avaimet]}]
  (case tyyppi
    :runkoaineet (runkoaineiden-kentat tiedot polun-avaimet)
    :sideaineet (sideaineiden-kentat tiedot polun-avaimet 0)
    :lisaaineet (lisaaineiden-kentat tiedot polun-avaimet)

    nil))


(defn- sideaineet-komponentti [e! rivi kayttotapa polun-avaimet sideainetyypit]
  (let [aineet (or (get-in rivi (cons :data [::pot2-domain/sideaineet kayttotapa :aineet]))
                   {0 mk-tiedot/tyhja-sideaine})
        jo-valitut-sideainetyypit (into #{} (map :sideaine/tyyppi (vals aineet)))]
    [:div.sideaine-komponentti
     (map-indexed (fn [idx [_ sideaine]]
                    ;; Tässä tarjotaan käyttäjälle mahdollisena sideainetyyppinä kaikki sellaiset, joita ei ole vielä ko. massalle
                    ;; valittu.
                    (let [sideainetyyppivalinnat (vec (remove #(and (jo-valitut-sideainetyypit (::pot2-domain/koodi %))
                                                                    ;;  Jotta nykyinen valinta näkyy, se pitää jättää poistamatta
                                                                    (not= (::pot2-domain/koodi %)
                                                                          (:sideaine/tyyppi sideaine)))
                                                              sideainetyypit))]
                      ^{:key (str idx)}
                      [:div
                       (for [sak (sideaineiden-kentat (merge
                                                        sideaine
                                                        {:sideainetyypit sideainetyyppivalinnat})
                                                      polun-avaimet idx)]

                         ^{:key (str idx (:otsikko sak))}
                         [:div.inline-block {:style {:margin-right "6px"}}
                          [otsikko-ja-kentta e! sak]])]))
                  aineet)
     (when (= :lisatty kayttotapa)
       [:div
        [napit/uusi "Lisää uusi"
         #(e! (mk-tiedot/->LisaaSideaine kayttotapa))
         {:luokka (str "napiton-nappi lisaa-sideaine")}]
        [napit/poista "Poista viimeisin"
         #(e! (mk-tiedot/->PoistaSideaine kayttotapa))
         {:luokka (str "napiton-nappi poista-sideaine")}]])]))

(defn- ainevalintalaatikot [tyyppi aineet]
  (if (= tyyppi :sideaineet)
    mk-tiedot/sideaineen-kayttotavat
    aineet))

(defn- runko-ja-lisa-aineen-lukutila
  [tiedot aineen-otsikko]
  (let [{:runkoaine/keys [massaprosentti kuulamyllyarvo litteysluku esiintyma]
         :lisaaine/keys [pitoisuus]} tiedot]
    [:div.aineen-pitoisuus-ja-nimi
     [:div.pitoisuus (str (or massaprosentti pitoisuus) " %")]
     [:div.nimi-ja-yksityiskohdat
      [:div.nimi aineen-otsikko]
      [:div.massan-yksityiskohdat
       (when kuulamyllyarvo [:span.yksityiskohta (str "KM-arvo " kuulamyllyarvo)])
       (when litteysluku [:span.yksityiskohta (str "Litteysluku " litteysluku)])
       (when esiintyma [:span.yksityiskohta esiintyma])]]]))

(defn- sideaineen-lukutila
  [tiedot aineen-otsikko ainetyypit]
  [:div
   [:div (str aineen-otsikko)]
   [:div.sideaineiden-lukutila
    (for [aine (reverse
                 (sort-by :sideaine/pitoisuus
                          (vals (:aineet tiedot))))
          :let [aineen-nimi (str (pot2-domain/ainetyypin-koodi->nimi ainetyypit (:sideaine/tyyppi aine)))]]
      ^{:key aine}
      [:div.aineen-pitoisuus-ja-nimi
       [:div.pitoisuus (str (:sideaine/pitoisuus aine) " %")]
       [:div.nimi-ja-yksityiskohdat
        [:div.nimi aineen-nimi]]])]])

(defn- ainevalinta-kentat [e! {:keys [rivi tyyppi aineet voi-muokata?] :as opts}]
  [:div.ainevalinta-kentat
   (let [aineet (mk-tiedot/jarjesta-aineet-tarvittaessa opts)]
     (for [t (ainevalintalaatikot tyyppi aineet)]
       (let [polun-avaimet [(keyword "harja.domain.pot2" (name tyyppi)) (::pot2-domain/koodi t)]
             {:keys [valittu?] :as tiedot} (get-in rivi (cons :data polun-avaimet))]
         (if voi-muokata?
           ^{:key t}
           [:div {:class (str "aineiden-muokkaustila ainevalinta " (when valittu? "valittu"))}
            (when (or (not= polun-avaimet [:harja.domain.pot2/sideaineet :lisatty])
                      (mk-tiedot/lisatty-sideaine-mahdollinen? (:data rivi)))
              [aineen-otsikko-checkbox e! {:otsikko (::pot2-domain/nimi t)
                                           :arvo valittu? :label-luokka (when valittu? "bold")
                                           :polku (conj polun-avaimet :valittu?)}])

            (when valittu?
              [:div.kentat-haitari
               (case tyyppi
                 (:runkoaineet :lisaaineet)
                 (for [k (tyypin-kentat {:tyyppi tyyppi
                                         :tiedot tiedot
                                         :polun-avaimet polun-avaimet
                                         :voi-muokata? voi-muokata?})]
                   ^{:key (:otsikko k)}
                   [:div.inline-block {:style {:margin-right "6px"}}
                    [otsikko-ja-kentta e! k]])

                 :sideaineet
                 [sideaineet-komponentti e! rivi (::pot2-domain/koodi t)
                  polun-avaimet aineet])])]
           (when valittu?
             ^{:key t}
             [:div {:class (str "aineiden-lukutila " (name tyyppi))}
              (case tyyppi
                (:runkoaineet :lisaaineet)
                [runko-ja-lisa-aineen-lukutila tiedot (::pot2-domain/nimi t)]

                :sideaineet
                [sideaineen-lukutila tiedot (::pot2-domain/nimi t) aineet])])))))])


(defn massa-lomake [e! {:keys [pot2-massa-lomake materiaalikoodistot] :as app}]
  (let [saa-sulkea? (atom false)
        muokkaustilassa? (atom false)]
    (komp/luo
      (komp/piirretty #(do
                         (e! (mk-tiedot/->HaePot2MassatJaMurskeet))
                         (yleiset/fn-viiveella (fn [] (reset! saa-sulkea? true)))))
      (komp/klikattu-ulkopuolelle #(when (and @saa-sulkea?
                                              (not @muokkaustilassa?))
                                     (e! (pot2-tiedot/->SuljeMateriaalilomake)))
                                  {:tarkista-komponentti? true})
      (fn [e! {:keys [pot2-massa-lomake materiaalikoodistot] :as app}]
        (let [{:keys [massatyypit runkoainetyypit sideainetyypit lisaainetyypit]} materiaalikoodistot
              sivulle? (:sivulle? pot2-massa-lomake)
              massa-id (::pot2-domain/massa-id pot2-massa-lomake)
              muut-validointivirheet (pot2-validoinnit/runko-side-ja-lisaaineen-validointivirheet pot2-massa-lomake materiaalikoodistot)
              materiaali-kaytossa (::pot2-domain/kaytossa pot2-massa-lomake)
              materiaali-lukittu? (some #(str/includes? (:tila %) "lukittu") materiaali-kaytossa)
              voi-muokata? (and
                             (not materiaali-lukittu?)
                             (if (contains? pot2-massa-lomake :voi-muokata?)
                               (:voi-muokata? pot2-massa-lomake)
                               true))]
          (when voi-muokata? (reset! muokkaustilassa? true))
          [:div.massa-lomake
           [lomake/lomake
            {:muokkaa! #(e! (mk-tiedot/->PaivitaMassaLomake (lomake/ilman-lomaketietoja %)))
             :luokka (when sivulle? "overlay-oikealla overlay-leveampi") :voi-muokata? voi-muokata?
             :sulje-fn (when sivulle? #(e! (pot2-tiedot/->SuljeMateriaalilomake)))
             :otsikko-komp (fn [data]
                             [:div.lomake-otsikko-pieni (cond
                                                          (false? voi-muokata?)
                                                          "Massan tiedot"

                                                          massa-id
                                                          "Muokkaa massaa"

                                                          :else
                                                          "Uusi massa")])
             :footer-fn (fn [data]
                          [mm-yhteiset/tallennus-ja-puutelistaus e! {:data data
                                                                     :validointivirheet muut-validointivirheet
                                                                     :tallenna-fn #(e! (mk-tiedot/->TallennaLomake data))
                                                                     :voi-tallentaa? (or (not (lomake/voi-tallentaa? data))
                                                                                         (not (empty? muut-validointivirheet)))
                                                                     :peruuta-fn #(e! (mk-tiedot/->TyhjennaLomake))
                                                                     :poista-fn #(e! (mk-tiedot/->TallennaLomake (merge data {::pot2-domain/poistettu? true})))
                                                                     :tyyppi :massa
                                                                     :id massa-id
                                                                     :materiaali-kaytossa materiaali-kaytossa
                                                                     :voi-muokata? voi-muokata?}])
             :vayla-tyyli? true}
            [{:otsikko "" :muokattava? (constantly false) :nimi ::pot2-domain/massan-nimi :tyyppi :string :palstoja 3
              :piilota-label? true :vayla-tyyli? true :kentan-arvon-luokka "fontti-20"
              :hae (fn [rivi]
                     (if-not (::pot2-domain/tyyppi rivi)
                       "Nimi muodostuu automaattisesti lomakkeeseen täytettyjen tietojen perusteella"
                       (mm-yhteiset/materiaalin-rikastettu-nimi {:tyypit massatyypit
                                                                 :materiaali rivi
                                                                 :fmt :string})))}
             (when (and (not voi-muokata?)
                        (not materiaali-lukittu?))
               (mm-yhteiset/muokkaa-nappi #(e! (mk-tiedot/->AloitaMuokkaus :pot2-massa-lomake))))
             (when-not voi-muokata? (lomake/lomake-spacer {}))
             (lomake/rivi
               {:otsikko "Massatyyppi" :alasveto-luokka "massatyyppi-alasveto"
                :nimi ::pot2-domain/tyyppi :tyyppi :valinta
                :valinta-nayta ::pot2-domain/nimi :valinta-arvo ::pot2-domain/koodi :valinnat massatyypit
                ::lomake/col-luokka "col-sm-4"
                :pakollinen? true :vayla-tyyli? true}
               {:otsikko "Max raekoko" :nimi ::pot2-domain/max-raekoko
                :tyyppi :valinta
                :valinta-nayta (fn [rivi]
                                 (str rivi))
                :vayla-tyyli? true
                :valinta-arvo identity :valinnat pot2-domain/massan-max-raekoko
                ::lomake/col-luokka "col-sm-4"
                :pakollinen? true}
               {:otsikko "Nimen tarkenne" :nimi ::pot2-domain/nimen-tarkenne :tyyppi :string
                ::lomake/col-luokka "col-sm-4" :vayla-tyyli? true})
             (lomake/rivi
               {:otsikko "Kuulamyllyluokka"
                :nimi ::pot2-domain/kuulamyllyluokka
                :tyyppi :valinta :valinta-nayta (fn [rivi]
                                                  (str (:nimi rivi)))
                :vayla-tyyli? true :valinta-arvo :nimi
                :valinnat paallystysilmoitus-domain/+kyylamyllyt-ja-nil+
                ::lomake/col-luokka "col-sm-4"
                :pakollinen? true}
               {:otsikko "Litteyslukuluokka"
                :nimi ::pot2-domain/litteyslukuluokka :tyyppi :valinta
                :valinta-nayta (fn [rivi]
                                 (str rivi))
                :vayla-tyyli? true
                :valinta-arvo identity :valinnat pot2-domain/litteyslukuluokat
                ::lomake/col-luokka "col-sm-4"
                :pakollinen? true}
               {:otsikko "DoP" :nimi ::pot2-domain/dop-nro :tyyppi :string
                :validoi [[:ei-tyhja "Anna DoP nro"]]
                ::lomake/col-luokka "col-sm-4"
                :vayla-tyyli? true :pakollinen? true})

             (when voi-muokata? (lomake/lomake-spacer {}))

             {:nimi ::pot2-domain/runkoaineet :otsikko "Runkoaineen materiaali" :tyyppi :komponentti :palstoja 3
              :kentan-arvon-luokka "text-uppercase"
              :kaariva-luokka (str "mk-aine " (when-not voi-muokata? "lukutila"))
              :komponentti (fn [rivi]
                             [ainevalinta-kentat e! {:rivi rivi
                                                     :tyyppi :runkoaineet
                                                     :aineet runkoainetyypit
                                                     :voi-muokata? voi-muokata?}])}
             {:nimi ::pot2-domain/sideaineet :otsikko "Sideaineet" :tyyppi :komponentti :palstoja 3
              :kaariva-luokka (str "mk-aine " (when-not voi-muokata? "lukutila"))
              :komponentti (fn [rivi] [ainevalinta-kentat e! {:rivi rivi
                                                              :tyyppi :sideaineet
                                                              :aineet sideainetyypit
                                                              :voi-muokata? voi-muokata?}])}
             {:nimi ::pot2-domain/lisaaineet :otsikko "Lisäaineet" :tyyppi :komponentti :palstoja 3
              :kaariva-luokka (str "mk-aine " (when-not voi-muokata? "lukutila"))
              :komponentti (fn [rivi] [ainevalinta-kentat e! {:rivi rivi
                                                              :tyyppi :lisaaineet
                                                              :aineet lisaainetyypit
                                                              :voi-muokata? voi-muokata?}])}
             (lomake/lomake-spacer {})]

            pot2-massa-lomake]])))))