(ns harja.views.urakka.pot2.pot2-lomake
"POT2-lomake"
  (:require
    [reagent.core :refer [atom]]
    [harja.tiedot.urakka.pot2.pot2-tiedot :as pot2-tiedot]
    [harja.loki :refer [log]]
    [harja.ui.grid :as grid]
    [harja.ui.komponentti :as komp]
    [harja.ui.lomake :as lomake]
    [harja.ui.napit :as napit]
    [harja.views.urakka.pot-yhteinen :as pot-yhteinen]
    [harja.domain.oikeudet :as oikeudet]
    [harja.ui.ikonit :as ikonit]
    [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
    [harja.tiedot.navigaatio :as nav]
    [harja.tiedot.urakka.paallystys :as paallystys]
    [harja.tiedot.urakka.yllapitokohteet :as yllapitokohteet]
    [harja.domain.yllapitokohde :as yllapitokohteet-domain])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))


(defn- alusta [e! app]
  [:div "Alustatiedot"])

(def kohdeosat-atom (atom nil))

(defn- kulutuskerros [e! {:keys [kohdeosat perustiedot kirjoitusoikeus?] :as app}]
  (log "kulutuskerros kohdeosat-atom" (pr-str @kohdeosat-atom))
  (let [voi-muokata? true]
    [grid/muokkaus-grid
     {:otsikko "Kulutuskerros"
      :tunniste :kohdeosa-id
      :uusi-rivi (fn [rivi]
                   ;; Otetaan pääkohteen tie, ajorata ja kaista, jos on
                   (assoc rivi
                     :tr-numero (:tr-numero perustiedot)))
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
     [{:otsikko "Tie" :tyyppi :string :hae :tr-numero}
      {:otsikko "Ajor." :tyyppi :string :hae :tr-ajorata}
      {:otsikko "Kaista" :tyyppi :string :hae :tr-kaista}
      {:otsikko "Aosa" :tyyppi :string :hae :tr-alkuosa}
      {:otsikko "Aet" :tyyppi :string :hae :tr-alkuetaisyys}
      {:otsikko "Losa" :tyyppi :string :hae :tr-loppuosa}
      {:otsikko "Let" :tyyppi :string :hae :tr-loppuetaisyys}
      {:otsikko "Pituus (m)" :tyyppi :string :hae :tr-pituus}]
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
       perustiedot      :perustiedot
       :as              app}
   lukko urakka kayttaja]
  ;; Toistaiseksi ei käytetä lukkoa POT2-näkymässä
  (let [muokkaa! (fn [f & args]
                   (e! (pot2-tiedot/->PaivitaTila [:paallystysilmoitus-lomakedata] (fn [vanha-arvo]
                                                                                     (apply f vanha-arvo args)))))]
    (komp/luo
      (komp/lippu pot2-tiedot/pot2-nakymassa?)
      (komp/sisaan (fn [this]
                     (reset! kohdeosat-atom (yllapitokohteet-domain/indeksoi-kohdeosat (yllapitokohteet-domain/jarjesta-yllapitokohteet (:kohdeosat app))))
                     (nav/vaihda-kartan-koko! :S)))

      (fn [e! {:keys [perustiedot] :as app}]
        (let [perustiedot-app (select-keys app #{:perustiedot :kirjoitusoikeus? :ohjauskahvat})
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
           [kulutuskerros e! (select-keys app #{:kohdeosat :kirjoitusoikeus?})]
           [tallenna e! app {:kayttaja kayttaja
                             :urakka-id (:id urakka)
                             :valmis-tallennettavaksi? valmis-tallennettavaksi?}]])))))
