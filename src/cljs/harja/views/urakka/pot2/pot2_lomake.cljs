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
    [harja.ui.ikonit :as ikonit])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))


(defn- alusta [e! app]
  [:div "Alustatiedot"])

(defn- kulutuskerros [e! app]
  [:div "Kulutuskerroksen tiedot"])

(defn- muokkaa-fn [tila]
  tila)

(defn- otsikkotiedot [{:keys [tila] :as perustiedot}]
  [:span
   [:h1 (str "Päällystysilmoitus - "
                   (pot-yhteinen/paallystyskohteen-fmt perustiedot))]
   [:div
    [:div.inline-block.pot-tila {:class (name tila)}
     tila]]])

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

(defn pot2-lomake
  [e! {yllapitokohde-id :yllapitokohde-id
       perustiedot      :perustiedot
       :as              lomakedata-nyt}
   lukko urakka kayttaja]
  (komp/luo
    (komp/lippu pot2-tiedot/pot2-nakymassa?)
    (komp/piirretty (fn [this]
                      (println "component did mount")))
    (fn [e! app]
      (let [perustiedot-app (select-keys lomakedata-nyt #{:perustiedot :kirjoitusoikeus? :ohjauskahvat})]
        [:div.pot2-lomake
         [napit/takaisin "Takaisin ilmoitusluetteloon" #(e! (pot2-tiedot/->MuutaTila [:paallystysilmoitus-lomakedata] nil))]
         [otsikkotiedot perustiedot]
         [:hr]
         [pot-yhteinen/paallystysilmoitus-perustiedot
          e! perustiedot-app urakka false (fn [] (println "do nothing")) [] []]]))))
