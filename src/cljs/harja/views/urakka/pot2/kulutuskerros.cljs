(ns harja.views.urakka.pot2.kulutuskerros
  "POT2-lomakkeen kulutuskerros"
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
    [harja.ui.komponentti :as komp]
    [harja.ui.lomake :as lomake]
    [harja.ui.napit :as napit]
    [harja.ui.ikonit :as ikonit]
    [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
    [harja.tiedot.navigaatio :as nav]
    [harja.tiedot.urakka.paallystys :as paallystys]
    [harja.tiedot.urakka.yllapitokohteet :as yllapitokohteet]
    [harja.tiedot.urakka.pot2.materiaalikirjasto :as mk-tiedot]
    [harja.tiedot.urakka.pot2.pot2-tiedot :as pot2-tiedot]
    [harja.views.urakka.pot2.paallyste-ja-alusta-yhteiset :as pot2-yhteiset])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))


(defn validoi-kulutuskerros
  [rivi taulukko]
  (println "validoi-kulutuskerros rivi" (pr-str rivi))
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

(defn kulutuskerros
  "Alikohteiden päällysteiden kulutuskerroksen rivien muokkaus"
  [e! {:keys [kirjoitusoikeus? perustiedot] :as app}
   {:keys [massat massatyypit materiaalikoodistot validointi]} kohdeosat-atom]
  (let [perusleveys 2
        kulutuskerros-toimenpiteet (:kulutuskerros-toimenpiteet materiaalikoodistot)]
    [grid/muokkaus-grid
     {:otsikko "Kulutuskerros" :tunniste :kohdeosa-id :rivinumerot? true
      :voi-kumota? false :lisaa-rivi " Lisää toimenpide"
      :uusi-rivi (fn [rivi]
                   (assoc rivi
                     :tr-numero (:tr-numero perustiedot)))
      :piilota-toiminnot? true
      :rivi-validointi (:rivi validointi)
      :taulukko-validointi (:taulukko validointi)
      ;; Gridin renderöinnin jälkeen lasketaan alikohteiden pituudet
      :luomisen-jalkeen (fn [grid-state]
                          (paallystys/hae-osan-pituudet grid-state paallystys/tr-osien-tiedot))
      :tyhja (if (nil? @kohdeosat-atom) [ajax-loader "Haetaan kohdeosia..."]
                                        [:div
                                         [:div {:style {:display "inline-block"}} "Ei kohdeosia"]
                                         (when kirjoitusoikeus?
                                           [:div {:style {:display "inline-block"
                                                          :float "right"}}
                                            [napit/yleinen-ensisijainen "Lisää osa"
                                             #(reset! kohdeosat-atom (yllapitokohteet/lisaa-uusi-kohdeosa @kohdeosat-atom 1 (get-in app [:perustiedot :tr-osoite])))
                                             {:ikoni (ikonit/livicon-arrow-down)
                                              :luokka "btn-xs"}]])])
      :rivi-klikattu #(log "click")}
     [{:otsikko "Toimen\u00ADpide" :nimi :toimenpide :leveys perusleveys
       :tyyppi :valinta :valinnat kulutuskerros-toimenpiteet :valinta-arvo ::pot2-domain/koodi
       :valinta-nayta ::pot2-domain/lyhenne :validoi [[:ei-tyhja "Anna arvo"]]}
      {:otsikko "Tie" :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true
       :leveys perusleveys :nimi :tr-numero :validoi (:tr-numero validointi)}
      {:otsikko "Ajor." :nimi :tr-ajorata :tyyppi :valinta :leveys perusleveys
       :valinnat pot/+ajoradat-numerona+ :valinta-arvo :koodi
       :valinta-nayta (fn [rivi] (if rivi (:nimi rivi) "- Valitse Ajorata -"))
       :tasaa :oikea :kokonaisluku? true :validoi [[:ei-tyhja "Anna arvo"]]}
      {:otsikko "Kaista" :nimi :tr-kaista :tyyppi :valinta :leveys perusleveys
       :valinnat pot/+kaistat+ :valinta-arvo :koodi
       :valinta-nayta (fn [rivi]
                        (if rivi
                          (:nimi rivi)
                          "- Valitse kaista -"))
       :tasaa :oikea :kokonaisluku? true :validoi [[:ei-tyhja "Anna arvo"]]}
      {:otsikko "Aosa" :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true
       :leveys perusleveys :nimi :tr-alkuosa :validoi (:tr-alkuosa validointi)}
      {:otsikko "Aet" :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true
       :leveys perusleveys :nimi :tr-alkuetaisyys :validoi (:tr-alkuetaisyys validointi)}
      {:otsikko "Losa" :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true
       :leveys perusleveys :nimi :tr-loppuosa :validoi (:tr-loppuosa validointi)}
      {:otsikko "Let" :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true
       :leveys perusleveys :nimi :tr-loppuetaisyys :validoi (:tr-loppuetaisyys validointi)}
      {:otsikko "Pit. (m)" :nimi :pituus :leveys perusleveys :tyyppi :numero :tasaa :oikea
       :muokattava? (constantly false)
       :hae #(paallystys/rivin-kohteen-pituus
               (paallystys/tien-osat-riville % paallystys/tr-osien-tiedot) %) :validoi [[:ei-tyhja "Anna arvo"]]}
      {:otsikko "Pääl\u00ADlyste *)" :nimi :materiaali :leveys 3
       :tyyppi :valinta :valinnat massat :valinta-arvo ::pot2-domain/massa-id
       :valinta-nayta (fn [rivi]
                        (mk-tiedot/massan-rikastettu-nimi massatyypit rivi :string)) :validoi [[:ei-tyhja "Anna arvo"]]}
      {:otsikko "Leveys (m)" :nimi :leveys :tyyppi :positiivinen-numero :tasaa :oikea
       :leveys perusleveys :validoi [[:ei-tyhja "Anna arvo"]]}
      {:otsikko "Kok.m. (t)" :nimi :kokonaismassamaara :tyyppi :positiivinen-numero :tasaa :oikea
       :leveys perusleveys :validoi [[:ei-tyhja "Anna arvo"]]}
      {:otsikko "Pinta-ala (m²)" :nimi :pinta_ala :tyyppi :positiivinen-numero :tasaa :oikea
       :leveys perusleveys :validoi [[:ei-tyhja "Anna arvo"]]}
      {:otsikko "Massa\u00ADmenekki (kg/m\u00B2)" :nimi :massamenekki :tyyppi :positiivinen-numero :tasaa :oikea
       :leveys perusleveys :validoi [[:ei-tyhja "Anna arvo"]]}
      {:otsikko "Pien\u00ADnar" :nimi :piennar :leveys 1 :tyyppi :checkbox :hae (fn [rivi]
                                                                                  (boolean (:piennar rivi)))}
      {:otsikko "" :nimi :kulutuskerros-toiminnot :tyyppi :reagent-komponentti :leveys perusleveys
       :tasaa :keskita :komponentti-args [e! app kirjoitusoikeus? kohdeosat-atom :paallystekerros]
       :komponentti pot2-yhteiset/rivin-toiminnot-sarake}]
     kohdeosat-atom]))