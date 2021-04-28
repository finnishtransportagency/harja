(ns harja.views.urakka.pot2.materiaalikirjasto
  "POT2 materiaalikirjasto"
  (:require [clojure.string :as str]
            [cljs.core.async :refer [<! chan]]
            [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]

            [harja.ui.grid :as grid]
            [harja.ui.debug :refer [debug]]
            [harja.ui.dom :as dom]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :as kentat]
            [harja.ui.komponentti :as komp]
            [harja.ui.lomake :as ui-lomake]
            [harja.ui.modal :as modal]
            [harja.ui.napit :as napit]
            [harja.ui.validointi :as v]
            [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko virheen-ohje] :as yleiset]
            [harja.domain.paallystysilmoitus :as paallystysilmoitus-domain]
            [harja.domain.pot2 :as pot2-domain]
            [harja.tiedot.urakka.pot2.validoinnit :as pot2-validoinnit]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.pot2.materiaalikirjasto :as mk-tiedot]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.views.urakka.pot2.massat :as massat]
            [harja.views.urakka.pot2.murskeet :as murskeet]
            [harja.loki :refer [log logt tarkkaile!]])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))


(defn- urakan-materiaalit [e! app]
  [:span
   [massat/massat-taulukko e! app]
   [murskeet/murskeet-taulukko e! app]
   ;; spacer, jotta alimpien rivien pop up ei piiloudu. Voi olla että voidaan poistaa kunhan murskeiden hallionta on tehty
   [:div {:style {:height "100px"}}]
   [napit/sulje #(swap! mk-tiedot/nayta-materiaalikirjasto? not)]])

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
         [massat/massa-lomake e! app]

         (and (:pot2-murske-lomake app) (not (get-in app [:pot2-murske-lomake :sivulle?])))
         [murskeet/murske-lomake e! app]

         :else
         [urakan-materiaalit e! app])
       [debug app {:otsikko "TUCK STATE"}]])))

(defn materiaalikirjasto-modal [e! app]
  [modal/modal
   {:otsikko (str "Urakan materiaalikirjasto - " (:nimi @nav/valittu-urakka))
    :luokka "materiaalikirjasto-modal"
    :nakyvissa? @mk-tiedot/nayta-materiaalikirjasto?
    :sulje-fn #(cond
                 (:pot2-massa-lomake app)
                 (e! (mk-tiedot/->TyhjennaLomake))

                 (:pot2-murske-lomake app)
                 (e! (mk-tiedot/->SuljeMurskeLomake))

                 :else
                 (swap! mk-tiedot/nayta-materiaalikirjasto? not))}
   [:div
    [materiaalikirjasto e! app]]])


