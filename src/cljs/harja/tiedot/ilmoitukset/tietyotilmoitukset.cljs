(ns harja.tiedot.ilmoitukset.tietyotilmoitukset
  (:require [reagent.core :refer [atom]]
            [harja.pvm :as pvm]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.tiedot.urakat :as tiedot-urakat]
            [harja.tiedot.hallintayksikot :as hy]
            [harja.loki :refer [log tarkkaile!]]
            [cljs.core.async :as async]
            [harja.atom :refer [paivita-periodisesti] :refer-macros [reaction<!]]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]]
            [tuck.core :as t]
            [clojure.string :as str]
            [reagent.core :as r])
  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

;; Valinnat jotka riippuvat ulkoisista atomeista
(defonce ulkoisetvalinnat
  (reaction {:voi-hakea? true
             :hallintayksikko (:id @nav/valittu-hallintayksikko)
             :urakka (:id @nav/valittu-urakka)
             :valitun-urakan-hoitokaudet @tiedot-urakka/valitun-urakan-hoitokaudet
             :urakoitsija (:id @nav/valittu-urakoitsija)
             :urakkatyyppi (:arvo @nav/urakkatyyppi)
             :hoitokausi @tiedot-urakka/valittu-hoitokausi}))


(defonce kayttajan-urakat
  (reaction<! [hallintayksikot @hy/hallintayksikot]
              (log "kayttajan-urakat kaynnistetty")
              (when hallintayksikot ;; (and nakyvissa? hallintayksikot)
                (go
                  (async/<!
                   (async/reduce conj []
                                 (async/merge (mapv tiedot-urakat/hae-hallintayksikon-urakat
                                                    hallintayksikot))))))))

(defonce karttataso-ilmoitukset (atom false))

(defonce ilmoitukset (atom {:ilmoitusnakymassa? false
                            :valittu-ilmoitus nil
                            :haku-kaynnissa? false
                            :ilmoitukset nil ;; haetut ilmoitukset
                            :valinnat {:alkuaika (pvm/tuntia-sitten 1)
                                       :loppuaika (pvm/nyt)}}))

;; (tarkkaile! "tti/ilmoitukset" ilmoitukset)

;; Vaihtaa valinnat
(defrecord AsetaValinnat [valinnat])

;; Kun valintojen reaktio muuttuu
(defrecord YhdistaValinnat [ulkoisetvalinnat])

(defrecord HaeIlmoitukset []) ;; laukaise ilmoitushaku
(defrecord IlmoitusHaku [tulokset]) ;; Ilmoitusten palvelinhaun tulokset


;; Valitsee ilmoituksen tarkasteltavaksi
(defrecord ValitseIlmoitus [ilmoitus])

;; Palvelimelta palautuneet ilmoituksen tiedot
(defrecord IlmoituksenTiedot [ilmoitus])

(defrecord PoistaIlmoitusValinta [])

(defrecord IlmoitustaMuokattu [ilmoitus])

(defn- hae-ilmoitukset [{valinnat :valinnat haku :ilmoitushaku-id :as app}]
  (log "---> hae-ilmoitukset, appin :valinnat = " (pr-str valinnat))
  (-> app
      (assoc :ilmoitushaku-id (.setTimeout js/window
                                           (t/send-async! ->HaeIlmoitukset)
                                           1000))))

(extend-protocol t/Event
  AsetaValinnat
  (process-event [{valinnat :valinnat} app]
    (log "---> AsetaValinnat, valinnat" (pr-str valinnat))
    (hae-ilmoitukset
       (assoc app :valinnat valinnat)))

  YhdistaValinnat
  (process-event [{ulkoisetvalinnat :ulkoisetvalinnat :as e} app]
    (let [uudet-valinnat (merge ulkoisetvalinnat (:valinnat app))
          app (assoc app :valinnat uudet-valinnat)]
      (log "--->YhdistaValinnat, kutsutaan hae-ilmoitukset")
      (hae-ilmoitukset app)))

  HaeIlmoitukset
  (process-event [_ {valinnat :valinnat :as app}]
    (let [tulos! (t/send-async! ->IlmoitusHaku)]
      (log "---> HaeIlmoitukset: valinnat:" (pr-str valinnat) ", app keys:" (pr-str (keys  app)))
      (go
        (tulos!
         {:ilmoitukset (async/<! (k/post! :hae-tietyoilmoitukset (select-keys valinnat [:alkuaika :loppuaika] )))})))
    (assoc app :ilmoitukset nil))

  IlmoitusHaku
  (process-event [vastaus {valittu :valittu-ilmoitus :as app}]
    (log "----> IlmoitusHaku" (pr-str vastaus))
    (let [ilmoitukset (:ilmoitukset (:tulokset vastaus))]
      (assoc app :ilmoitukset ilmoitukset)))

  ValitseIlmoitus
  (process-event [{ilmoitus :ilmoitus} app]
    #_(let [tulos! (t/send-async! ->IlmoituksenTiedot)]
      (go
        (tulos! (async/<! (k/post! :hae-ilmoitukset (:id ilmoitus))))))
    #_(assoc app :ilmoituksen-haku-kaynnissa? true)
    (assoc app :valittu-ilmoitus ilmoitus))

  PoistaIlmoitusValinta
  (process-event [_ app]
    (assoc app :valittu-ilmoitus nil))

  IlmoitustaMuokattu
  (process-event [ilmoitus app]
    (log "IlmoitustaMuokattu: saatiin" (keys ilmoitus) "ja" (keys app))
    app)
  )
