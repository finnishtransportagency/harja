(ns harja.tiedot.urakka.yhatuonti
  (:require [reagent.core :refer [atom] :as reagent]
            [harja.ui.yleiset :refer [ajax-loader vihje]]
            [harja.loki :refer [log logt tarkkaile!]]
            [cljs.core.async :refer [<! >! chan timeout]]
            [harja.ui.lomake :refer [lomake]]
            [harja.ui.grid :refer [grid]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.modal :as modal]
            [cljs-time.core :as t]
            [harja.tiedot.navigaatio :as nav])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]))

(def hakulomake-data (atom nil))
(def hakutulokset-data (atom []))
(def sidonta-kaynnissa? (atom false))


(defn hae-yha-urakat [{:keys [nimi tunniste vuosi] :as hakuparametrit}]
  (log "[YHA] Hae YHA-urakat...")
  (reset! hakutulokset-data nil)
  #_(k/post! :hae-yha-urakat {:nimi nimi
                            :tunniste tunniste
                            :vuosi vuosi})
  ;; FIXME Hae YHA-urakat, toistaiseksi palauta vain testidata
  (go (do
        (<! (timeout 2000))
        [{:yhatunnus "YHA1" :yhaid 5 :yhanimi "YHA-urakka" :elyt "Pohjois-Pohjanmaa" :vuodet 2010}])))



(defn- sido-yha-urakka-harja-urakkaan [harja-urakka-id yha-tiedot]
  (log "[YHA] Sidotaan Harja-urakka " harja-urakka-id " yha-urakkaan: " (pr-str yha-tiedot))
  (reset! sidonta-kaynnissa? true)
  (go
    ;; FIXME Palauta urakan tiedot kannasta alla olevan testidatan mukaisesti
    (let [vastaus (<! (k/post! :sido-yha-urakka-harja-urakkaan {:harja-urakka-id harja-urakka-id
                                                                :yhatiedot yha-tiedot}))]
      (<! (timeout 2000))
      (log "[YHA] Sidonta suoritettu")
      (reset! sidonta-kaynnissa? false)
      (reset! nav/valittu-urakka vastaus)
      (modal/piilota!))))