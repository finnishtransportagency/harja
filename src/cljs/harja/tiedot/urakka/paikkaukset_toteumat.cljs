(ns harja.tiedot.urakka.paikkaukset-toteumat
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [harja.pvm :as pvm]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.navigaatio.reitit :as reitit]
            [harja.tiedot.urakka.paikkaukset-yhteinen :as yhteiset-tiedot]
            [harja.tyokalut.tuck :as tt]
            [harja.ui.viesti :as viesti]
            [harja.ui.kartta.asioiden-ulkoasu :as asioiden-ulkoasu]
            [harja.domain.paikkaus :as paikkaus]
            [harja.domain.tierekisteri :as tierekisteri])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(def app (atom {:paikkauksien-haku-kaynnissa? false
                :valinnat {:aikavali (:aloitus-aikavali @yhteiset-tiedot/tila)}}))

(def taso-nakyvissa? (atom false))

(defn suirun-pituus
  [teiden-pituudet tierekisteriosoite]
  (tierekisteri/laske-tien-pituus (get teiden-pituudet (::tierekisteri/tie tierekisteriosoite))
                                  {:tr-alkuosa (::tierekisteri/aosa tierekisteriosoite)
                                   :tr-alkuetaisyys (::tierekisteri/aet tierekisteriosoite)
                                   :tr-loppuosa (::tierekisteri/losa tierekisteriosoite)
                                   :tr-loppuetaisyys (::tierekisteri/let tierekisteriosoite)}))

(defn kiinnostavat-tiedot-grid [{tierekisteriosoite ::paikkaus/tierekisteriosoite paikkauskohde ::paikkaus/paikkauskohde
                                 :as paikkaus} teiden-pituudet]
  (let [sellaisenaan-naytettavat-arvot (select-keys paikkaus #{::paikkaus/tyomenetelma ::paikkaus/alkuaika ::paikkaus/loppuaika
                                                               ::paikkaus/massatyyppi ::paikkaus/leveys ::paikkaus/massamenekki
                                                               ::paikkaus/raekoko ::paikkaus/kuulamylly ::paikkaus/id
                                                               ::paikkaus/paikkauskohde ::paikkaus/sijainti})
        suirun-pituus (suirun-pituus teiden-pituudet tierekisteriosoite)
        suirun-tiedot {:suirun-pituus suirun-pituus
                       :suirun-pinta-ala (* suirun-pituus (::paikkaus/leveys paikkaus))}
        sijainti {::paikkaus/sijainti (-> (::paikkaus/sijainti paikkaus)
                                          (assoc :type :moniviiva
                                                 :viivat asioiden-ulkoasu/paikkaukset))}
        nimi (select-keys paikkauskohde #{::paikkaus/nimi})]
    (merge sellaisenaan-naytettavat-arvot tierekisteriosoite nimi sijainti suirun-tiedot)))

(defn kiinnostavat-tiedot-vetolaatikko
  [paikkaus teiden-pituudet]
  (let [sellaisenaan-naytettavat-arvot (select-keys paikkaus [::paikkaus/tienkohdat ::paikkaus/materiaalit ::paikkaus/id])]
    sellaisenaan-naytettavat-arvot))

(defn kasittele-haettu-tulos
  [tulos {teiden-pituudet :teiden-pituudet}]
  (let [kiinnostavat-tiedot (map #(kiinnostavat-tiedot-grid % teiden-pituudet)
                                 tulos)
        paikkauket-vetolaatikko (map #(kiinnostavat-tiedot-vetolaatikko % teiden-pituudet)
                                     tulos)]
    {:paikkaukset-grid kiinnostavat-tiedot
     :haettu-uudet-paikkaukset? true
     :paikkauket-vetolaatikko paikkauket-vetolaatikko}))

(def toteumat-kartalla
  (reaction (let [paikkaukset (remove #(= (type %) harja.ui.grid.protokollat/Otsikko)
                                      (:paikkaukset-grid @app))
                  paikkauksien-kohdat (:paikkauket-vetolaatikko @app)
                  infopaneelin-tiedot-fn #(merge (select-keys % #{::tierekisteri/tie ::tierekisteri/aosa ::tierekisteri/aet
                                                                  ::tierekisteri/losa ::tierekisteri/let ::paikkaus/alkuaika
                                                                  ::paikkaus/loppuaika ::paikkaus/massatyyppi ::paikkaus/leveys
                                                                  ::paikkaus/massamenekki ::paikkaus/raekoko ::paikkaus/kuulamylly})
                                                 {::paikkaus/nimi (get-in % [::paikkaus/paikkauskohde ::paikkaus/nimi])}
                                                 (some (fn [paikkaus-kohta]
                                                         (when (= (::paikkaus/id paikkaus-kohta) (::paikkaus/id %))
                                                           (select-keys (first (::paikkaus/tienkohdat paikkaus-kohta)) #{::paikkaus/ajorata ::paikkaus/ajourat
                                                                                                                         ::paikkaus/ajouravalit ::paikkaus/reunat})))
                                                       paikkauksien-kohdat))]
              (when (and (not-empty paikkaukset) @taso-nakyvissa?)
                (with-meta (mapv (fn [paikkaus]
                                   {:alue (::paikkaus/sijainti paikkaus)
                                    :tyyppi-kartalla :paikkaukset-toteumat
                                    :stroke {:width asioiden-ulkoasu/+normaali-leveys+}
                                    :infopaneelin-tiedot (infopaneelin-tiedot-fn paikkaus)})
                                 paikkaukset)
                           {:selitteet [{:vari (map :color asioiden-ulkoasu/paikkaukset)
                                         :teksti "Paikkaukset"}]})))))

;; Muokkaukset
(defrecord PaikkausValittu [paikkauskohde valittu?])
(defrecord PaivitaValinnat [uudet])
(defrecord Nakymaan [])
(defrecord NakymastaPois [])
(defrecord SiirryKustannuksiin [paikkauskohde-id])
(defrecord LisaaOtsikotGridiin [otsikon-lisays-fn])
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
  (process-event [_ app]
    (-> app
        (tt/post! :hae-urakan-paikkauskohteet
                  {::paikkaus/urakka-id @nav/valittu-urakka-id
                   :aikavali (:aloitus-aikavali @yhteiset-tiedot/tila)}
                  {:onnistui ->EnsimmainenHaku
                   :epaonnistui ->PaikkauksetEiHaettu})
        (assoc :nakymassa? true
               :paikkauksien-haku-kaynnissa? true)))
  NakymastaPois
  (process-event [_ app]
    (assoc app :nakymassa? false))
  EnsimmainenHaku
  (process-event [{tulos :tulos} app]
    (let [app (assoc app :teiden-pituudet (:teiden-pituudet tulos))]
      (yhteiset-tiedot/ensimmaisen-haun-kasittely {:paikkauskohde-idn-polku [::paikkaus/paikkauskohde ::paikkaus/id]
                                                   :tuloksen-avain :paikkaukset
                                                   :kasittele-haettu-tulos kasittele-haettu-tulos
                                                   :tulos tulos
                                                   :app app})))
  PaikkauksetHaettu
  (process-event [{{paikkaukset :paikkaukset} :tulos} app]
    (let [naytettavat-tiedot (kasittele-haettu-tulos paikkaukset app)]
      (-> app
          (merge naytettavat-tiedot)
          (assoc :paikkauksien-haku-kaynnissa? false
                 :paikkauksien-haku-tulee-olemaan-kaynnissa? false))))
  PaikkauksetEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Paikkauksien haku epäonnistui! " :danger)
    (assoc app
           :paikkauksien-haku-kaynnissa? false
           :paikkauksien-haku-tulee-olemaan-kaynnissa? false))
  SiirryKustannuksiin
  (process-event [{paikkauskohde-id :paikkauskohde-id} app]
    (swap! yhteiset-tiedot/tila assoc
           :paikkauskohde-id paikkauskohde-id
           :aloitus-aikavali [nil nil])
    (swap! reitit/url-navigaatio assoc :kohdeluettelo-paikkaukset :kustannukset)
    (assoc app :nakymassa? false))
  LisaaOtsikotGridiin
  (process-event [{otsikon-lisays-fn :otsikon-lisays-fn} app]
    (assoc app
           :paikkaukset-grid (mapcat otsikon-lisays-fn
                                     (group-by ::paikkaus/nimi (:paikkaukset-grid app)))
           :haettu-uudet-paikkaukset? false)))