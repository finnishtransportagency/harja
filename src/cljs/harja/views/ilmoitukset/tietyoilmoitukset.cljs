(ns harja.views.ilmoitukset.tietyoilmoitukset
  (:require [reagent.core :refer [atom] :as r]
            [harja.tiedot.ilmoitukset.tietyoilmoitukset :as tiedot]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :refer [grid]]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.ui.valinnat :refer [urakan-hoitokausi-ja-aikavali]]
            [harja.ui.debug :as ui-debug]
            [harja.loki :refer [tarkkaile! log]]
            [cljs.pprint :refer [pprint]]
            [harja.tiedot.navigaatio :as nav]
            [harja.views.kartta :as kartta]
            [harja.ui.notifikaatiot :as notifikaatiot]
            [tuck.core :refer [tuck send-value! send-async!]]
            [harja.tiedot.hallintayksikot :as hallintayksikot-tiedot]
            [harja.tiedot.kartta :as kartta-tiedot]
            [harja.views.ilmoitukset.tietyoilmoitushakulomake :as tietyoilmoitushakulomake]
            [harja.views.ilmoitukset.tietyoilmoituslomake :as tietyoilmoituslomake]
            [harja.ui.napit :as napit]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.ikonit :as ikonit]
            [harja.transit :as transit])
  (:require-macros
   [cljs.core.async.macros :refer [go]]))

(defn vie-pdf [id]
  [:form {:target "_blank"
          :method "POST"
          :action (k/pdf-url :tietyoilmoitus)}
   [:input {:type "hidden" :name "parametrit"
            :value (transit/clj->transit {:id id})}]
   [:button.nappi-ensisijainen.pull-right
    {:type "submit"}
    (ikonit/print) " Tallenna PDF"]])

(defn ilmoitukset* [e! ilmoitukset]
  (e! (tiedot/->HaeKayttajanUrakat @hallintayksikot-tiedot/hallintayksikot))
  (e! (tiedot/->YhdistaValinnat @tiedot/ulkoisetvalinnat))
  (komp/luo
    (komp/lippu tiedot/karttataso-tietyoilmoitukset)
    (komp/kuuntelija :ilmoitus-klikattu (fn [_ i] (e! (tiedot/->ValitseIlmoitus i))))
    (komp/sisaan-ulos #(do
                         (notifikaatiot/pyyda-notifikaatiolupa)
                         (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
                         (nav/vaihda-kartan-koko! :M)
                         (kartta-tiedot/kasittele-infopaneelin-linkit!
                           {:tietyoilmoitus {:toiminto (fn [tietyoilmoitus-infopaneelista]
                                                   (e! (tiedot/->ValitseIlmoitus tietyoilmoitus-infopaneelista)))
                                       :teksti "Valitse ilmoitus"}}))
                      #(do
                         (kartta-tiedot/kasittele-infopaneelin-linkit! nil)
                         (nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko)))
    (fn [e! {valittu-ilmoitus :valittu-ilmoitus kayttajan-urakat :kayttajan-urakat :as app}]
      [:span
       #_[ui-debug/debug @tiedot/tietyoilmoitukset]
       [kartta/kartan-paikka]
       [vie-pdf 1]
       (if valittu-ilmoitus
         [tietyoilmoituslomake/lomake e! valittu-ilmoitus kayttajan-urakat]
         [tietyoilmoitushakulomake/hakulomake e! app])])))
