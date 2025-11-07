(ns harja.views.vesivaylat.urakka.toimenpiteet
  (:require [reagent.core :refer [atom]]
            [harja.ui.komponentti :as komp]
            [harja.ui.bootstrap :as bs]
            [harja.views.kanavat.urakka.toimenpiteet.kokonaishintaiset :as kanava-kok-hint]
            [harja.views.kanavat.urakka.toimenpiteet.muutos-ja-lisatyot :as lisatyot]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.istunto :as istunto]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.urakka :as urakka-domain])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn toimenpiteet []
  (komp/luo
    (fn [{:keys [id] :as ur}]
      [bs/tabs {:style :tabs :classes "tabs-taso2"
                :active (nav/valittu-valilehti-atom :toimenpiteet)}

       "Kokonaishintaiset"
       :kanavien-kokonaishintaiset
       (when (and (istunto/ominaisuus-kaytossa? :vesivayla)
               (urakka-domain/kanavaurakka? ur)
               (oikeudet/urakat-kanavat-kokonaishintaiset id))
         [kanava-kok-hint/kokonaishintaiset])

       "Muutos- ja lisätyöt"
       :kanavien-lisatyot
       (when (and (istunto/ominaisuus-kaytossa? :vesivayla)
               (not (urakka-domain/kanavaurakka? ur))
               (oikeudet/urakat-kanavat-lisatyot id))
         [lisatyot/lisatyot])])))
