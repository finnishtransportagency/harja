(ns harja.tiedot.ilmoitukset.tietyoilmoitukset
  (:require [reagent.core :refer [atom]]
            [harja.pvm :as pvm]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.tiedot.urakat :as tiedot-urakat]
            [harja.loki :refer [log tarkkaile!]]
            [cljs.core.async :as async]
            [harja.atom :refer [paivita-periodisesti] :refer-macros [reaction<!]]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]]
            [tuck.core :as t]
            [cljs.pprint :refer [pprint]])
  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(defonce ulkoisetvalinnat
         (reaction {:voi-hakea? true
                    :hallintayksikko (:id @nav/valittu-hallintayksikko)
                    :urakka @nav/valittu-urakka
                    :valitun-urakan-hoitokaudet @tiedot-urakka/valitun-urakan-hoitokaudet
                    :urakoitsija (:id @nav/valittu-urakoitsija)
                    :urakkatyyppi (:arvo @nav/urakkatyyppi)
                    :hoitokausi @tiedot-urakka/valittu-hoitokausi}))

(defn- nil-hylkiva-concat [akku arvo]
  (if (or (nil? arvo) (nil? akku))
    nil
    (concat akku arvo)))

(defonce karttataso-ilmoitukset (atom false))

(def aikavalit [{:nimi "1 tunnin ajalta" :tunteja 1}
                {:nimi "12 tunnin ajalta" :tunteja 12}
                {:nimi "1 päivän ajalta" :tunteja 24}
                {:nimi "1 viikon ajalta" :tunteja 168}
                {:nimi "Vapaa aikaväli" :vapaa-aikavali true}])

(defonce ilmoitukset (atom {:ilmoitusnakymassa? false
                            :valittu-ilmoitus nil
                            :haku-kaynnissa? false
                            :ilmoitukset nil
                            :valinnat {:vakioaikavali (first aikavalit)
                                       :alkuaika (pvm/tuntia-sitten 1)
                                       :loppuaika (pvm/nyt)}}))

(defrecord AsetaValinnat [valinnat])
(defrecord YhdistaValinnat [ulkoisetvalinnat])
(defrecord HaeIlmoitukset [])
(defrecord IlmoituksetHaettu [tulokset])
(defrecord ValitseIlmoitus [ilmoitus])
(defrecord PoistaIlmoitusValinta [])
(defrecord IlmoitustaMuokattu [ilmoitus])
(defrecord HaeKayttajanUrakat [hallintayksikot])
(defrecord KayttajanUrakatHaettu [urakat])

(defn- hae-ilmoitukset [{valinnat :valinnat haku :ilmoitushaku-id :as app}]
  (-> app
      (assoc :ilmoitushaku-id (.setTimeout js/window (t/send-async! ->HaeIlmoitukset) 1000))))

(extend-protocol t/Event
  AsetaValinnat
  (process-event [{valinnat :valinnat} app]
    (hae-ilmoitukset
      (assoc app :valinnat valinnat)))

  YhdistaValinnat
  (process-event [{ulkoisetvalinnat :ulkoisetvalinnat :as e} app]
    (let [uudet-valinnat (merge ulkoisetvalinnat (:valinnat app))
          app (assoc app :valinnat uudet-valinnat)]
      (hae-ilmoitukset app)))

  HaeIlmoitukset
  (process-event [_ {valinnat :valinnat :as app}]
    (let [tulos! (t/send-async! ->IlmoituksetHaettu)]
      (go
        (tulos!
          {:ilmoitukset (async/<! (k/post! :hae-tietyoilmoitukset (select-keys valinnat [:alkuaika :loppuaika])))})))
    (assoc app :ilmoitukset nil))

  IlmoituksetHaettu
  (process-event [vastaus {valittu :valittu-ilmoitus :as app}]
    (let [ilmoitukset (:ilmoitukset (:tulokset vastaus))]
      (assoc app :ilmoitukset ilmoitukset)))

  ValitseIlmoitus
  (process-event [{ilmoitus :ilmoitus} app]
    (assoc app :valittu-ilmoitus ilmoitus))

  PoistaIlmoitusValinta
  (process-event [_ app]
    (assoc app :valittu-ilmoitus nil))

  IlmoitustaMuokattu
  (process-event [ilmoitus app]
    (log "IlmoitustaMuokattu: saatiin" (keys ilmoitus) "ja" (keys app))
    app)

  HaeKayttajanUrakat
  (process-event [{hallintayksikot :hallintayksikot} app]
    (let [tulos! (t/send-async! ->KayttajanUrakatHaettu)]
      (when hallintayksikot
        (go (tulos! (async/<!
                      (async/reduce nil-hylkiva-concat []
                                    (async/merge
                                      (mapv tiedot-urakat/hae-hallintayksikon-urakat hallintayksikot))))))))
    (assoc app :kayttajan-urakat nil))

  KayttajanUrakatHaettu
  (process-event [{urakat :urakat} app]
    (let [urakka (or @nav/valittu-urakka (first urakat))]
      (assoc app :kayttajan-urakat urakat
                 :valinnat (assoc (:valinnat app) :urakka urakka)))))
