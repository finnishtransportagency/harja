(ns harja.views.urakka.pot2.pot2-lomake
"POT2-lomake"
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
    [harja.tiedot.urakka.pot2.massat :as tiedot-massa]
    [harja.tiedot.urakka.pot2.pot2-tiedot :as pot2-tiedot]
    [harja.views.urakka.pot-yhteinen :as pot-yhteinen])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))


(defn- alusta [e! app]
  [:div "Alustatiedot"])

(defn- kulutuskerroksen-toiminnot-sarake
  [rivi osa e! app voi-muokata? kohdeosat-atom]
  (let [kohdeosat-muokkaa! (fn [uudet-kohdeosat-fn]
                             (let [vanhat-kohdeosat @kohdeosat-atom
                                   uudet-kohdeosat (uudet-kohdeosat-fn vanhat-kohdeosat)]
                               (swap! kohdeosat-atom (fn [_]
                                                       uudet-kohdeosat))))
        lisaa-osa-fn (fn [index]
                       (kohdeosat-muokkaa! (fn [vanhat-kohdeosat]
                                             (yllapitokohteet/lisaa-uusi-kohdeosa vanhat-kohdeosat (inc index) {}))))
        poista-osa-fn (fn [index]
                        (kohdeosat-muokkaa! (fn [vanhat-kohdeosat]
                                              (yllapitokohteet/poista-kohdeosa vanhat-kohdeosat (inc index)))))]
    (fn [rivi {:keys [index]} voi-muokata?]
      (let [yllapitokohde (-> app :paallystysilmoitus-lomakedata
                              :perustiedot
                              (select-keys [:tr-numero :tr-kaista :tr-ajorata :tr-alkuosa :tr-alkuetaisyys :tr-loppuosa :tr-loppuetaisyys]))]
        [:span.tasaa-oikealle
         [napit/yleinen-ensisijainen ""
          lisaa-osa-fn
          {:ikoni (ikonit/livicon-plus)
           :disabled (or (not (:kirjoitusoikeus? app))
                         (not voi-muokata?))
           :luokka "napiton-nappi btn-xs"
           :toiminto-args [index]}]
         [napit/kielteinen ""
          poista-osa-fn
          {:ikoni (ikonit/livicon-trash)
           :disabled (or (not (:kirjoitusoikeus? app))
                         (not voi-muokata?))
           :luokka "napiton-nappi btn-xs"
           :toiminto-args [index]}]])))
  )

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

(defn- kulutuskerros
  "Alikohteiden päällysteiden kulutuskerroksen rivien muokkaus"
  [e! {:keys [kirjoitusoikeus? perustiedot] :as app}
   {:keys [massat massatyypit materiaalikoodistot validointi]} kohdeosat-atom]
  (let [voi-muokata? true
        perusleveys 2
        kulutuskerros-toimenpiteet (:kulutuskerros-toimenpiteet materiaalikoodistot)]
    [grid/muokkaus-grid
     {:otsikko "Kulutuskerros" :tunniste :kohdeosa-id :rivinumerot? true
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
                                         (when (and kirjoitusoikeus? voi-muokata?)
                                           [:div {:style {:display "inline-block"
                                                          :float "right"}}
                                            [napit/yleinen-ensisijainen "Lisää osa"
                                             #(reset! kohdeosat-atom (yllapitokohteet/lisaa-uusi-kohdeosa @kohdeosat-atom 1 (get-in app [:perustiedot :tr-osoite])))
                                             {:ikoni (ikonit/livicon-arrow-down)
                                              :luokka "btn-xs"}]])])
      :rivi-klikattu #(log "click")}
     [{:otsikko "Toimen\u00ADpide" :nimi :toimenpide :leveys perusleveys
       :tyyppi :valinta :valinnat kulutuskerros-toimenpiteet :valinta-arvo ::pot2-domain/koodi
       :valinta-nayta ::pot2-domain/lyhenne}
      {:otsikko "Tie" :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true
       :leveys perusleveys :nimi :tr-numero :validoi (:tr-numero validointi)}
      {:otsikko "Ajor." :nimi :tr-ajorata :tyyppi :valinta :leveys perusleveys
       :valinnat pot/+ajoradat-numerona+ :valinta-arvo :koodi
       :valinta-nayta (fn [rivi] (if rivi (:nimi rivi) "- Valitse Ajorata -"))
       :tasaa :oikea :kokonaisluku? true}
      {:otsikko "Kaista" :nimi :tr-kaista :tyyppi :valinta :leveys perusleveys
       :valinnat pot/+kaistat+ :valinta-arvo :koodi
       :valinta-nayta (fn [rivi]
                        (if rivi
                          (:nimi rivi)
                          "- Valitse kaista -"))
       :tasaa :oikea :kokonaisluku? true}
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
               (paallystys/tien-osat-riville % paallystys/tr-osien-tiedot) %) }
      {:otsikko "Pääl\u00ADlyste" :nimi :materiaali :leveys 3
       :tyyppi :valinta :valinnat massat :valinta-arvo :pot2-massa/id
       :valinta-nayta (fn [rivi]
                        (pot2-domain/massatyypin-rikastettu-nimi massatyypit rivi))}
      {:otsikko "Leveys (m)" :nimi :leveys :tyyppi :positiivinen-numero :tasaa :oikea
       :leveys perusleveys}
      {:otsikko "Kok.m. (t)" :nimi :kokonaismassamaara :tyyppi :positiivinen-numero :tasaa :oikea
       :leveys perusleveys}
      {:otsikko "Pinta-ala (m²)" :nimi :pinta_ala :tyyppi :positiivinen-numero :tasaa :oikea
       :leveys perusleveys}
      {:otsikko "Massa\u00ADmenekki (kg/m\u00B2)" :nimi :massamenekki :tyyppi :positiivinen-numero :tasaa :oikea
       :leveys perusleveys}
      {:otsikko "Pien\u00ADnar" :nimi :piennar :leveys 1 :tyyppi :checkbox :hae (fn [rivi]
                                                                            (boolean (:piennar rivi)))}
      {:otsikko "Toiminnot" :nimi :kulutuskerros-toiminnot :tyyppi :reagent-komponentti :leveys perusleveys
       :tasaa :keskita :komponentti-args [e! app voi-muokata? kohdeosat-atom]
       :komponentti kulutuskerroksen-toiminnot-sarake}]
     kohdeosat-atom]))


(defn- otsikkotiedot [{:keys [tila] :as perustiedot}]
  [:span
   [:h1 (str "Päällystysilmoitus - "
                   (pot-yhteinen/paallystyskohteen-fmt perustiedot))]
   [:div
    [:div.inline-block.pot-tila {:class (when tila (name tila))}
     (if-not tila "Aloittamatta" tila)]]])

(defn tallenna
  [e! {:keys [tekninen-osa tila]}
   {:keys [kayttaja urakka-id valmis-tallennettavaksi?]}]
  (let [paatos-tekninen-osa (:paatos tekninen-osa)
        huomautusteksti
        (cond (and (not= :lukittu tila)
                   (= :hyvaksytty paatos-tekninen-osa))
              "Päällystysilmoitus hyväksytty, ilmoitus lukitaan tallennuksen yhteydessä."
              :default nil)]

    [:div.pot-tallennus
     (when huomautusteksti
       (lomake/yleinen-huomautus huomautusteksti))

     [napit/palvelinkutsu-nappi
      "Tallenna"
      ;; Palvelinkutsunappi olettaa saavansa kanavan. Siksi go.
      #(go
         (e! (pot2-tiedot/->TallennaPot2Tiedot)))
      {:luokka "nappi-ensisijainen"
       :data-cy "pot-tallenna"
       :id "tallenna-paallystysilmoitus"
       :disabled (or (false? valmis-tallennettavaksi?)
                     (not (oikeudet/voi-kirjoittaa?
                            oikeudet/urakat-kohdeluettelo-paallystysilmoitukset
                            urakka-id kayttaja)))
       :ikoni (ikonit/tallenna)
       :virheviesti "Tallentaminen epäonnistui"}]]))

(def pot2-validoinnit
  {:perustiedot paallystys/perustietojen-validointi
   :kulutuskerros {:rivi [{:fn validoi-kulutuskerros
                           :sarakkeet {:tr-numero :tr-numero
                                       :tr-ajorata :tr-ajorata
                                       :tr-kaista :tr-kaista
                                       :tr-alkuosa :tr-alkuosa
                                       :tr-alkuetaisyys :tr-alkuetaisyys
                                       :tr-loppuosa :tr-loppuosa
                                       :tr-loppuetaisyys :tr-loppuetaisyys}}]
                   :taulukko [{:fn (r/partial kohde-toisten-kanssa-paallekkain-validointi true)
                               :sarakkeet {:tr-numero :tr-numero
                                           :tr-ajorata :tr-ajorata
                                           :tr-kaista :tr-kaista
                                           :tr-alkuosa :tr-alkuosa
                                           :tr-alkuetaisyys :tr-alkuetaisyys
                                           :tr-loppuosa :tr-loppuosa
                                           :tr-loppuetaisyys :tr-loppuetaisyys}}]}})

(defn pot2-lomake
  [e! {yllapitokohde-id :yllapitokohde-id
       paallystysilmoitus-lomakedata :paallystysilmoitus-lomakedata
       massat :massat
       materiaalikoodistot :materiaalikoodistot
       :as              app}
   lukko urakka kayttaja]
  ;; Toistaiseksi ei käytetä lukkoa POT2-näkymässä
  (let [muokkaa! (fn [f & args]
                   (e! (pot2-tiedot/->PaivitaTila [:paallystysilmoitus-lomakedata] (fn [vanha-arvo]
                                                                                     (apply f vanha-arvo args)))))
        {:keys [tr-numero tr-alkuosa tr-loppuosa]} (get-in paallystysilmoitus-lomakedata [:perustiedot :tr-osoite])]
    (println "paallystysilmoitus-lomakedata kulutuskerros" (pr-str (:kulutuskerros paallystysilmoitus-lomakedata)))
    (komp/luo
      (komp/lippu pot2-tiedot/pot2-nakymassa?)
      (komp/sisaan (fn [this]
                     (e! (paallystys/->HaeTrOsienPituudet tr-numero tr-alkuosa tr-loppuosa))
                     (e! (paallystys/->HaeTrOsienTiedot tr-numero tr-alkuosa tr-loppuosa))
                     (reset! pot2-tiedot/kohdeosat-atom
                             (yllapitokohteet-domain/indeksoi-kohdeosat (yllapitokohteet-domain/jarjesta-yllapitokohteet
                                                                          (:kulutuskerros paallystysilmoitus-lomakedata))))
                     (nav/vaihda-kartan-koko! :S)))
      (fn [e! {:keys [paallystysilmoitus-lomakedata] :as app}]
        (let [perustiedot (:perustiedot paallystysilmoitus-lomakedata)
              perustiedot-app (select-keys paallystysilmoitus-lomakedata #{:perustiedot :kirjoitusoikeus? :ohjauskahvat})
              kulutuskerros-app (select-keys paallystysilmoitus-lomakedata #{:kirjoitusoikeus? :perustiedot :kulutuskerros})
              tallenna-app (select-keys (get-in app [:paallystysilmoitus-lomakedata :perustiedot])
                                        #{:tekninen-osa :tila})
              {:keys [tila]} perustiedot
              huomautukset (paallystys/perustietojen-huomautukset (:tekninen-osa perustiedot-app)
                                                                  (:valmispvm-kohde perustiedot-app))
              valmis-tallennettavaksi? (and
                                         (not= tila :lukittu)
                                         ;; todo: tähän mahd. tallennusta estävät validointivirheet
                                         )]
          [:div.pot2-lomake
           [napit/takaisin "Takaisin ilmoitusluetteloon" #(e! (pot2-tiedot/->MuutaTila [:paallystysilmoitus-lomakedata] nil))]
           [otsikkotiedot perustiedot]
           (when (= :lukittu tila)
             [pot-yhteinen/poista-lukitus e! urakka])
           [:hr]
           [pot-yhteinen/paallystysilmoitus-perustiedot
            e! perustiedot-app urakka false muokkaa! pot2-validoinnit huomautukset]
           [:hr]
           [kulutuskerros e! kulutuskerros-app {:massat massat
                                                :materiaalikoodistot materiaalikoodistot
                                                :validointi (:kulutuskerros pot2-validoinnit)} pot2-tiedot/kohdeosat-atom]
           [tallenna e! tallenna-app {:kayttaja kayttaja
                                      :urakka-id (:id urakka)
                                      :valmis-tallennettavaksi? valmis-tallennettavaksi?}]])))))
