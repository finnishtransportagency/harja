(ns harja.tiedot.hallinta.kojelauta
  (:require [harja.ui.viesti :as viesti]
            [harja.ui.protokollat :as protokollat]
            [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(defn tee-urakkahaku [urakat]
  (reify protokollat/Haku
    (hae [_ teksti]
      (go (let [itemit (if (< (count teksti) 1)
                         urakat
                         (filter #(and
                                    (:nimi %)
                                    (not= (.indexOf (.toLowerCase (:nimi %))
                                            (.toLowerCase teksti)) -1))
                           urakat))]
            (vec (sort-by :nimi itemit)))))))

(def tila (atom {:urakkavuodet (range 2016 2025)
                 :kriteerit [{:nimi "Jotain"}
                             {:nimi "Jotain muuta"}]
                 :urakat [{:nimi "POP MHU Kajaani 2024-2029"}
                          {:nimi "POP MHU Oulu 2019-2024"}]
                 :valinnat {}}))

(defrecord Valitse [avain valinta])
(defrecord HaeUrakat [])

(extend-protocol tuck/Event
  Valitse
  (process-event [{:keys [avain valinta]} app]
    (assoc-in app [:valinnat avain] valinta))

  HaeUrakat
  (process-event [_ app]
    (let [urakat [{:nimi "POP MHU Kajaani 2024-2029"}
                  {:nimi "POP MHU Oulu 2019-2024"}]]
      (assoc app
        :urakat urakat
        :urakkahaku (tee-urakkahaku urakat)))))
