(ns harja.views.urakka.paikkaukset-yhteinen
  (:require [harja.tiedot.urakka.paikkaukset-yhteinen :as yhteinen-tiedot]
            [harja.ui.napit :as napit]
            [harja.ui.kentat :as kentat]
            [harja.ui.valinnat :as valinnat]))

(defn otsikkokomponentti
  [napin-teksti siirry-toimenpiteisiin-fn id]
  [{:tyyli {:float "right"
            :position "relative"}
    :sisalto
    (fn [_]
      [napit/yleinen-ensisijainen
       napin-teksti
       #(siirry-toimenpiteisiin-fn id)])}])

(defn hakuehdot [{:keys [valinnat] :as app} paivita-valinnat-fn paikkaus-valittu-fn]
  (let [tr-atomi (partial yhteinen-tiedot/valinta-wrap app paivita-valinnat-fn)
        aikavali-atomi (partial yhteinen-tiedot/valinta-wrap app paivita-valinnat-fn)]
    [:span
     [kentat/tee-otsikollinen-kentta
      {:otsikko "Tierekisteriosoite"
       :kentta-params {:tyyppi :tierekisteriosoite
                       :tr-otsikot? false}
       :arvo-atom (tr-atomi :tr)
       :tyylit {:width "fit-content"}}]
     [valinnat/aikavali (aikavali-atomi :aikavali)]
     [:span.label-ja-kentta
      [:span.kentan-otsikko "Näytettävät paikkauskohteet"]
      [:div.kentta
       [valinnat/checkbox-pudotusvalikko
        (:urakan-paikkauskohteet valinnat)
        paikkaus-valittu-fn
        [" paikkauskohde valittu" " paikkauskohdetta valittu"]]]]]))