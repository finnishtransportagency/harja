(ns harja.views.urakka.laskutus
  "Päätason sivu Laskutus"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.bootstrap :as bs]
            [harja.tiedot.navigaatio :as nav]
            [harja.asiakas.kommunikaatio :as k]

            [harja.loki :refer [log]]
            [harja.views.urakka.laskutusyhteenveto :as laskutusyhteenveto]
            [harja.views.urakka.kulut.mhu-kustannusten-seuranta :as kustannusten-seuranta]
            [harja.ui.komponentti :as komp]
            [harja.views.urakka.maksuerat :as maksuerat]
            [harja.views.urakka.kulut.kulut :as kohdistetut-kulut]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.urakka :as urakka-domain])

  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]))

(defn laskutus []
  (komp/luo
    (fn []
      (let [mhu-urakka? (= :teiden-hoito
                           (:tyyppi @nav/valittu-urakka))
            vesivayla-hoito-urakka (= :vesivayla-hoito
                                     (:tyyppi @nav/valittu-urakka))]
        [:span.laskutus
         [bs/tabs {:style :tabs :classes "tabs-taso2"
                   :active (nav/valittu-valilehti-atom :laskutus)}
          "Kulujen kohdistus"
          :kohdistetut-kulut
          (when (or mhu-urakka? (and vesivayla-hoito-urakka (k/kehitysymparistossa?)))
            ^{:keys "kohdistetut-kulut"}
            [kohdistetut-kulut/kohdistetut-kulut])

          "Laskutusyhteenveto"
          :laskutusyhteenveto
          (when-not (urakka-domain/vesivaylaurakka? @nav/valittu-urakka)
          ^{:key "laskutusyhteenveto"}
          [laskutusyhteenveto/laskutusyhteenveto])

          "Maksuerät"
          :maksuerat
          ^{:key "maksuerat"}
          (when (and (oikeudet/urakat-kulut-maksuerat (:id @nav/valittu-urakka)) (not (urakka-domain/vesivaylaurakka? @nav/valittu-urakka)))
            [maksuerat/maksuerat-listaus])

          "Kustannusten seuranta"
          :kustannusten-seuranta
          (when mhu-urakka?
            ^{:key "kustannusten-seuranta"}
            [kustannusten-seuranta/kustannusten-seuranta])
          ]]))))
