(ns harja.views.urakka.laadunseuranta
  (:require [reagent.core :refer [atom]]
            [harja.ui.bootstrap :as bs]

            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.laadunseuranta :as urakka-laadunseuranta]
            [harja.views.urakka.laadunseuranta.tarkastukset :as tarkastukset]
            [harja.views.urakka.laadunseuranta.laatupoikkeamat :as laatupoikkeamat]
            [harja.views.urakka.laadunseuranta.sanktiot :as sanktiot]
            [harja.views.urakka.laadunseuranta.mobiilityokalu :as mobiilityokalu]
            [harja.ui.komponentti :as komp]
            [harja.loki :refer [log]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.views.urakka.laadunseuranta.siltatarkastukset :as siltatarkastukset]
            [harja.tiedot.urakka :as tiedot-urakka]))

(defn laadunseuranta [ur]
  (komp/luo
    (komp/lippu urakka-laadunseuranta/laadunseurannassa?)
    (fn [{:keys [id tyyppi] :as ur}]
      [bs/tabs
       {:style :tabs :classes "tabs-taso2"
        :active (nav/valittu-valilehti-atom :laadunseuranta)}

       "Tarkastukset" :tarkastukset
       (when (oikeudet/urakat-laadunseuranta-tarkastukset id)
         [tarkastukset/tarkastukset {:nakyma tyyppi}])

       "Laatupoikkeamat" :laatupoikkeamat
       (when (oikeudet/urakat-laadunseuranta-laatupoikkeamat id)
         [laatupoikkeamat/laatupoikkeamat {:nakyma tyyppi}])

       (if @tiedot-urakka/yllapidon-urakka? "Sakot ja bonukset" "Sanktiot") :sanktiot
       (when (oikeudet/urakat-laadunseuranta-sanktiot id)
         [sanktiot/sanktiot {:nakyma tyyppi}])

       "Siltatarkastukset" :siltatarkastukset
       (when (and (= :hoito tyyppi)
                  (oikeudet/urakat-laadunseuranta-siltatarkastukset id))
         ^{:key "siltatarkastukset"}
         [siltatarkastukset/siltatarkastukset])

       "Mobiilityökalu" :mobiilityokalu
       ^{:key "mobiilityokalu"}
       [mobiilityokalu/mobiilityokalu]])))
