(ns harja.tiedot.urakka.paikkaukset-toteumat
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [harja.pvm :as pvm]
            [harja.tiedot.navigaatio :as nav]
            [harja.tyokalut.tuck :as tt]
            [harja.ui.viesti :as viesti]
            [harja.ui.grid :as grid]
            [harja.domain.paikkaus :as paikkaus]
            [harja.domain.tierekisteri :as tierekisteri])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def app (atom {:paikkauksien-haku-kaynnissa? false
                :valinnat nil}))

(defn kiinnostavat-tiedot-grid [{tierekisteriosoite ::paikkaus/tierekisteriosoite paikkauskohde ::paikkaus/paikkauskohde
                                 :as paikkaus}]
  (let [sellaisenaan-naytettavat-arvot (select-keys paikkaus #{::paikkaus/tyomenetelma
                                                               ::paikkaus/massatyyppi ::paikkaus/leveys ::paikkaus/massamenekki
                                                               ::paikkaus/raekoko ::paikkaus/kuulamylly ::paikkaus/id})
        alkuaika {::paikkaus/alkuaika (pvm/pvm (::paikkaus/alkuaika paikkaus))}
        loppuaika {::paikkaus/loppuaika (pvm/pvm (::paikkaus/loppuaika paikkaus))}
        nimi (select-keys paikkauskohde #{::paikkaus/nimi})]
    (merge sellaisenaan-naytettavat-arvot alkuaika loppuaika tierekisteriosoite nimi)))

(defn kasittele-haettu-tulos
  [tulos]
  (let [kiinnostavat-tiedot (map #(kiinnostavat-tiedot-grid %)
                                 tulos)
        paikkaukset-grid (mapcat (fn [[otsikko paikkaukset]]
                                   (cons (grid/otsikko otsikko) paikkaukset))
                                 (group-by ::paikkaus/nimi kiinnostavat-tiedot))
        paikkaukset-vetolaatikko (map #(select-keys % [::paikkaus/tienkohdat ::paikkaus/materiaalit ::paikkaus/id])
                                     tulos)]
    {:paikkaukset-grid paikkaukset-grid
     :paikkaukset-vetolaatikko paikkaukset-vetolaatikko}))

;; Muokkaukset
(defrecord PaikkausValittu [paikkauskohde valittu?])
(defrecord PaivitaValinnat [uudet])
(defrecord Nakymaan [])
(defrecord NakymastaPois [])
(defrecord SiirryKustannuksiin [paikkaus-id])
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
      (let [paikkauksien-idt (into #{} (keep #(when (:valittu? %)
                                                  (:id %))
                                               (get-in app [:valinnat :urakan-paikkauskohteet])))
            params (-> (:valinnat app)
                       (dissoc :urakan-paikkauskohteet)
                       (assoc :paikkaus-idt paikkauksien-idt)
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
                       :epaonnistui ->PaikkauksetEiHaettu})
            (assoc :paikkauksien-haku-tulee-olemaan-kaynnissa? true)))
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
    (let [paikkauskohteet (reduce (fn [paikkaukset paikkaus]
                                    (if (some #(= (:id %) (get-in paikkaus [::paikkaus/paikkauskohde ::paikkaus/id]))
                                              paikkaukset)
                                      paikkaukset
                                      (conj paikkaukset
                                            {:id (get-in paikkaus [::paikkaus/paikkauskohde ::paikkaus/id])
                                             :nimi (get-in paikkaus [::paikkaus/paikkauskohde ::paikkaus/nimi])
                                             :valittu? true})))
                                  [] tulos)
          naytettavat-tiedot (kasittele-haettu-tulos tulos)]
      (-> app
          (merge naytettavat-tiedot)
          (assoc :paikkauksien-haku-kaynnissa? false)
          (assoc-in [:valinnat :urakan-paikkauskohteet] paikkauskohteet))))
  PaikkauksetHaettu
  (process-event [{tulos :tulos} app]
    (let [naytettavat-tiedot (kasittele-haettu-tulos tulos)]
      (-> app
          (merge naytettavat-tiedot)
          (assoc :paikkauksien-haku-kaynnissa? false
                 :paikkauksien-haku-tulee-olemaan-kaynnissa? false))))
  PaikkauksetEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Paikkausten haku epäonnistui!" :danger)
    (assoc app
           :paikkauksien-haku-kaynnissa? false
           :paikkauksien-haku-tulee-olemaan-kaynnissa? false))
  SiirryKustannuksiin
  (process-event [{paikkaus-id :paikkaus-id} app]
    (js/console.log "SIIRTYMÄ NAPPIA PAINETTU")
    app))