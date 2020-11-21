(ns harja.views.urakka.pot2.pot2-lomake

  (:require
    [harja.tiedot.urakka.pot2.pot2-tiedot :as pot2-tiedot]
    [harja.ui.komponentti :as komp]
    [harja.ui.napit :as napit]
    [harja.views.urakka.pot-yhteinen :as pot-yhteinen]
    [harja.ui.dom :as dom]))


(defn- alusta [e! app]
  [:div "Alustatiedot"])

(defn- kulutuskerros [e! app]
  [:div "Kulutuskerroksen tiedot"])

(defn- muokkaa-fn [tila]
  tila)

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
         [pot-yhteinen/paallystysilmoitus-perustiedot
          e! perustiedot-app urakka false (fn [] (println "do nothing")) [] []]]))))
