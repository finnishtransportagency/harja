(ns harja.views.hallinta.integraatiotilanne
  (:require [harja.views.hallinta.integraatioloki :as integraatioloki]
            [harja.views.hallinta.jms-jonot :as jms-jonot]
            [harja.domain.oikeudet :as oikeudet]
            [harja.ui.komponentti :as komp]
            [harja.ui.bootstrap :as bs]
            [harja.tiedot.navigaatio :as nav]))

(defn integraatiotilanne
  []
  (komp/luo
    (fn []
      [:span.kohdeluettelo
       [bs/tabs {:style :tabs :classes "tabs-taso2"
                 :active (nav/valittu-valilehti-atom :integraatioloki)}

        "Integraatioloki"
        :integraatioloki
        (when (oikeudet/hallinta-integraatiotilanne-integraatioloki)
          ^{:key "integraatioloki"}
          [integraatioloki/integraatioloki])

        "Sonjajonot"
        :sonjajonot
        (when (oikeudet/hallinta-integraatiotilanne-sonjajonot)
          ^{:key "sonjajonot"}
          [jms-jonot/jms-jonot])]])))