(ns harja.tiedot.urakka.paikkaukset-kustannukset
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [harja.pvm :as pvm]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.navigaatio.reitit :as reitit]
            [harja.tiedot.urakka.paikkaukset-yhteinen :as yhteiset-tiedot]
            [harja.tyokalut.tuck :as tt]
            [harja.ui.grid :as grid]
            [harja.domain.paikkaus :as paikkaus])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def app (atom nil))

(defn kiinnostavat-tiedot-grid [paikkaus]
  (select-keys paikkaus #{:selite :yksikko :yksikkohinta :paikkaustoteuma-id
                          :maara :hinta :tyyppi :paikkauskohde-id :nimi
                          :kirjattu}))

;; Muokkaukset
(defrecord Nakymaan [otsikkokomponentti])
(defrecord NakymastaPois [])
(defrecord SiirryToimenpiteisiin [paikkauskohde-id])
;; Haut
(defrecord KustannuksetHaettu [tulos])

(defn- lisaa-otsikko-ja-yhteenveto
  [otsikko otsikkokomponentti paikkaukset skeema]
  (let [otsikkorivi (grid/otsikko otsikko {:otsikkokomponentit (otsikkokomponentti (:paikkauskohde-id (first paikkaukset)))})
        yhteenveto-id (gensym "yhteenveto")
        yhteenvetorivi (assoc skeema :paikkaustoteuma-id yhteenveto-id)]
    (cons otsikkorivi (conj paikkaukset yhteenvetorivi))))

(defn kasittele-haettu-tulos
  [tulos app]
  ;; TODO, haetaan paikkauskohteiden kokonaishintaiset kustannukset työmenetelmittäin
  {:paikkauksien-kokonaishinta-tyomenetelmittain-grid []})


(extend-protocol tuck/Event
  Nakymaan
  (process-event [{otsikkokomponentti :otsikkokomponentti} app]
    (assoc app
           :nakymassa? true
           :otsikkokomponentti otsikkokomponentti))
  NakymastaPois
  (process-event [_ app]
    (swap! yhteiset-tiedot/tila assoc :ensimmainen-haku-tehty? false)
    (assoc app :nakymassa? false))
  KustannuksetHaettu
  (process-event [{{kustannukset :kustannukset} :tulos} app]
    (let [naytettavat-tiedot (kasittele-haettu-tulos kustannukset app)]
      (merge app naytettavat-tiedot)))
  SiirryToimenpiteisiin
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
    (swap! reitit/url-navigaatio assoc :kohdeluettelo-paikkaukset :toteumat)
    (assoc app :nakymassa? false)))
