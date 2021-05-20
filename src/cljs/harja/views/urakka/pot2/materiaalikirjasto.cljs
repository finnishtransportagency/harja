(ns harja.views.urakka.pot2.materiaalikirjasto
  "POT2 materiaalikirjasto"
  (:require [clojure.string :as str]
            [cljs.core.async :refer [<! chan]]
            [reagent.core :refer [atom] :as r]
            [harja.ui.debug :refer [debug]]
            [harja.ui.komponentti :as komp]
            [harja.ui.modal :as modal]
            [harja.ui.napit :as napit]
            [harja.ui.validointi :as v]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko virheen-ohje] :as yleiset]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.pot2.materiaalikirjasto :as mk-tiedot]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.views.urakka.pot2.massa-lomake :as massa-lomake]
            [harja.views.urakka.pot2.massat-taulukko :as massat-taulukko]
            [harja.views.urakka.pot2.murske-lomake :as murske-lomake]
            [harja.views.urakka.pot2.murskeet-taulukko :as murskeet-taulukko]
            [harja.loki :refer [log logt tarkkaile!]])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))


(defn- urakan-materiaalit [e! app]
  [:span
   [massat-taulukko/massat-taulukko e! app]
   [murskeet-taulukko/murskeet-taulukko e! app]
   ;; spacer, jotta alimpien rivien pop up ei piiloudu. Voi olla että voidaan poistaa kunhan murskeiden hallionta on tehty
   [:div {:style {:height "100px"}}]
   [napit/sulje "Sulje kirjasto"
    #(swap! mk-tiedot/nayta-materiaalikirjasto? not)]])

(defn materiaalikirjasto [e! app]
  (komp/luo
    (komp/lippu mk-tiedot/materiaalikirjastossa?)
    (komp/piirretty (fn [this]
                      (e! (mk-tiedot/->AlustaTila))
                      (e! (mk-tiedot/->HaePot2MassatJaMurskeet))))
    (fn [e! app]
      [:div
       (cond
         (and (:pot2-massa-lomake app) (not (get-in app [:pot2-massa-lomake :sivulle?])))
         [massa-lomake/massa-lomake e! app]

         (and (:pot2-murske-lomake app) (not (get-in app [:pot2-murske-lomake :sivulle?])))
         [murske-lomake/murske-lomake e! app]

         :else
         [urakan-materiaalit e! app])
       [debug app {:otsikko "TUCK STATE"}]])))

(def materiaalikirjaston-otsikon-text
  "Voit käyttää materiaalikirjastoon lisättyjä massoja ja murskeita kaikissa urakan päällystysilmoituksissa.")

(defn materiaalikirjasto-modal [e! app]
  [modal/modal
   {:otsikko "Materiaalikirjasto"
    :otsikon-alle-komp (fn []
                         [:span
                          [:div.fontti-14-vaaleampi {:style {:margin-bottom "24px"}}
                           (str (:nimi @nav/valittu-urakka))]
                          [:div.fontti-14 materiaalikirjaston-otsikon-text]])
    :luokka "materiaalikirjasto-modal"
    :nakyvissa? @mk-tiedot/nayta-materiaalikirjasto?
    :sulje-ruksista-fn #(cond
                          (:pot2-massa-lomake app)
                          (e! (mk-tiedot/->TyhjennaLomake))

                          (:pot2-murske-lomake app)
                          (e! (mk-tiedot/->SuljeMurskeLomake))

                          :else
                          (swap! mk-tiedot/nayta-materiaalikirjasto? not))
    :sulje-fn #(when
                 (and (not (:pot2-massa-lomake app))
                      (not (:pot2-murske-lomake app)))

                 (swap! mk-tiedot/nayta-materiaalikirjasto? not))}
   [:div
    [materiaalikirjasto e! app]]])


