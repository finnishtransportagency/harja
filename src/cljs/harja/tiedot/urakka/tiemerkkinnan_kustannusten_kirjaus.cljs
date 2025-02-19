(ns harja.tiedot.urakka.tiemerkkinnan-kustannusten-kirjaus  "Tiemerkintäurakan Kustannukset-välilehden tiedot"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<! >! chan]]
            [harja.ui.raportti :refer [muodosta-html]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.transit :as t]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.raportit :as raportit])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))

(defonce kustannusten-kirjaus-valilehti-nakyvissa? (atom false))

(defn tallenna-tiemerkinnan-kustannukset! [urakka-id kustannukset]
  ;;(println "urakka-id tal" urakka-id " kustannukset: " kustannukset)
  (k/post! :tallenna-tiemerkinta-kustannuskirjaukset
    {:urakka urakka-id
     :kustannukset kustannukset}))

(defn hae-tiemerkinnan-kustannukset [urakka-id urakka]
  (k/post! :hae-tiemerkinta-kustannuskirjaukset urakka-id))

(def tiemerkintaurakan-kustannukset
  (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
               urakka @nav/valittu-urakka
               nakymassa? @kustannusten-kirjaus-valilehti-nakyvissa?]
    {:nil-kun-haku-kaynnissa? true}
    (when (and valittu-urakka-id kustannusten-kirjaus-valilehti-nakyvissa?)
      (hae-tiemerkinnan-kustannukset valittu-urakka-id urakka))))
