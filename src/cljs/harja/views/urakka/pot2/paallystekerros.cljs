(ns harja.views.urakka.pot2.paallystekerros
  "POT2-lomakkeen päällystekerros"
  (:require
    [reagent.core :refer [atom] :as r]
    [harja.domain.oikeudet :as oikeudet]
    [harja.domain.paallystysilmoitus :as pot]
    [harja.domain.pot2 :as pot2-domain]
    [harja.domain.tierekisteri :as tr]
    [harja.domain.yllapitokohde :as yllapitokohteet-domain]
    [harja.loki :refer [log]]
    [harja.ui.debug :refer [debug]]
    [harja.ui.grid :as grid]
    [harja.ui.napit :as napit]
    [harja.ui.ikonit :as ikonit]
    [harja.ui.yleiset :refer [ajax-loader]]
    [harja.tiedot.navigaatio :as nav]
    [harja.tiedot.urakka.paallystys :as paallystys]
    [harja.tiedot.urakka.yllapitokohteet :as yllapitokohteet]
    [harja.views.urakka.pot2.paallyste-ja-alusta-yhteiset :as pot2-yhteiset]
    [harja.views.urakka.pot2.massa-ja-murske-yhteiset :as mm-yhteiset]
    [harja.tiedot.urakka.pot2.pot2-tiedot :as pot2-tiedot]
    [harja.tiedot.urakka.pot2.materiaalikirjasto :as mk-tiedot]
    [harja.ui.yleiset :as yleiset])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))


(defn validoi-paallystekerros
  [rivi taulukko]
  (let [{:keys [perustiedot tr-osien-tiedot]} (:paallystysilmoitus-lomakedata @paallystys/tila)
        paakohde (select-keys perustiedot tr/paaluvali-avaimet)
        vuosi 2021 ;; riittää pot2:lle aina
        ;; Kohteiden päällekkyys keskenään validoidaan taulukko tasolla, jotta rivin päivittäminen oikeaksi korjaa
        ;; myös toisilla riveillä olevat validoinnit.
        validoitu (if (= (:tr-numero paakohde) (:tr-numero rivi))
                    (yllapitokohteet-domain/validoi-alikohde paakohde rivi [] (get tr-osien-tiedot (:tr-numero rivi)) vuosi)
                    (yllapitokohteet-domain/validoi-muukohde paakohde rivi [] (get tr-osien-tiedot (:tr-numero rivi)) vuosi))]
    (yllapitokohteet-domain/validoitu-kohde-tekstit (dissoc validoitu :alikohde-paallekkyys :muukohde-paallekkyys) false)))

(defn kohde-toisten-kanssa-paallekkain-validointi
  [alikohde? _ rivi taulukko]
  (let [toiset-alikohteet (keep (fn [[indeksi kohdeosa]]
                                  (when (and (:tr-alkuosa kohdeosa) (:tr-alkuetaisyys kohdeosa)
                                             (:tr-loppuosa kohdeosa) (:tr-loppuetaisyys kohdeosa)
                                             (not= kohdeosa rivi))
                                    kohdeosa))
                                taulukko)
        paallekkyydet (filter #(yllapitokohteet-domain/tr-valit-paallekkain? rivi %)
                              toiset-alikohteet)]
    (yllapitokohteet-domain/validoitu-kohde-tekstit {:alikohde-paallekkyys
                                                     paallekkyydet}
                                                    (not alikohde?))))

(def gridin-perusleveys 2)

(defn paallystekerros
  "Alikohteiden päällystekerroksen rivien muokkaus"
  [e! {:keys [kirjoitusoikeus? perustiedot] :as app}
   {:keys [massat materiaalikoodistot validointi]} kohdeosat-atom]
  (let [voi-muokata? (not= :lukittu (:tila perustiedot))]
    [grid/muokkaus-grid
     {:otsikko "Kulutuskerros" :tunniste :kohdeosa-id :rivinumerot? true
      :voi-muokata? voi-muokata? :voi-lisata? false
      :voi-kumota? false
      :muutos #(e! (pot2-tiedot/->Pot2Muokattu))
      :custom-toiminto {:teksti "Lisää toimenpide"
                        :toiminto #(e! (pot2-tiedot/->LisaaPaallysterivi kohdeosat-atom))
                        :opts {:ikoni (ikonit/livicon-plus)
                               :luokka "nappi-toissijainen"}}
      :piilota-toiminnot? true
      :rivi-validointi (:rivi validointi)
      :taulukko-validointi (:taulukko validointi)
      ;; Gridin renderöinnin jälkeen lasketaan alikohteiden pituudet
      :luomisen-jalkeen (fn [grid-state]
                          (paallystys/hae-osan-pituudet grid-state paallystys/tr-osien-tiedot))
      :tyhja (if (nil? @kohdeosat-atom)
               [ajax-loader "Haetaan kohdeosia..."]
               [yleiset/vihje "Aloita painamalla Lisää toimenpide -painiketta."])}
     [{:otsikko "Toimen\u00ADpide" :nimi :toimenpide :tayta-alas? pot2-tiedot/tayta-alas?-fn
       :tyyppi :valinta :valinnat (:paallystekerros-toimenpiteet materiaalikoodistot) :valinta-arvo ::pot2-domain/koodi
       :valinta-nayta ::pot2-domain/lyhenne :validoi [[:ei-tyhja "Anna arvo"]]
       :leveys (:toimenpide pot2-yhteiset/gridin-leveydet)}
      {:otsikko "Tie" :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true
       :leveys (:perusleveys pot2-yhteiset/gridin-leveydet) :nimi :tr-numero :validoi (:tr-numero validointi)}
      {:otsikko "Ajor." :nimi :tr-ajorata :tyyppi :valinta :leveys (:perusleveys pot2-yhteiset/gridin-leveydet)
       :valinnat pot/+ajoradat-numerona+ :valinta-arvo :koodi
       :valinta-nayta (fn [rivi] (if rivi (:nimi rivi) "- Valitse Ajorata -"))
       :tasaa :oikea :kokonaisluku? true :validoi [[:ei-tyhja "Anna arvo"]]}
      {:otsikko "Kaista" :nimi :tr-kaista :tyyppi :valinta :leveys (:perusleveys pot2-yhteiset/gridin-leveydet)
       :valinnat pot/+kaistat+ :valinta-arvo :koodi
       :valinta-nayta (fn [rivi]
                        (if rivi
                          (:nimi rivi)
                          "- Valitse kaista -"))
       :tasaa :oikea :kokonaisluku? true :validoi [[:ei-tyhja "Anna arvo"]]}
      {:otsikko "Aosa" :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true
       :leveys (:perusleveys pot2-yhteiset/gridin-leveydet) :nimi :tr-alkuosa :validoi (:tr-alkuosa validointi)}
      {:otsikko "Aet" :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true
       :leveys (:perusleveys pot2-yhteiset/gridin-leveydet) :nimi :tr-alkuetaisyys :validoi (:tr-alkuetaisyys validointi)}
      {:otsikko "Losa" :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true
       :leveys (:perusleveys pot2-yhteiset/gridin-leveydet) :nimi :tr-loppuosa :validoi (:tr-loppuosa validointi)}
      {:otsikko "Let" :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true
       :leveys (:perusleveys pot2-yhteiset/gridin-leveydet) :nimi :tr-loppuetaisyys :validoi (:tr-loppuetaisyys validointi)}
      {:otsikko "Pituus" :nimi :pituus :leveys (:perusleveys pot2-yhteiset/gridin-leveydet) :tyyppi :numero :tasaa :oikea
       :muokattava? (constantly false)
       :hae #(paallystys/rivin-kohteen-pituus
               (paallystys/tien-osat-riville % paallystys/tr-osien-tiedot) %) :validoi [[:ei-tyhja "Anna arvo"]]}
      {:otsikko "Pääl\u00ADlyste" :nimi :materiaali :leveys (:materiaali pot2-yhteiset/gridin-leveydet) :tayta-alas? pot2-tiedot/tayta-alas?-fn
       :tyyppi :valinta :valinnat massat :valinta-arvo ::pot2-domain/massa-id
       :linkki-fn (fn [arvo]
                    (e! (pot2-tiedot/->NaytaMateriaalilomake {::pot2-domain/massa-id arvo})))
       :linkki-icon (ikonit/livicon-external)
       :valinta-nayta (fn [rivi]
                        (if (empty? massat)
                          [:div.neutraali-tausta "Lisää massa"]
                          [:div.pot2-paallyste
                           [mm-yhteiset/materiaalin-rikastettu-nimi {:tyypit (:massatyypit materiaalikoodistot)
                                                                     :materiaali (pot2-tiedot/rivi->massa-tai-murske rivi {:massat massat})
                                                                     :fmt :komponentti}]]))
       :validoi [[:ei-tyhja "Anna arvo"]]}
      {:otsikko "Leveys (m)" :nimi :leveys :tyyppi :positiivinen-numero :tasaa :oikea
       :tayta-alas? pot2-tiedot/tayta-alas?-fn
       :leveys (:perusleveys pot2-yhteiset/gridin-leveydet) :validoi [[:ei-tyhja "Anna arvo"]]}
      {:otsikko "Kok.m. (t)" :nimi :kokonaismassamaara :tyyppi :positiivinen-numero :tasaa :oikea
       :leveys (:perusleveys pot2-yhteiset/gridin-leveydet) :validoi [[:ei-tyhja "Anna arvo"]]}
      {:otsikko "Pinta-ala (m²)" :nimi :pinta_ala :tyyppi :positiivinen-numero :tasaa :oikea
       :leveys (:perusleveys pot2-yhteiset/gridin-leveydet) :validoi [[:ei-tyhja "Anna arvo"]]}
      {:otsikko "Massa\u00ADmenekki (kg/m\u00B2)" :nimi :massamenekki :tyyppi :positiivinen-numero :tasaa :oikea
       :tayta-alas? pot2-tiedot/tayta-alas?-fn :leveys (:perusleveys pot2-yhteiset/gridin-leveydet)
       :validoi [[:ei-tyhja "Anna arvo"]]}
      {:otsikko "" :nimi :kulutuspaallyste-toiminnot :tyyppi :reagent-komponentti :leveys (:toiminnot pot2-yhteiset/gridin-leveydet)
       :tasaa :keskita :komponentti-args [e! app kirjoitusoikeus? kohdeosat-atom :paallystekerros voi-muokata?]
       :komponentti pot2-yhteiset/rivin-toiminnot-sarake}]
     kohdeosat-atom]))