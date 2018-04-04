(ns harja.tiedot.urakka.paikkaukset-kustannukset
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [harja.pvm :as pvm]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.navigaatio.reitit :as reitit]
            [harja.tiedot.urakka.paikkaukset-yhteinen :as yhteiset-tiedot]
            [harja.tyokalut.tuck :as tt]
            [harja.ui.viesti :as viesti]
            [harja.ui.grid :as grid]
            [harja.domain.paikkaus :as paikkaus])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def app (atom {:paikkauksien-haku-kaynnissa? false
                :valinnat nil}))

(defn kiinnostavat-tiedot-grid [paikkaus]
  (let [sellaisenaan-naytettavat-arvot (select-keys paikkaus #{:selite :yksikko :yksikkohinta :paikkaustoteuma-id
                                                               :maara :hinta :tyyppi :paikkauskohde-id :nimi})
        kirjausaika {:kirjattu (pvm/pvm (:kirjattu paikkaus))}]
    (merge sellaisenaan-naytettavat-arvot kirjausaika)))

;; Muokkaukset
(defrecord PaikkausValittu [paikkauskohde valittu?])
(defrecord PaivitaValinnat [uudet])
(defrecord Nakymaan [otsikkokomponentti])
(defrecord NakymastaPois [])
(defrecord SiirryToimenpiteisiin [paikkauskohde-id])
;; Haut
(defrecord HaeKustannukset [])
(defrecord EnsimmainenHaku [tulos])
(defrecord KustannuksetHaettu [tulos])
(defrecord KustannuksetEiHaettu [])
(defrecord HaeKustannuksetKutsuLahetetty [])

(defn kasittele-haettu-tulos
  [tulos {otsikkokomponentti :otsikkokomponentti}]
  (let [kiinnostavat-tiedot (map #(kiinnostavat-tiedot-grid %)
                                 tulos)
        kokonaishintaiset-tiedot (filter #(= (:tyyppi %) "kokonaishintainen") kiinnostavat-tiedot)
        yksikkohintaiset-tiedot (filter #(= (:tyyppi %) "yksikkohintainen") kiinnostavat-tiedot)
        kokonaishintaiset-grid (mapcat (fn [[otsikko paikkaukset]]
                                         (cons (grid/otsikko otsikko {:otsikkokomponentit (otsikkokomponentti (:paikkauskohde-id (first paikkaukset)))}) paikkaukset))
                                       (group-by :nimi kokonaishintaiset-tiedot))
        yksikkohintaset-grid (mapcat (fn [[otsikko paikkaukset]]
                                       (cons (grid/otsikko otsikko {:otsikkokomponentit (otsikkokomponentti (:paikkauskohde-id (first paikkaukset)))}) paikkaukset))
                                     (group-by :nimi yksikkohintaiset-tiedot))]
    {:yksikkohintaiset-grid yksikkohintaset-grid
     :kokonaishintaiset-grid kokonaishintaiset-grid}))

(def valintojen-avaimet [:tr :aikavali :urakan-paikkauskohteet])

(extend-protocol tuck/Event
  PaivitaValinnat
  (process-event [{u :uudet} app]
    (let [uudet-valinnat (merge (:valinnat app)
                                (select-keys u valintojen-avaimet))
          haku (tuck/send-async! ->HaeKustannukset)]
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
  HaeKustannukset
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
            (tt/post! :hae-paikkausurakan-kustannukset
                      params
                      ;; Checkbox-group ja aluksen nimen kirjoitus generoisi
                      ;; liikaa requesteja ilman viivettä.
                      {:viive 1000
                       :tunniste :hae-paikkaukset-kustannukset-nakymaan
                       :lahetetty ->HaeKustannuksetKutsuLahetetty
                       :onnistui ->KustannuksetHaettu
                       :epaonnistui ->KustannuksetEiHaettu})
            (assoc :paikkauksien-haku-tulee-olemaan-kaynnissa? true)))
      app))
  HaeKustannuksetKutsuLahetetty
  (process-event [_ app]
    (assoc app :paikkauksien-haku-kaynnissa? true))
  Nakymaan
  (process-event [{otsikkokomponentti :otsikkokomponentti} app]
    (-> app
        (tt/post! :hae-paikkausurakan-kustannukset
                  {::paikkaus/urakka-id @nav/valittu-urakka-id}
                  {:onnistui ->EnsimmainenHaku
                   :epaonnistui ->KustannuksetEiHaettu})
        (assoc :nakymassa? true
               :otsikkokomponentti otsikkokomponentti
               :paikkauksien-haku-kaynnissa? true)))
  NakymastaPois
  (process-event [_ app]
    (assoc app :nakymassa? false))
  EnsimmainenHaku
  (process-event [{tulos :tulos} app]
    (println "TULOS: ")
    (cljs.pprint/pprint tulos)
    (yhteiset-tiedot/ensimmaisen-haun-kasittely {:paikkauskohde-idn-polku [:paikkauskohde-id]
                                                 :paikkauskohde-nimen-polku [:nimi]
                                                 :kasittele-haettu-tulos kasittele-haettu-tulos
                                                 :tulos tulos
                                                 :app app}))
  KustannuksetHaettu
  (process-event [{tulos :tulos} app]
    (let [naytettavat-tiedot (kasittele-haettu-tulos tulos app)]
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
  (process-event [{paikkauskohde-id :paikkauskohde-id} app]
    (reset! yhteiset-tiedot/paikkauskohde-id paikkauskohde-id)
    (swap! reitit/url-navigaatio assoc :kohdeluettelo-paikkaukset :toteumat)
    (assoc app :nakymassa? false)))