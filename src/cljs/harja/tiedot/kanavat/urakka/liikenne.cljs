(ns harja.tiedot.kanavat.urakka.liikenne
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tt]
            [cljs.core.async :as async]
            [harja.pvm :as pvm]
            [harja.id :refer [id-olemassa?]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.navigaatio :as nav]

            [harja.domain.urakka :as ur]
            [harja.domain.kanavat.kanavan-kohde :as kohde]
            [harja.domain.kanavat.kanava :as kanava]
            [harja.domain.kanavat.liikennetapahtuma :as lt]
            [harja.domain.kanavat.lt-alus :as lt-alus]
            [harja.domain.kanavat.lt-nippu :as lt-nippu])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def tila (atom {:nakymassa? false
                 :liikennetapahtumien-haku-kaynnissa? false
                 :valittu-liikennetapahtuma nil
                 :haetut-liikennetapahtumat nil
                 :tapahtumarivit nil}))

(def uusi-tapahtuma {})

(defrecord Nakymassa? [nakymassa?])
(defrecord HaeLiikennetapahtumat [])
(defrecord LiikennetapahtumatHaettu [tulos])
(defrecord LiikennetapahtumatEiHaettu [virhe])
(defrecord ValitseTapahtuma [tapahtuma])

(defn tapahtumarivit [tapahtuma]
  (let [yleistiedot
        (merge
          tapahtuma
          {:kohteen-nimi (str
                           (when-let [nimi (get-in tapahtuma [::lt/kohde ::kohde/kohteen-kanava ::kanava/nimi])]
                             (str nimi ", "))
                           (when-let [nimi (get-in tapahtuma [::lt/kohde ::kohde/nimi])]
                             (str nimi ", "))
                           (when-let [tyyppi (kohde/tyyppi->str (get-in tapahtuma [::lt/kohde ::kohde/tyyppi]))]
                             (str tyyppi)))})
        alustiedot
        (map
          (fn [alus]
            (merge
              yleistiedot
              alus
              {:suunta (::lt-alus/suunta alus)}))
          (::lt/alukset tapahtuma))

        nipputiedot
        (map
          (fn [nippu]
            (merge yleistiedot nippu {:suunta (::lt-nippu/suunta nippu)}))
          (::lt/niput tapahtuma))]

    (concat alustiedot nipputiedot)))

(extend-protocol tuck/Event
  Nakymassa?
  (process-event [{nakymassa? :nakymassa?} app]
    (assoc app :nakymassa? nakymassa?))

  HaeLiikennetapahtumat
  (process-event [_ app]
    (tt/post! :hae-liikennetapahtumat
              {::ur/id (:id @nav/valittu-urakka)}
              {:onnistui ->LiikennetapahtumatHaettu
               :epaonnistui ->LiikennetapahtumatEiHaettu})

    (log "HaeLiikennetapahtumat")
    (assoc app :liikennetapahtumien-haku-kaynnissa? true))

  LiikennetapahtumatHaettu
  (process-event [{tulos :tulos} app]
    (log (pr-str tulos))
    (-> app
        (assoc :liikennetapahtumien-haku-kaynnissa? false)
        (assoc :tapahtumarivit (mapcat tapahtumarivit tulos))))

  LiikennetapahtumatEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Liikennetapahtumien haku epäonnistui! " :danger)
    (assoc app :liikennetapahtumien-haku-kaynnissa? false))

  ValitseTapahtuma
  (process-event [{t :tapahtuma} app]
    (assoc app :valittu-liikennetapahtuma t)))
