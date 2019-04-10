(ns harja.tiedot.urakka.tarkka-aikataulu
  "Ylläpidon urakoiden yksityiskohtainen aikataulu"
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log logt]]
            [cljs.core.async :refer [<!]]
            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(defn tallenna-aikataulu [{:keys [rivit urakka-id sopimus-id vuosi yllapitokohde-id onnistui-fn epaonnistui-fn]}]
  (go
    (let [vastaus (<! (k/post! :tallenna-yllapitokohteiden-tarkka-aikataulu
                               {:urakka-id urakka-id
                                :yllapitokohde-id yllapitokohde-id
                                :sopimus-id sopimus-id
                                :vuosi vuosi
                                :aikataulurivit rivit}))]
      (if (k/virhe? vastaus)
        (epaonnistui-fn)
        (onnistui-fn vastaus)))))
