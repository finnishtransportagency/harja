(ns harja.tiedot.tilannekuva.nykytilanne
  (:require [reagent.core :refer [atom]]

            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log]]
            [cljs.core.async :refer [<!]]
            [harja.atom :refer-macros [reaction<!] :refer [paivita-periodisesti]]
            [harja.tiedot.navigaatio :as nav]
            [harja.pvm :as pvm]
            [harja.asiakas.tapahtumat :as tapahtumat]
            [cljs-time.core :as t]
            [clojure.set :refer [rename-keys]]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]])

  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

;; Mitä haetaan?
(defonce hae-toimenpidepyynnot? (atom true))
(defonce hae-kyselyt? (atom true))
(defonce hae-tiedoitukset? (atom true))
(defonce hae-havainnot? (atom true))
(defonce hae-tyokoneet? (atom true))

;; Millä ehdoilla haetaan?
(defonce livesuodattimen-asetukset (atom "0-4h"))

(defonce nakymassa? (atom false))
(defonce karttataso-nykytilanne (atom false))
(def haetut-asiat (atom nil))

(def nykytilanteen-asiat-kartalla
  (reaction
    @haetut-asiat
    (when @karttataso-nykytilanne
      (kartalla-esitettavaan-muotoon @haetut-asiat))))

(defn kasaa-parametrit []
  {:hallintayksikko @nav/valittu-hallintayksikko-id
   :urakka          (:id @nav/valittu-urakka)
   :alue            @nav/kartalla-nakyva-alue
   :alku            (pvm/nyt)
   :loppu           (t/plus (pvm/nyt) (case @livesuodattimen-asetukset
                                        "0-4h" (t/hours 4)
                                        "0-12h" (t/hours 12)
                                        "0-24h" (t/hours 24)))})

(defn hae-asiat []
  (go
    (let [yhdista (fn [& tulokset]
                    (apply (comp vec concat) (remove k/virhe? tulokset)))
          yhteiset-parametrit (kasaa-parametrit)
          tulos (yhdista
                  (when @hae-tyokoneet?
                    (mapv
                      #(assoc % :tyyppi-kartalla :tyokone)
                      (<! (k/post! :hae-tyokoneseurantatiedot yhteiset-parametrit))))
                  (when (or @hae-toimenpidepyynnot? @hae-tiedoitukset? @hae-kyselyt?)
                    (mapv
                      #(assoc % :tyyppi-kartalla (get % :ilmoitustyyppi))
                      (<! (k/post! :hae-ilmoitukset (assoc
                                                      yhteiset-parametrit
                                                      :aikavali [(:alku yhteiset-parametrit)
                                                                 (:loppu yhteiset-parametrit)]
                                                      :tilat #{:avoimet}
                                                      :tyypit (remove nil? [(when @hae-toimenpidepyynnot? :toimenpidepyynto)
                                                                            (when @hae-kyselyt? :kysely)
                                                                            (when @hae-tiedoitukset? :tiedoitus)]))))))
                  (when @hae-havainnot?
                    (mapv
                      #(assoc % :tyyppi-kartalla :havainto)
                      (<! (k/post! :hae-urakan-havainnot (rename-keys
                                                           yhteiset-parametrit
                                                           {:urakka :urakka-id}))))))]
      (reset! haetut-asiat tulos)
      (tapahtumat/julkaise! {:aihe      :uusi-tyokonedata
                             :tyokoneet tulos}))))

(def +bufferi+ 500)

(def asioiden-haku (reaction<!
                     [nakymassa? @nakymassa?
                      _ @hae-toimenpidepyynnot?
                      _ @hae-kyselyt?
                      _ @hae-tiedoitukset?
                      _ @hae-tyokoneet?
                      _ @hae-havainnot?
                      _ @livesuodattimen-asetukset]
                     {:odota +bufferi+}
                     (when nakymassa? (hae-asiat))))