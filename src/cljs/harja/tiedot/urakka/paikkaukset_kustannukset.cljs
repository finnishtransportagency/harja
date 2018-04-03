(ns harja.tiedot.urakka.paikkaukset-kustannukset
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [harja.pvm :as pvm]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.paikkaukset-yhteinen :as yhteinen-tiedot]
            [harja.tyokalut.tuck :as tt]
            [harja.ui.viesti :as viesti]
            [harja.ui.grid :as grid]
            [harja.ui.napit :as napit]
            [harja.domain.paikkaus :as paikkaus]
            [harja.domain.tierekisteri :as tierekisteri])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def app (atom {:paikkauksien-haku-kaynnissa? false
                :valinnat nil}))

(defn kiinnostavat-tiedot-grid [paikkaus]
  (let [sellaisenaan-naytettavat-arvot (select-keys paikkaus #{:selite :yksikko :yksikkohinta :paikkaustoteuma-id
                                                               :maara :hinta :tyyppi :paikkauskohde-id :nimi})
        kirjausaika {:kirjattu (pvm/pvm (:kirjattu paikkaus))}]
    (merge sellaisenaan-naytettavat-arvot kirjausaika)))

;; Muokkaukset
(defrecord PaikkausValittu [paikkauskohde valittu? otsikko-fn])
(defrecord PaivitaValinnat [uudet otsikko-fn])
(defrecord Nakymaan [otsikko-fn])
(defrecord NakymastaPois [])
(defrecord SiirryToimenpiteisiin [paikkaus-id])
;; Haut
(defrecord HaeKustannukset [otsikko-fn])
(defrecord EnsimmainenHaku [tulos otsikko-fn])
(defrecord KustannuksetHaettu [tulos otsikko-fn])
(defrecord KustannuksetEiHaettu [])
(defrecord HaeKustannuksetKutsuLahetetty [])

(defn kasittele-haettu-tulos
  [tulos otsikko-fn]
  (let [kiinnostavat-tiedot (map #(kiinnostavat-tiedot-grid %)
                                 tulos)
        kokonaishintaiset-tiedot (filter #(= (:tyyppi %) "kokonaishintainen") kiinnostavat-tiedot)
        yksikkohintaiset-tiedot (filter #(= (:tyyppi %) "yksikkohintainen") kiinnostavat-tiedot)
        kokonaishintaiset-grid (mapcat (fn [[otsikko paikkaukset]]
                                         (cons (grid/otsikko otsikko {:otsikkokomponentit (otsikko-fn (:paikkauskohde-id (first paikkaukset)))}) paikkaukset))
                                       (group-by :nimi kokonaishintaiset-tiedot))
        yksikkohintaset-grid (mapcat (fn [[otsikko paikkaukset]]
                                       (cons (grid/otsikko otsikko {:otsikkokomponentit (otsikko-fn (:paikkauskohde-id (first paikkaukset)))}) paikkaukset))
                                     (group-by :nimi yksikkohintaiset-tiedot))]
    {:yksikkohintaiset-grid yksikkohintaset-grid
     :kokonaishintaiset-grid kokonaishintaiset-grid}))

(defn valinta-wrap [e! app otsikko-fn polku]
  (r/wrap (get-in app [:valinnat polku])
          (fn [u]
            (e! (->PaivitaValinnat {polku u} otsikko-fn)))))

(def valintojen-avaimet [:tr :aikavali :urakan-paikkauskohteet])

(extend-protocol tuck/Event
  PaivitaValinnat
  (process-event [{u :uudet otsikko-fn :otsikko-fn} app]
    (let [uudet-valinnat (merge (:valinnat app)
                                (select-keys u valintojen-avaimet))
          haku (tuck/send-async! ->HaeKustannukset otsikko-fn)]
      (go (haku uudet-valinnat))
      (assoc app :valinnat uudet-valinnat)))
  PaikkausValittu
  (process-event [{{:keys [id]} :paikkauskohde valittu? :valittu? otsikko-fn :otsikko-fn} app]
    (let [uudet-paikkausvalinnat (map #(if (= (:id %) id)
                                         (assoc % :valittu? valittu?)
                                         %)
                                      (get-in app [:valinnat :urakan-paikkauskohteet]))]
      (tuck/process-event (->PaivitaValinnat {:urakan-paikkauskohteet uudet-paikkausvalinnat} otsikko-fn) app)
      (assoc-in app [:valinnat :urakan-paikkauskohteet] uudet-paikkausvalinnat)))
  HaeKustannukset
  (process-event [{otsikko-fn :otsikko-fn} app]
    (if-not (:paikkauksien-haku-kaynnissa? app)
      (let [paikkauksien-idt (into #{} (keep #(when (:valittu? %)
                                                (:id %))
                                             (get-in app [:valinnat :urakan-paikkauskohteet])))
            params (-> (:valinnat app)
                       (dissoc :urakan-paikkauskohteet)
                       (assoc :paikkaus-idt paikkauksien-idt)
                       (assoc ::paikkaus/urakka-id @nav/valittu-urakka-id))]
        (-> app
            (tt/post! :hae-paikkausurakan-kustannukset
                      params
                      ;; Checkbox-group ja aluksen nimen kirjoitus generoisi
                      ;; liikaa requesteja ilman viivettä.
                      {:viive 1000
                       :tunniste :hae-paikkaukset-kustannukset-nakymaan
                       :lahetetty ->HaeKustannuksetKutsuLahetetty
                       :onnistui ->KustannuksetHaettu
                       :onnistui-parametrit [otsikko-fn]
                       :epaonnistui ->KustannuksetEiHaettu})
            (assoc :paikkauksien-haku-tulee-olemaan-kaynnissa? true)))
      app))
  HaeKustannuksetKutsuLahetetty
  (process-event [_ app]
    (assoc app :paikkauksien-haku-kaynnissa? true))
  Nakymaan
  (process-event [{otsikko-fn :otsikko-fn} app]
    (-> app
        (tt/post! :hae-paikkausurakan-kustannukset
                  {::paikkaus/urakka-id @nav/valittu-urakka-id}
                  {:onnistui ->EnsimmainenHaku
                   :onnistui-parametrit [otsikko-fn]
                   :epaonnistui ->KustannuksetEiHaettu})
        (assoc :nakymassa? true
               :paikkauksien-haku-kaynnissa? true)))
  NakymastaPois
  (process-event [_ app]
    (assoc app :nakymassa? false))
  EnsimmainenHaku
  (process-event [{tulos :tulos otsikko-fn :otsikko-fn} app]
    (let [toteumista-tultu-kohde @yhteinen-tiedot/paikkauskohde
          paikkauskohteet (reduce (fn [paikkaukset paikkaus]
                                    (if (some #(= (:id %) (:paikkauskohde-id paikkaus))
                                              paikkaukset)
                                      paikkaukset
                                      (conj paikkaukset
                                            {:id (:paikkauskohde-id paikkaus)
                                             :nimi (:nimi paikkaus)
                                             :valittu? (or (nil? toteumista-tultu-kohde)
                                                           (= (:id toteumista-tultu-kohde)
                                                              (:paikkauskohde-id paikkaus)))})))
                                  [] tulos)
          naytettavat-tulokset (filter #(or (nil? toteumista-tultu-kohde)
                                            (= (:id toteumista-tultu-kohde)
                                               (:paikkauskohde-id %)))
                                       tulos)
          naytettavat-tiedot (kasittele-haettu-tulos naytettavat-tulokset otsikko-fn)]
      (reset! yhteinen-tiedot/paikkauskohde nil)
      (-> app
          (merge naytettavat-tiedot)
          (assoc :paikkauksien-haku-kaynnissa? false)
          (assoc-in [:valinnat :urakan-paikkauskohteet] paikkauskohteet))))
  KustannuksetHaettu
  (process-event [{tulos :tulos otsikko-fn :otsikko-fn} app]
    (let [naytettavat-tiedot (kasittele-haettu-tulos tulos otsikko-fn)]
      (-> app
          (merge naytettavat-tiedot)
          (assoc :paikkauksien-haku-kaynnissa? false
                 :paikkauksien-haku-tulee-olemaan-kaynnissa? false))))
  KustannuksetEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Liikennetapahtumien haku epäonnistui! " :danger)
    (assoc app
           :paikkauksien-haku-kaynnissa? false
           :paikkauksien-haku-tulee-olemaan-kaynnissa? false))
  SiirryToimenpiteisiin
  (process-event [{paikkaus-id :paikkaus-id} app]
    (js/console.log "SIIRTYMÄ NAPPIA PAINETTU")
    app))