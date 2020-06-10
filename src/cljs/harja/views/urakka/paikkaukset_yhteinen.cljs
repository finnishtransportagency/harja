(ns harja.views.urakka.paikkaukset-yhteinen
  (:require [reagent.core :as r :refer [atom]]
            [tuck.core :as tuck]
            [harja.tiedot.urakka.paikkaukset-yhteinen :as yhteiset-tiedot]
            [harja.tiedot.navigaatio :as nav]
            [harja.pvm :as pvm]
            [harja.ui.debug :as debug]
            [harja.ui.napit :as napit]
            [harja.ui.kentat :as kentat]
            [harja.ui.komponentti :as komp]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.yleiset :as yleiset]
            [harja.views.urakka.valinnat :as urakka-valinnat]))


(defn hakuehdot* [e! {:keys [valinnat aikavali-otsikko voi-valita-trn-kartalta? urakan-tyomenetelmat] :as yhteinen-tila}]
  (let [tr-atom (atom (:tr valinnat))
        aikavali-atom (atom (:aikavali valinnat))
        tyomenetelmat-atom (atom (:tyomenetelmat valinnat))]
    (add-watch tr-atom
               :tierekisteri-haku
               (fn [_ _ vanha uusi]
                 (e! (yhteiset-tiedot/->PaivitaValinnat {:tr uusi}))))
    (add-watch aikavali-atom
               :aikavali-haku
               (fn [_ _ vanha uusi]

                 (when-not (and (pvm/sama-pvm? (first vanha) (first uusi))
                                (pvm/sama-pvm? (second vanha) (second uusi)))
                   (e! (yhteiset-tiedot/->PaivitaValinnat {:aikavali uusi})))))
    (add-watch tyomenetelmat-atom
               :tyomenetelmat-haku
               (fn [_ _ vanha uusi]
                 (e! (yhteiset-tiedot/->PaivitaValinnat {:tyomenetelmat uusi}))))
    (fn [e! {:keys [valinnat aikavali-otsikko voi-valita-trn-kartalta?] :as yhteinen-tila}]
      [:span
       [kentat/tee-otsikollinen-kentta
        {:otsikko "Tierekisteriosoite"
         :kentta-params {:tyyppi :tierekisteriosoite
                         :tr-otsikot? false
                         :voi-valita-kartalta? voi-valita-trn-kartalta?}
         :arvo-atom tr-atom
         :tyylit {:width "fit-content"}}]
       [valinnat/aikavali aikavali-atom {:otsikko aikavali-otsikko}]
       [:span.label-ja-kentta
        [:span.kentan-otsikko "Näytettävät paikkauskohteet"]
        [:div.kentta
         [valinnat/checkbox-pudotusvalikko
          (:urakan-paikkauskohteet valinnat)
          (fn [paikkauskohde valittu?]
            (e! (yhteiset-tiedot/->PaikkausValittu paikkauskohde valittu?)))
          [" paikkauskohde valittu" " paikkauskohdetta valittu"]
          {:kaikki-valinta-fn (fn []
                                (let [osa-valittu (some true? (map :valittu? (:urakan-paikkauskohteet valinnat)))]
                                  (e! (yhteiset-tiedot/->PaivitaValinnat {:urakan-paikkauskohteet
                                                                          (map #(assoc % :valittu? (not osa-valittu)) (:urakan-paikkauskohteet valinnat))
                                                                          }))))}]]]
       [:span.label-ja-kentta
        [:span.kentan-otsikko "Työmenetelmät"]
        [:div.kentta
         [valinnat/checkbox-pudotusvalikko
          (vec (map-indexed (fn [i tyomenetelma]
                              {:id i :nimi tyomenetelma :valittu? (contains? (:tyomenetelmat valinnat) tyomenetelma)})
                            (sort urakan-tyomenetelmat)))
          (fn [tyomenetelma valittu?]
            (e! (yhteiset-tiedot/->TyomenetelmaValittu tyomenetelma valittu?)))
          [" työmenetelmä valittu" " työmenetelmää valittu"]
          {:kaikki-valinta-fn (fn []
                                (e! (yhteiset-tiedot/->PaivitaValinnat {:tyomenetelmat (if (empty? (:tyomenetelmat valinnat))
                                                                                           urakan-tyomenetelmat
                                                                                           (set nil))})))}]]]])))
(defn hakuehdot-pohja [e! app]
  (komp/luo
    (komp/sisaan #(e! (yhteiset-tiedot/->Nakymaan)))
    (fn [e! app]
      (if (:ensimmainen-haku-tehty? app)
        [:div
         [debug/debug app {:otsikko "Hakuehtojen tila"}]
         [hakuehdot* e! app]]
        [yleiset/ajax-loader "Haetaan paikkauksia.."]))))

(defn hakuehdot [optiot]
  (komp/luo
    (komp/sisaan #(yhteiset-tiedot/alku-parametrit optiot))
    (fn [_]
      [tuck/tuck yhteiset-tiedot/tila hakuehdot-pohja])))
