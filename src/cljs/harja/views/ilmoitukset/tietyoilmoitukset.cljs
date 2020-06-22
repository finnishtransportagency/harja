(ns harja.views.ilmoitukset.tietyoilmoitukset
  (:require [reagent.core :refer [atom] :as r]
            [harja.tiedot.ilmoitukset.tietyoilmoitukset :as tiedot]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :refer [grid]]
            [harja.ui.kentat :refer [tee-kentta]]
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
            [harja.tyokalut.spec-apurit :as spec-apurit])
  (:require-macros
    [cljs.core.async.macros :refer [go]]))

#_(defn- debug-state [app]
  [:span
   [:div.row
    [df/DataFriskShell app]]])

(defn ilmoitukset* [e! ilmoitukset]
  (e! (tiedot/->HaeKayttajanUrakat @hallintayksikot-tiedot/vaylamuodon-hallintayksikot))
  (e! (tiedot/->YhdistaValinnat @tiedot/ulkoisetvalinnat))
  (komp/luo
    (komp/lippu tiedot/karttataso-tietyoilmoitukset)
    (komp/kuuntelija :ilmoitus-klikattu (fn [_ ilmoitus]
                                          (e! (tiedot/->ValitseIlmoitus
                                                (spec-apurit/poista-ei-namespacetetut-avaimet ilmoitus)))))
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
    (fn [e! {valittu-ilmoitus :valittu-ilmoitus
             tallennus-kaynnissa? :tallennus-kaynnissa?
             kayttajan-urakat :kayttajan-urakat :as app}]
      [:span
       [kartta/kartan-paikka]
       (if valittu-ilmoitus
         [tietyoilmoituslomake/lomake e! app tallennus-kaynnissa? valittu-ilmoitus kayttajan-urakat]
         [tietyoilmoitushakulomake/hakulomake e! app])])))
