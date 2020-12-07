(ns harja.views.urakka.pot2.pot2-lomake
"POT2-lomake"
  (:require
    [reagent.core :refer [atom]]
    [harja.domain.oikeudet :as oikeudet]
    [harja.domain.pot2 :as pot2-domain]
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
    [harja.views.urakka.pot-yhteinen :as pot-yhteinen]
    [harja.domain.tierekisteri :as tr])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))


(defn- alusta [e! app]
  [:div "Alustatiedot"])

(defn- kulutuskerros
  "Alikohteiden päällysteiden kulutuskerroksen rivien muokkaus"
  [e! {:keys [kirjoitusoikeus? perustiedot] :as app}
   {:keys [massat massatyypit materiaalikoodistot]} kohdeosat-atom]
  (let [voi-muokata? true
        perusleveys 2
        kulutuskerros-toimenpiteet (:kulutuskerros-toimenpiteet materiaalikoodistot)]
    [grid/muokkaus-grid
     {:otsikko "Kulutuskerros"
      :tunniste :kohdeosa-id
      :uusi-rivi (fn [rivi]
                   (assoc rivi
                     :tr-numero (:tr-numero perustiedot)))
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
     [{:otsikko "Toimen\u00ADpide" :nimi :toimenpide :leveys 1
       :tyyppi :valinta :valinnat kulutuskerros-toimenpiteet :valinta-arvo ::pot2-domain/koodi
       :valinta-nayta ::pot2-domain/lyhenne}
      {:otsikko "Tie" :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true :leveys perusleveys :nimi :tr-numero}
      {:otsikko "Ajor." :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true :leveys perusleveys :nimi :tr-ajorata}
      {:otsikko "Kaista" :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true :leveys perusleveys :nimi :tr-kaista}
      {:otsikko "Aosa" :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true :leveys perusleveys :nimi :tr-alkuosa}
      {:otsikko "Aet" :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true :leveys perusleveys :nimi :tr-alkuetaisyys}
      {:otsikko "Losa" :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true :leveys perusleveys :nimi :tr-loppuosa}
      {:otsikko "Let" :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true :leveys perusleveys :nimi :tr-loppuetaisyys}
      {:otsikko "Pit. (m)" :nimi :pituus :leveys perusleveys :tyyppi :numero :tasaa :oikea
       :muokattava? (constantly false) :hae #(paallystys/rivin-kohteen-pituus (paallystys/tien-osat-riville % paallystys/tr-osien-tiedot) %)}
      {:otsikko "Pääl\u00ADlyste" :nimi :materiaali :leveys 3
       :tyyppi :valinta :valinnat massat :valinta-arvo :pot2-massa/id
       :valinta-nayta (fn [rivi]
                        (pot2-domain/massatyypin-rikastettu-nimi massatyypit rivi))}
      {:otsikko "Piennar" :nimi :piennar :leveys 1 :tyyppi :checkbox :hae (fn [rivi]
                                                                            (boolean (:piennar rivi)))}]
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
  {:perustiedot paallystys/perustietojen-validointi})


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
    (komp/luo
      (komp/lippu pot2-tiedot/pot2-nakymassa?)
      (komp/sisaan (fn [this]
                     (e! (paallystys/->HaeTrOsienPituudet tr-numero tr-alkuosa tr-loppuosa))
                     (e! (paallystys/->HaeTrOsienTiedot tr-numero tr-alkuosa tr-loppuosa))
                     (reset! pot2-tiedot/kohdeosat-atom (yllapitokohteet-domain/indeksoi-kohdeosat (yllapitokohteet-domain/jarjesta-yllapitokohteet (:kohdeosat paallystysilmoitus-lomakedata))))
                     (nav/vaihda-kartan-koko! :S)))
      (fn [e! {:keys [paallystysilmoitus-lomakedata] :as app}]
        (let [perustiedot (:perustiedot paallystysilmoitus-lomakedata)
              perustiedot-app (select-keys paallystysilmoitus-lomakedata #{:perustiedot :kirjoitusoikeus? :ohjauskahvat})
              kulutuskerros-app (select-keys paallystysilmoitus-lomakedata #{:kirjoitusoikeus? :perustiedot})
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
                                                :materiaalikoodistot materiaalikoodistot} pot2-tiedot/kohdeosat-atom]
           [debug app {:otsikko "TUCK STATE"}]
           [tallenna e! tallenna-app {:kayttaja kayttaja
                                      :urakka-id (:id urakka)
                                      :valmis-tallennettavaksi? valmis-tallennettavaksi?}]])))))
