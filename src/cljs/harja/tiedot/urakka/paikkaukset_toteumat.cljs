(ns harja.tiedot.urakka.paikkaukset-toteumat
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def app (atom {:paikkauksien-haku-kaynnissa? false
                :valinnat {:tr nil}}))

;; Muokkaukset
(defrecord PaikkausValittu [paikkauskohde valittu?])
(defrecord PaivitaValinnat [u])

(defn valinta-wrap [e! app polku]
  (r/wrap (get-in app [:valinnat polku])
          (fn [u]
            (e! (->PaivitaValinnat {polku u})))))

(def valintojen-avaimet [:tr :aika :urakan-paikkauskohteet])

(extend-protocol tuck/Event
  PaivitaValinnat
  (process-event [{u :uudet} app]
    (let [uudet-valinnat (merge (:valinnat app)
                                (select-keys u valintojen-avaimet))
          #_#_haku (tuck/send-async! ->HaePaikkaukset)]
      #_(go (haku uudet-valinnat))
      (assoc app :valinnat uudet-valinnat)))
  PaikkausValittu
  (process-event [{{:keys [id]} :paikkauskohde valittu? :valittu?} app]
    (let [uudet-paikkausvalinnat (map #(if (= (:id %) id)
                                       (assoc % :valittu? valittu?)
                                       %)
                                    (get-in app [:valinnat :urakan-paikkauskohteet]))]
      (tuck/process-event (->PaivitaValinnat {:urakan-paikkauskohteet uudet-paikkausvalinnat}) app)
      (assoc-in app [:valinnat :urakan-paikkauskohteet] uudet-paikkausvalinnat))))