(ns harja.views.urakka.paikkaukset-yhteinen
  (:require [reagent.core :as r :refer [atom]]
            [harja.tiedot.urakka.paikkaukset-yhteinen :as yhteinen-tiedot]
            [harja.tiedot.navigaatio :as nav]
            [harja.pvm :as pvm]
            [harja.ui.napit :as napit]
            [harja.ui.kentat :as kentat]
            [harja.ui.valinnat :as valinnat]
            [harja.views.urakka.valinnat :as urakka-valinnat]))

(defn aika-formatteri [aika]
  (when-not (nil? aika)
    [:div
     [:span {:style {:width "100%"
                     :display "inline-block"}}
      (pvm/pvm aika)]
     [:span (str "klo. " (pvm/aika aika))]]))

(defn otsikkokomponentti
  [napin-teksti siirry-toimenpiteisiin-fn id]
  [{:tyyli {:float "right"
            :position "relative"}
    :sisalto
    (fn [_]
      [napit/yleinen-ensisijainen
       napin-teksti
       #(siirry-toimenpiteisiin-fn id)])}])

(defn hakuehdot [{:keys [valinnat] :as app} {:keys [paivita-valinnat-fn paikkaus-valittu-fn aikavali-otsikko]}]
  (let [tr-atom (atom (:tr valinnat))
        aikavali-atom (atom (:aikavali valinnat))
        tyomenetelmat-atom (atom (:tyomenetelmat valinnat))]
    (add-watch tr-atom
               :tierekisteri-haku
               (fn [_ _ vanha uusi]
                 (paivita-valinnat-fn {:tr uusi})))
    (add-watch aikavali-atom
               :aikavali-haku
               (fn [_ _ vanha uusi]
                 (when-not (and (pvm/sama-pvm? (first vanha) (first uusi))
                                (pvm/sama-pvm? (second vanha) (second uusi)))
                   (paivita-valinnat-fn {:aikavali uusi}))))
    (add-watch tyomenetelmat-atom
               :tyomenetelmat-haku
               (fn [_ _ vanha uusi]
                 (paivita-valinnat-fn {:tyomenetelmat uusi})))
    (fn [{:keys [valinnat] :as app} paivita-valinnat-fn paikkaus-valittu-fn]
      [:span
       [kentat/tee-otsikollinen-kentta
        {:otsikko "Tierekisteriosoite"
         :kentta-params {:tyyppi :tierekisteriosoite
                         :tr-otsikot? false}
         :arvo-atom tr-atom
         :tyylit {:width "fit-content"}}]
       [urakka-valinnat/aikavali-nykypvm-taakse @nav/valittu-urakka aikavali-atom {:otsikko aikavali-otsikko}]
       [:span.label-ja-kentta
        [:span.kentan-otsikko "Näytettävät paikkauskohteet"]
        [:div.kentta
         [valinnat/checkbox-pudotusvalikko
          (:urakan-paikkauskohteet valinnat)
          paikkaus-valittu-fn
          [" paikkauskohde valittu" " paikkauskohdetta valittu"]]]]
       [kentat/tee-otsikollinen-kentta
        {:otsikko "Työmenetelmät"
         :tyylit {:width "fit-content"}
         :kentta-params {:tyyppi :checkbox-group
                         :vaihtoehdot #{"massapintaus" "kuumennuspintaus" "remix-pintaus"}
                         :nayta-rivina? true}
         :arvo-atom tyomenetelmat-atom}]])))