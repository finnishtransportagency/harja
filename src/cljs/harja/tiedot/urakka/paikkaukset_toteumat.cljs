(ns harja.tiedot.urakka.paikkaukset-toteumat
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [harja.pvm :as pvm]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.navigaatio.reitit :as reitit]
            [harja.tiedot.urakka.paikkaukset-yhteinen :as yhteiset-tiedot]
            [harja.tyokalut.tuck :as tt]
            [harja.ui.viesti :as viesti]
            [harja.ui.grid :as grid]
            [harja.domain.paikkaus :as paikkaus]
            [harja.domain.tierekisteri :as tierekisteri])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def app (atom {:paikkauksien-haku-kaynnissa? false
                :valinnat {:tyomenetelmat #{}
                           :aikavali (:aloitus-aikavali @yhteiset-tiedot/tila)}}))

(defn kiinnostavat-tiedot-grid [{tierekisteriosoite ::paikkaus/tierekisteriosoite paikkauskohde ::paikkaus/paikkauskohde
                                 :as paikkaus}]
  (let [sellaisenaan-naytettavat-arvot (select-keys paikkaus #{::paikkaus/tyomenetelma ::paikkaus/alkuaika ::paikkaus/loppuaika
                                                               ::paikkaus/massatyyppi ::paikkaus/leveys ::paikkaus/massamenekki
                                                               ::paikkaus/raekoko ::paikkaus/kuulamylly ::paikkaus/id
                                                               ::paikkaus/paikkauskohde})
        nimi (select-keys paikkauskohde #{::paikkaus/nimi})]
    (merge sellaisenaan-naytettavat-arvot tierekisteriosoite nimi)))

(defn kasittele-haettu-tulos
  [tulos {otsikkokomponentti :otsikkokomponentti}]
  (let [kiinnostavat-tiedot (map #(kiinnostavat-tiedot-grid %)
                                 tulos)
        paikkaukset-grid (mapcat (fn [[otsikko paikkaukset]]
                                   (cons (grid/otsikko otsikko {:otsikkokomponentit (otsikkokomponentti (get-in (first paikkaukset)
                                                                                                                [::paikkaus/paikkauskohde ::paikkaus/id]))})
                                         (sort-by (juxt ::tierekisteri/tie ::tierekisteri/aosa ::tierekisteri/aet ::tierekisteri/losa ::tierekisteri/let)
                                                  paikkaukset)))
                                 (group-by ::paikkaus/nimi kiinnostavat-tiedot))
        paikkauket-vetolaatikko (map #(select-keys % [::paikkaus/tienkohdat ::paikkaus/materiaalit ::paikkaus/id])
                                     tulos)]
    {:paikkaukset-grid paikkaukset-grid
     :paikkauket-vetolaatikko paikkauket-vetolaatikko}))

;; Muokkaukset
(defrecord PaikkausValittu [paikkauskohde valittu?])
(defrecord PaivitaValinnat [uudet])
(defrecord Nakymaan [otsikkokomponentti])
(defrecord NakymastaPois [])
(defrecord SiirryKustannuksiin [paikkauskohde-id])
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

(def valintojen-avaimet [:tr :aikavali :urakan-paikkauskohteet :tyomenetelmat])

(extend-protocol tuck/Event
  PaivitaValinnat
  (process-event [{u :uudet} app]
    (let [uudet-valinnat (merge (:valinnat app)
                                u)
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
  (process-event [{otsikkokomponentti :otsikkokomponentti} app]
    (-> app
        (tt/post! :hae-urakan-paikkauskohteet
                  {::paikkaus/urakka-id @nav/valittu-urakka-id
                   :aikavali (:aloitus-aikavali @yhteiset-tiedot/tila)}
                  {:onnistui ->EnsimmainenHaku
                   :epaonnistui ->PaikkauksetEiHaettu})
        (assoc :nakymassa? true
               :paikkauksien-haku-kaynnissa? true
               :otsikkokomponentti otsikkokomponentti)))
  NakymastaPois
  (process-event [_ app]
    (assoc app :nakymassa? false))
  EnsimmainenHaku
  (process-event [{tulos :tulos} app]
    (yhteiset-tiedot/ensimmaisen-haun-kasittely {:paikkauskohde-idn-polku [::paikkaus/paikkauskohde ::paikkaus/id]
                                                 :tuloksen-avain :paikkaukset
                                                 :kasittele-haettu-tulos kasittele-haettu-tulos
                                                 :tulos tulos
                                                 :app app}))
  PaikkauksetHaettu
  (process-event [{{paikkaukset :paikkaukset} :tulos} app]
    (let [naytettavat-tiedot (kasittele-haettu-tulos paikkaukset app)]
      (-> app
          (merge naytettavat-tiedot)
          (assoc :paikkauksien-haku-kaynnissa? false
                 :paikkauksien-haku-tulee-olemaan-kaynnissa? false))))
  PaikkauksetEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Liikennetapahtumien haku epäonnistui! " :danger)
    (assoc app
           :paikkauksien-haku-kaynnissa? false
           :paikkauksien-haku-tulee-olemaan-kaynnissa? false))
  SiirryKustannuksiin
  (process-event [{paikkauskohde-id :paikkauskohde-id} app]
    (swap! yhteiset-tiedot/tila assoc
           :paikkauskohde-id paikkauskohde-id
           :aloitus-aikavali [nil nil])
    (swap! reitit/url-navigaatio assoc :kohdeluettelo-paikkaukset :kustannukset)
    (assoc app :nakymassa? false)))