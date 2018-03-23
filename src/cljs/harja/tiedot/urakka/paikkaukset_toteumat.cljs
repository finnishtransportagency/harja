(ns harja.tiedot.urakka.paikkaukset-toteumat
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [harja.tiedot.navigaatio :as nav]
            [harja.tyokalut.tuck :as tt]
            [harja.ui.viesti :as viesti]
            [harja.domain.paikkaus :as paikkaus])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def app (atom {:paikkauksien-haku-kaynnissa? false
                :valinnat {:tr nil}}))

;; Muokkaukset
(defrecord PaikkausValittu [paikkauskohde valittu?])
(defrecord PaivitaValinnat [uudet])
(defrecord Nakymaan [])
(defrecord NakymastaPois [])
;; Haut
(defrecord HaePaikkaukset [])
(defrecord PaikkauksetHaettu [tulos])
(defrecord PaikkauksetEiHaettu [])

(defn valinta-wrap [e! app polku]
  (r/wrap (get-in app [:valinnat polku])
          (fn [u]
            (e! (->PaivitaValinnat {polku u})))))

(def valintojen-avaimet [:tr :aikavali :urakan-paikkauskohteet])

(extend-protocol tuck/Event
  PaivitaValinnat
  (process-event [{u :uudet} app]
    (let [uudet-valinnat (merge (:valinnat app)
                                (select-keys u valintojen-avaimet))
          haku (tuck/send-async! ->HaePaikkaukset)]
      (go (haku uudet-valinnat))
      ;(println "UUDET VALINNAT " (pr-str uudet-valinnat))
      (assoc app :valinnat uudet-valinnat)))
  PaikkausValittu
  (process-event [{{:keys [id]} :paikkauskohde valittu? :valittu?} app]
    (let [uudet-paikkausvalinnat (map #(if (= (:id %) id)
                                         (assoc % :valittu? valittu?)
                                         %)
                                      (get-in app [:valinnat :urakan-paikkauskohteet]))]
      (tuck/process-event (->PaivitaValinnat {:urakan-paikkauskohteet uudet-paikkausvalinnat}) app)
      (assoc-in app [:valinnat :urakan-paikkauskohteet] uudet-paikkausvalinnat)))
  HaePaikkaukset
  (process-event [_ app]
    (if-not (:paikkauksien-haku-kaynnissa? app)
      (let [paikkaus-idt (into #{} (keep #(when (:valittu? %)
                                            (:id %))
                                         (get-in app [:valinnat :urakan-paikkauskohteet])))
            params (-> (:valinnat app)
                       (dissoc :urakan-paikkauskohteet)
                       (assoc :paikkaus-idt paikkaus-idt)
                       (assoc ::paikkaus/urakka-id @nav/valittu-urakka-id))]
        (-> app
            (tt/post! :hae-urakan-paikkauskohteet
                      params
                      ;; Checkbox-group ja aluksen nimen kirjoitus generoisi
                      ;; liikaa requesteja ilman viivettä.
                      {#_#_:viive 1000
                       #_#_:lahetetty ->HaeLiikennetapahtumatKutsuLahetetty
                       :onnistui ->PaikkauksetHaettu
                       :epaonnistui ->PaikkauksetEiHaettu})
            (assoc :liikennetapahtumien-haku-tulee-olemaan-kaynnissa? true)))
      app))
  Nakymaan
  (process-event [_ app]
    (-> app
        (tt/post! :hae-urakan-paikkauskohteet
                  {::paikkaus/urakka-id @nav/valittu-urakka-id}
                  {:onnistui ->PaikkauksetHaettu
                   :epaonnistui ->PaikkauksetEiHaettu})
        (assoc :nakymassa? true)))
  NakymastaPois
  (process-event [_ app]
    (assoc app :nakymassa? false))
  PaikkauksetHaettu
  (process-event [{tulos :tulos} app]
    (println "TULOS")
    (cljs.pprint/pprint tulos)
    app)
  PaikkauksetEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Liikennetapahtumien haku epäonnistui! " :danger)
    (assoc app :paikkauksien-haku-kaynnissa? false)))