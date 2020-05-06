(ns harja.tiedot.urakka.paikkaukset-toteumat
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [harja.loki :refer [log]]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.navigaatio.reitit :as reitit]
            [harja.tiedot.urakka.paikkaukset-yhteinen :as yhteiset-tiedot]
            [harja.tyokalut.tuck :as tt]
            [harja.ui.kartta.asioiden-ulkoasu :as asioiden-ulkoasu]
            [harja.asiakas.kommunikaatio :as k]
            [harja.domain.paikkaus :as paikkaus]
            [harja.domain.tierekisteri :as tierekisteri])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(def tyhja-lomake {:kopio-itselle? true
                   :saate nil
                   :muut-vastaanottajat {}})

(def app (atom {:lomakedata tyhja-lomake}))

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


(defn ilmoita-virheesta-paikkaustiedoissa [paikkaus]
  (k/post! :ilmoita-virheesta-paikkaustiedoissa
           (merge paikkaus
                  {::paikkaus/urakka-id (:id @nav/valittu-urakka)})))

(defn merkitse-paikkaus-tarkistetuksi [paikkaus]
  (log "merkitse-paikkaus-tarkistetuksi, " (pr-str paikkaus))
  (k/post! :merkitse-paikkauskohde-tarkistetuksi
           (merge paikkaus
                  {::paikkaus/urakka-id (:id @nav/valittu-urakka)})))

(defn sahkopostiosoitteet-settiin [muut]
  (->> (vals muut)
       (filter (comp not :poistettu))
       (map :sahkoposti)
       (into #{})))

;; Muokkaukset
(defrecord Nakymaan [])
(defrecord NakymastaPois [])
(defrecord SiirryKustannuksiin [paikkauskohde-id])
;; Haut
(defrecord PaikkauksetHaettu [tulos])

;; Modal (sähköpostin lähetys paikkaustoteumassa olevasta virheestä)
(defrecord AvaaVirheModal [paikkaus])
(defrecord SuljeVirheModal [])
(defrecord VirheIlmoitusOnnistui [vastaus])
(defrecord MerkitseTarkistetuksiOnnistui [vastaus])
(defrecord PaivitaLomakedata [lomakedata])
(defrecord PaivitaMuutVastaanottajat [muut])

(extend-protocol tuck/Event
  Nakymaan
  (process-event [_ app]
    (assoc app :nakymassa? true))
  NakymastaPois
  (process-event [_ app]
    (swap! yhteiset-tiedot/tila assoc :ensimmainen-haku-tehty? false)
    (assoc app :nakymassa? false))
  PaikkauksetHaettu
  (process-event [{{paikkaukset :paikkaukset} :tulos} app]
    (let [naytettavat-tiedot (kasittele-haettu-tulos paikkaukset app)]
      (merge app naytettavat-tiedot)))
  SiirryKustannuksiin
  (process-event [{paikkauskohde-id :paikkauskohde-id} app]
    (swap! yhteiset-tiedot/tila update :valinnat (fn [valinnat]
                                                   (-> valinnat
                                                       (assoc :aikavali [nil nil]
                                                              :tyomenetelmat #{}
                                                              :tr nil)
                                                       (update :urakan-paikkauskohteet (fn [paikkauskohteet]
                                                                                         (map #(if (= paikkauskohde-id (:id %))
                                                                                                 %
                                                                                                 (assoc % :valittu? false))
                                                                                              paikkauskohteet))))))
    (swap! reitit/url-navigaatio assoc :kohdeluettelo-paikkaukset :kustannukset)
    (assoc app :nakymassa? false))
  AvaaVirheModal
  (process-event [{paikkaus :paikkaus} app]
    (assoc app :modalin-paikkaus paikkaus))
  SuljeVirheModal
  (process-event [_ app]
    (assoc app :modalin-paikkaus nil
               :lomakedata tyhja-lomake))
  VirheIlmoitusOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app :modalin-paikkaus nil
               :lomakedata tyhja-lomake))
  MerkitseTarkistetuksiOnnistui
  (process-event [{vastaus :vastaus} app]
    (log "MerkitseTarkistetuksi, vastaus " (pr-str vastaus))
    (assoc app :modalin-paikkaus nil))
  PaivitaLomakedata
  (process-event [{lomakedata :lomakedata} app]
    (assoc app :lomakedata lomakedata))
  PaivitaMuutVastaanottajat
  (process-event [{muut :muut} app]
    (assoc-in app [:lomakedata :muut-vastaanottajat] muut)))


