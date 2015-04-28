(ns harja.views.urakka.toteumat
  "Urakan 'Toteumat' välilehti:"
  (:require [reagent.core :refer [atom] :as reagent]
            [bootstrap :as bs]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija linkki sisalla? raksiboksi
                                      livi-pudotusvalikko]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.urakka.suunnittelu :as s]
            [harja.tiedot.urakka.toteumat :as toteumat]
            [harja.tiedot.istunto :as istunto]

            [harja.loki :refer [log logt]]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [cljs.core.async :refer [<!]]
            [clojure.string :as str]
            [cljs-time.core :as t])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]))

(defn toteumat [ur]
  (let [toteumat (atom nil)
        urakka (atom nil)
        hae-urakan-tiedot (fn [ur]
                            (reset! urakka ur)
                            ;; Tehdään hoitokauden osien (10-12 / 1-9) yhdistäminen  urakalle
                            (go (reset! toteumat (<! (toteumat/hae-urakan-toteumat (:id ur))))))]
    (hae-urakan-tiedot ur)
    (komp/luo
      {:component-will-receive-props
       (fn [_ & [_ ur]]
         (log "UUSI URAKKA: " (pr-str (dissoc ur :alue)))
         (hae-urakan-tiedot ur))}

      (fn [ur]
        [:div.toteumat "Toteumat"]))))