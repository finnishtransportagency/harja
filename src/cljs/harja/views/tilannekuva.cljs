(ns harja.views.tilannekuva
  "Harjan tilannekuvan pääsivu."
  (:require [reagent.core :refer [atom]]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.tilannekuva :as tiedot]
            [harja.loki :refer [log]]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.kentat :as kentat])
  (:require-macros [reagent.ratom :refer [reaction run!]]))


(defn historiasuodatin []
  [kentat/tee-kentta {:tyyppi :aikavalitsin
                      :kellonaika {
                                   :valinnat ["00:00" "06:00" "12:00" "18:00"]
                                   }} tiedot/historiasuodattimen-asetukset])

(defn livesuodatin []
  [kentat/tee-kentta {:tyyppi :radio
                      :valinnat ["0-4h" "0-12h" "0-24h"]}
   tiedot/livesuodattimen-asetukset])

(defonce suodattimet-rivit (atom {1 {:auki (= :live @tiedot/valittu-aikasuodatin)
                                     :otsikko "Live" :sisalto [livesuodatin]}
                                  2 {:auki (not (= :live @tiedot/valittu-aikasuodatin))
                                     :otsikko "Historia" :sisalto [historiasuodatin]}}))

(defonce aikasuodattimet [harja.ui.yleiset/haitari suodattimet-rivit {:vain-yksi-auki? true
                                                                      :aina-joku-auki? true
                                                                      :otsikko "Suodata ajan mukaan"}])

(defonce toteumat-rivit (atom {1 {:auki false :otsikko "Jotain" :sisalto [:p "Sisältöä"]}
                               2 {:auki false :otsikko "Myös jotain" :sisalto [:p "Ja lisää"]}}))

(defonce toteumat [harja.ui.yleiset/haitari toteumat-rivit {:otsikko "Muut suodattimet"}])

(defonce suodattimet [:span
                      aikasuodattimet
                      toteumat])

(defonce hallintapaneeli (atom {1 {:auki false :otsikko "Esimerkki" :sisalto suodattimet}}))

(defn tilannekuva []
  (komp/luo
    (komp/lippu tiedot/nakymassa? tiedot/taso-tilannekuva)
    (fn []
      (reaction (reset! tiedot/valittu-aikasuodatin (if (get-in @suodattimet-rivit [1 :auki])
                                                      :live
                                                      :historia)))

      [harja.ui.yleiset/haitari hallintapaneeli {:piiloita-kun-kiinni? true
                                                 :leijuva?             300}])))