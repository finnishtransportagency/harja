(ns harja.tiedot.urakka.suunnittelu.mhu-kustannussuunnitelma
  (:require [tuck.core :as tuck]
            [harja.loki :refer [log]]
            [harja.ui.taulukko.protokollat :as p]
            [harja.ui.taulukko.tyokalut :as tyokalut]))


(def toimenpiteet #{:talvihoito
                    :liikenneympariston-hoito
                    :sorateiden-hoito
                    :paallystepaikkaukset
                    :mhu-yllapito
                    :mhu-korvausinvestointi})

(def talvikausi [10 11 12 1 2 3 4])
(def kesakausi (into [] (range 5 10)))
(def hoitokausi (concat talvikausi kesakausi))

(def kaudet {:kesa kesakausi
             :talvi talvikausi
             :kaikki hoitokausi})

(defrecord LaajennaSoluaKlikattu [taulukon-polku rivin-id this auki?])
(defrecord PaivitaToimenpiteenHankintaMaara [osa arvo])

(extend-protocol tuck/Event
  LaajennaSoluaKlikattu
  (process-event [{:keys [taulukon-polku rivin-id auki?]} app]
    (update-in app taulukon-polku (fn [rivit]
                                               (mapv (fn [rivi]
                                                       (if (= (-> rivi meta :vanhempi) rivin-id)
                                                         (if auki?
                                                           (update rivi :luokat disj "piillotettu")
                                                           (update rivi :luokat conj "piillotettu"))
                                                         rivi))
                                                     rivit))))
  PaivitaToimenpiteenHankintaMaara
  (process-event [{:keys [osa arvo]} app]
    (let [[janan-index solun-index] (tyokalut/osan-polku-taulukossa (get-in app [:hankintakustannukset :hankintataulukko]) osa)]
      (update-in app [:hankintakustannukset :hankintataulukko janan-index :solut solun-index :parametrit]
                 (fn [parametrit]
                   (assoc parametrit :value arvo))))))