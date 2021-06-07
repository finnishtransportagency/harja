(ns harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-yhteinen
  (:require [reagent.core :as r :refer [atom]]
            [tuck.core :as tuck]
            [harja.domain.paikkaus :as paikkaus]
            [harja.tiedot.urakka.paikkaukset-yhteinen :as yhteiset-tiedot]
            [harja.pvm :as pvm]
            [harja.ui.debug :as debug]
            [harja.ui.kentat :as kentat]
            [harja.ui.komponentti :as komp]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.yleiset :as yleiset]
            [harja.tiedot.urakka.urakka :as tila]))


(defn hakuehdot* [e! {:keys [valinnat aikavali-otsikko voi-valita-trn-kartalta? urakan-tyomenetelmat] :as app}]
  (let [tr-atom (atom (:tr valinnat))
        aikavali-atom (atom (:aikavali valinnat))]
    (add-watch tr-atom
               :tierekisteri-haku
               (fn [_ _ _ uusi]
                 (e! (yhteiset-tiedot/->PaivitaValinnat {:tr uusi}))))
    (add-watch aikavali-atom
               :aikavali-haku
               (fn [_ _ vanha uusi]

                 (when-not (and (pvm/sama-pvm? (first vanha) (first uusi))
                                (pvm/sama-pvm? (second vanha) (second uusi)))
                   (e! (yhteiset-tiedot/->PaivitaValinnat {:aikavali uusi})))))
    (fn [e! {:keys [valinnat aikavali-otsikko ] :as yhteinen-tila}]
      [:div.flex-row.alkuun {:style {:align-items "flex-start"}}
       [:div {:style {:margin "5px"}}
        [kentat/tee-otsikollinen-kentta
              {:otsikko "Tierekisteriosoite"
               :luokka ""
               :otsikon-luokka "alasvedon-otsikko-vayla"
               :kentta-params {:tyyppi :tierekisteriosoite
                               :tr-otsikot? false
                               :voi-valita-kartalta? false
                               :alaotsikot? true
                               :vayla-tyyli? true
                               }
               :arvo-atom tr-atom
               :tyylit {:width "fit-content"}}]]
       [valinnat/aikavali aikavali-atom {:otsikko aikavali-otsikko
                                         :vayla-tyyli? true}]

       [:span.label-ja-kentta {:style {:width "500px"}}
        [:label.alasvedon-otsikko-vayla "Työmenetelmä"]
        [:div.kentta
         [valinnat/checkbox-pudotusvalikko
          (map (fn [t]
                 {:nimi (or (::paikkaus/tyomenetelma-nimi t) t)
                  :id (::paikkaus/tyomenetelma-id t)
                  :valittu? (or (some #(or (= t %)
                                           (= (::paikkaus/tyomenetelma-id t) %)) (:valitut-tyomenetelmat valinnat)) ;; Onko kyseinen työmenetelmä valittu
                                false)})
               (into ["Kaikki"] (:tyomenetelmat valinnat)))
          (fn [tyomenetelma valittu?]
            (e! (yhteiset-tiedot/->ValitseTyomenetelma tyomenetelma valittu?)))
          [" Työmenetelmä valittu" " Työmenetelmää valittu"]
          {:vayla-tyyli? true}]]]])))
(defn hakuehdot-pohja [e! app]
  (if (:ensimmainen-haku-tehty? app)
    [:div
     [hakuehdot* e! app]]
    [yleiset/ajax-loader "Haetaan paikkauksia.."]))

(defn hakuehdot [optiot]
  (fn [_]
    [tuck/tuck tila/paikkaustoteumat hakuehdot-pohja]))
