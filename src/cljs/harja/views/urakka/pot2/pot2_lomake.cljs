(ns harja.views.urakka.pot2.pot2-lomake
"POT2-lomake"
  (:require
    [harja.tiedot.urakka.pot2.pot2-tiedot :as pot2-tiedot]
    [harja.loki :refer [log]]
    [harja.ui.komponentti :as komp]
    [harja.ui.lomake :as lomake]
    [harja.ui.napit :as napit]
    [harja.views.urakka.pot-yhteinen :as pot-yhteinen]
    [harja.domain.oikeudet :as oikeudet]
    [harja.ui.ikonit :as ikonit]
    [harja.tiedot.navigaatio :as nav]
    [harja.tiedot.urakka.paallystys :as paallystys])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))


(defn- alusta [e! app]
  [:div "Alustatiedot"])

(defn- kulutuskerros [e! app]
  [:div "Kulutuskerroksen tiedot"])


(defn- otsikkotiedot [{:keys [tila] :as perustiedot}]
  [:span
   [:h1 (str "Päällystysilmoitus - "
                   (pot-yhteinen/paallystyskohteen-fmt perustiedot))]
   [:div
    [:div.inline-block.pot-tila {:class (when tila (name tila))}
     (if-not tila "Aloittamatta" tila)]]])

(defn tallenna
  [e! {:keys [tekninen-osa tila]} kayttaja {urakka-id :id :as urakka} valmis-tallennettavaksi?]
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
                     (nav/vaihda-kartan-koko! :S)))
      (fn [e! {:keys [perustiedot] :as app}]
        (let [perustiedot-app (select-keys app #{:perustiedot :kirjoitusoikeus? :ohjauskahvat})
              huomautukset (paallystys/perustietojen-huomautukset (:tekninen-osa perustiedot-app)
                                                                  (:valmispvm-kohde perustiedot-app))]
          [:div.pot2-lomake
           [napit/takaisin "Takaisin ilmoitusluetteloon" #(e! (pot2-tiedot/->MuutaTila [:paallystysilmoitus-lomakedata] nil))]
           [otsikkotiedot perustiedot]
           [:hr]
           [pot-yhteinen/paallystysilmoitus-perustiedot
            e! perustiedot-app urakka false muokkaa! pot2-validoinnit huomautukset]])))))
