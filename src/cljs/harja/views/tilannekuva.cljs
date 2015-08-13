(ns harja.views.tilannekuva
  "Harjan tilannekuvan pääsivu."
  (:require [reagent.core :refer [atom]]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.tilannekuva :as tiedot]
            [harja.loki :refer [log]]))


(defonce suodattimet-rivit (atom {1 {:auki true :otsikko "Live" :sisalto [:p "Livesuodatin"]}
                                  2 {:auki false :otsikko "Historia" :sisalto [:p "Etsi historiasta"]}}))

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
    {:component-will-mount
     (fn [] #_(when (or (= @nav/kartan-koko :hidden) (= @nav/kartan-koko :S)) (nav/vaihda-kartan-koko! :M)))}
    (fn []
      [:div "Tänne tulee myöhemmin tilannekuva..."]
      (harja.ui.yleiset/haitari hallintapaneeli {:piiloita-kun-kiinni? true
                                                 ;; Leijuva arvona voi antaa top-px arvon tai booleanin.
                                                 :leijuva?             300}))))