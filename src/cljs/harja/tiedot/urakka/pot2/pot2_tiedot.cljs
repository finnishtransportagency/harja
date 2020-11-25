(ns harja.tiedot.urakka.pot2.pot2-tiedot

  (:require
    [reagent.core :refer [atom] :as r]
    [tuck.core :refer [process-event] :as tuck]
    [harja.domain.pot2 :as pot2-domain]
    [harja.tyokalut.tuck :as tuck-apurit]
    [harja.loki :refer [log tarkkaile!]])

  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<! reaction-writable]]))

(defonce pot2-nakymassa? (atom false))

(defrecord MuutaTila [polku arvo])
(defrecord PaivitaTila [polku f])
(defrecord HaePot2Tiedot [paallystyskohde-id])
(defrecord HaePot2TiedotOnnistui [vastaus])
(defrecord HaePot2TiedotEpaonnistui [vastaus])

(extend-protocol tuck/Event

  MuutaTila
  (process-event [{:keys [polku arvo]} app]
    (assoc-in app polku arvo))
  PaivitaTila
  (process-event [{:keys [polku f]} app]
    (update-in app polku f))

  HaePot2Tiedot
  (process-event [{paallystyskohde-id :paallystyskohde-id} {urakka :urakka :as app}]
    (let [parametrit {:urakka-id (:id urakka)
                      ::pot2-domain/yllapitokohde-id paallystyskohde-id}]
      (tuck-apurit/post! app
                         :hae-kohteen-pot2-tiedot
                         parametrit
                         {:onnistui ->HaePot2TiedotOnnistui
                          :epaonnistui ->HaePot2TiedotEpaonnistui})))

  HaePot2TiedotOnnistui
  (process-event [{vastaus :vastaus} app]
    (println "HaePot2TiedotOnnistui " (pr-str vastaus))
    (-> app
        (assoc-in [:paallystysilmoitus-lomakedata :perustiedot] (:perustiedot vastaus))))

  HaePot2TiedotEpaonnistui
  ;; fixme implement
  (process-event [{vastaus :vastaus} app]
    (println "HaePot2TiedotEpaonnistui " (pr-str vastaus))
    app))