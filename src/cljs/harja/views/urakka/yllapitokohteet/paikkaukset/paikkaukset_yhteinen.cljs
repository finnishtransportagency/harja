(ns harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-yhteinen
  (:require [reagent.core :as r :refer [atom]]
            [tuck.core :as tuck]
            [harja.tiedot.urakka.paikkaukset-yhteinen :as yhteiset-tiedot]
            [harja.pvm :as pvm]
            [harja.ui.debug :as debug]
            [harja.ui.kentat :as kentat]
            [harja.ui.komponentti :as komp]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.yleiset :as yleiset]))


(defn hakuehdot* [e! {:keys [valinnat urakan-tyomenetelmat]}]
  (let [tr-atom (atom (:tr valinnat))
        aikavali-atom (atom (:aikavali valinnat))
        tyomenetelmat-atom (atom (:tyomenetelmat valinnat))]
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
    (add-watch tyomenetelmat-atom
               :tyomenetelmat-haku
               (fn [_ _ _ uusi]
                 (e! (yhteiset-tiedot/->PaivitaValinnat {:tyomenetelmat uusi}))))
    (fn [e! {:keys [valinnat aikavali-otsikko voi-valita-trn-kartalta?]}]
      [:div.filtterit.flex-row.alkuun {:style {:padding "16px" :align-items "flex-start"}}
       [kentat/tee-otsikollinen-kentta
        {:otsikko "Tierekisteriosoite"
         :luokka ""
         :otsikon-luokka "alasvedon-otsikko-vayla"
         :kentta-params {:tyyppi :tierekisteriosoite
                         :vayla-tyyli? true
                         :tr-otsikot? false
                         :alaotsikot? true
                         :piilota-nappi? true
                         :voi-valita-kartalta? voi-valita-trn-kartalta?}
         :arvo-atom tr-atom
         :tyylit {:width "fit-content"}}]
       [valinnat/aikavali aikavali-atom {:otsikko aikavali-otsikko 
                                         :lomake? false
                                         :luokka #{"label-ja-aikavali " "ei-marginia "}
                                         :vayla-tyyli? true}]
       [:div
        [:label.alasvedon-otsikko-vayla "Työmenetelmät"]
        [valinnat/checkbox-pudotusvalikko
         (vec (map-indexed (fn [i tyomenetelma]
                             {:id i 
                              :nimi tyomenetelma 
                              :valittu? (contains? (:tyomenetelmat valinnat) tyomenetelma)})
                           (sort urakan-tyomenetelmat)))
         (fn [tyomenetelma valittu?]
           (e! (yhteiset-tiedot/->TyomenetelmaValittu tyomenetelma valittu?)))
         [" työmenetelmä valittu" " työmenetelmää valittu"]
         {:vayla-tyyli? true
          #_:kaikki-valinta-fn #_(fn []
                                   (e! 
                                    (yhteiset-tiedot/->PaivitaValinnat 
                                     {:tyomenetelmat (if (empty? (:tyomenetelmat valinnat))
                                                       urakan-tyomenetelmat
                                                       (set nil))})))}]]])))
(defn hakuehdot-pohja [e! _app]
  (komp/luo
    (komp/sisaan #(e! (yhteiset-tiedot/->Nakymaan)))
    (fn [e! app]
      (if (:ensimmainen-haku-tehty? app)
        [:div
         [debug/debug app {:otsikko "Hakuehtojen tila"}]
         [hakuehdot* e! app]]
        [yleiset/ajax-loader "Haetaan paikkauksia.."]))))

(defn hakuehdot [{:keys [tila-atomi] :as optiot}]
  (komp/luo
    (komp/sisaan #(yhteiset-tiedot/alku-parametrit optiot (or tila-atomi yhteiset-tiedot/tila)))
    (fn [_]
      [tuck/tuck (or tila-atomi yhteiset-tiedot/tila) hakuehdot-pohja])))
