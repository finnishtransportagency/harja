(ns harja.views.urakka.pot2.pot2-lomake

  (:require
    [harja.tiedot.urakka.pot2.pot2-tiedot :as pot2-tiedot]
    [harja.loki :refer [log]]
    [harja.ui.komponentti :as komp]
    [harja.ui.napit :as napit]
    [harja.views.urakka.pot-yhteinen :as pot-yhteinen]))


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
