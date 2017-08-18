(ns harja.tiedot.hallinta.harja-data.analyysi
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log tarkkaile!]]
            [tuck.core :refer [Event process-event] :as tuck])

  (:require-macros [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(defonce app (atom {:kaytossa {:naytettavat-ryhmat #{:tallenna :hae :urakan :muut}}
                    :hakuasetukset {:naytettavat-ryhmat #{:tallenna :hae :urakka :muut}}
                    :hakuasetukset-nakyvilla? false
                    :analyysi-tehty? false
                    :analyysi {}
                    :analysointimetodi :naivi
                    :haettavat-analyysit #{:eniten-katkoksia :pisimmat-katkokset
                                           :rikkinaiset-lokitukset :eniten-katkosryhmia
                                           :selain-sammutettu-katkoksen-aikana
                                           :vaihdettu-nakymaa-katkoksen-aikana}}))

(defn graylog-palvelukutsu
  "Hakee serveriltä yhteyskatkosdatan."
  [palvelu callback hakuasetukset]
  (go (let [lokitukset-visualisointia-varten (<! (k/post! palvelu hakuasetukset))]
        (callback lokitukset-visualisointia-varten))))

(defn graylog-hae-analyysi
  [callback hakuasetukset]
  (graylog-palvelukutsu :graylog-hae-analyysi
                        (tuck/send-async! callback)
                        hakuasetukset))

(defrecord PaivitaArvo [arvo avain])
(defrecord PaivitaArvoFunktio [funktio avain])
(defrecord Nakymassa? [nakymassa?])
(defrecord HaeAnalyysi [])
(defrecord PaivitaAnalyysiArvot [analyysi])

(extend-protocol Event
  PaivitaArvo
  (process-event [{:keys [arvo avain]} app]
    (assoc app avain arvo))
  PaivitaArvoFunktio
  (process-event [{:keys [funktio avain]} app]
    (update app avain #(funktio %)))
  Nakymassa?
  (process-event [{nakymassa? :nakymassa?} app]
    (assoc app :nakymassa? nakymassa?))
  HaeAnalyysi
  (process-event [_ {:keys [analysointimetodi haettavat-analyysit hakuasetukset] :as app}]
    (let [app (assoc app :analyysi-tehty? false)]
      (graylog-hae-analyysi ->PaivitaAnalyysiArvot {:analysointimetodi analysointimetodi
                                                    :haettavat-analyysit haettavat-analyysit
                                                    :hakuasetukset hakuasetukset})
      app))
  PaivitaAnalyysiArvot
  (process-event [{analyysi :analyysi} app]
    (assoc app :analyysi analyysi
               :analyysi-tehty? true)))
