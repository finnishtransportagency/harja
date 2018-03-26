(ns harja.tiedot.urakka.paikkaukset-toteumat
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [harja.pvm :as pvm]
            [harja.tiedot.navigaatio :as nav]
            [harja.tyokalut.tuck :as tt]
            [harja.ui.viesti :as viesti]
            [harja.domain.paikkaus :as paikkaus]
            [harja.domain.tierekisteri :as tierekisteri])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def app (atom {:paikkauksien-haku-kaynnissa? false
                :valinnat nil}))

(defn kiinnostavat-tiedot [{tierekisteriosoite ::paikkaus/tierekisteriosoite paikkauskohde ::paikkaus/paikkauskohde
                            :as paikkaus}]
  (let [sellaisenaan-naytettavat-arvot (select-keys paikkaus #{::paikkaus/tyomenetelma
                                    ::paikkaus/massatyyppi ::paikkaus/leveys ::paikkaus/massamenekki
                                    ::paikkaus/raekoko ::paikkaus/kuulamylly ::paikkaus/id})
        alkuaika {::paikkaus/alkuaika (pvm/pvm (::paikkaus/alkuaika paikkaus))}
        loppuaika {::paikkaus/loppuaika (pvm/pvm (::paikkaus/loppuaika paikkaus))}
        nimi (select-keys paikkauskohde #{::paikkaus/nimi})]
    (merge sellaisenaan-naytettavat-arvot alkuaika loppuaika tierekisteriosoite nimi)))

;; Muokkaukset
(defrecord PaikkausValittu [paikkauskohde valittu?])
(defrecord PaivitaValinnat [uudet])
(defrecord Nakymaan [])
(defrecord NakymastaPois [])
;; Haut
(defrecord HaePaikkaukset [])
(defrecord EnsimmainenHaku [tulos])
(defrecord PaikkauksetHaettu [tulos])
(defrecord PaikkauksetEiHaettu [])
(defrecord HaePaikkauksetKutsuLahetetty [])

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
                      {:viive 1000
                       :tunniste :hae-paikkaukset-toteumat-nakymaan
                       :lahetetty ->HaePaikkauksetKutsuLahetetty
                       :onnistui ->PaikkauksetHaettu
                       :epaonnistui ->PaikkauksetEiHaettu})))
      app))
  HaePaikkauksetKutsuLahetetty
  (process-event [_ app]
    (assoc app :paikkauksien-haku-kaynnissa? true))
  Nakymaan
  (process-event [_ app]
    (-> app
        (tt/post! :hae-urakan-paikkauskohteet
                  {::paikkaus/urakka-id @nav/valittu-urakka-id}
                  {:onnistui ->EnsimmainenHaku
                   :epaonnistui ->PaikkauksetEiHaettu})
        (assoc :nakymassa? true
               :paikkauksien-haku-kaynnissa? true)))
  NakymastaPois
  (process-event [_ app]
    (assoc app :nakymassa? false))
  EnsimmainenHaku
  (process-event [{tulos :tulos} app]
    (println "TULOS")
    (cljs.pprint/pprint tulos)
    (let [paikkauskohteet (map (fn [paikkaus]
                                 {:nimi (get-in paikkaus [::paikkaus/paikkauskohde ::paikkaus/nimi])
                                  :id (::paikkaus/id paikkaus)
                                  :valittu? true})
                               tulos)
          paikkaukset (map #(kiinnostavat-tiedot %)
                           tulos)]
      (-> app
          (assoc :paikkaukset paikkaukset
                 :paikkauksien-haku-kaynnissa? false)
          (assoc-in [:valinnat :urakan-paikkauskohteet] paikkauskohteet))))
  PaikkauksetHaettu
  (process-event [{tulos :tulos} app]
    (let [paikkaukset (map #(kiinnostavat-tiedot %)
                           tulos)]
      (assoc app
             :paikkaukset paikkaukset
             :paikkauksien-haku-kaynnissa? false)))
  PaikkauksetEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Liikennetapahtumien haku epäonnistui! " :danger)
    (assoc app :paikkauksien-haku-kaynnissa? false)))