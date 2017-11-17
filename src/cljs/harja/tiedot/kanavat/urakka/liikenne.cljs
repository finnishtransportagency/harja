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
            [harja.domain.sopimus :as sop]
            [harja.domain.kanavat.kanavan-kohde :as kohde]
            [harja.domain.kanavat.kanava :as kanava]
            [harja.domain.kanavat.liikennetapahtuma :as lt]
            [harja.domain.kanavat.lt-alus :as lt-alus]
            [harja.domain.kanavat.lt-nippu :as lt-nippu]
            [harja.tiedot.urakka :as u]
            [reagent.core :as r])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(def tila (atom {:nakymassa? false
                 :liikennetapahtumien-haku-kaynnissa? false
                 :kohteiden-haku-kaynnissa? false
                 :valittu-liikennetapahtuma nil
                 :tapahtumarivit nil
                 :urakan-kohteet nil
                 :valinnat {::ur/id nil
                            ::sop/id nil
                            :aikavali nil
                            ::lt/kohde nil
                            ::lt-alus/suunta nil
                            ::lt/toimenpide nil
                            ::lt-alus/laji nil
                            :niput? false}}))

(def uusi-tapahtuma {})

(def valinnat
  (reaction
    (when (:nakymassa? @tila)
      {::ur/id (:id @nav/valittu-urakka)
       :aikavali @u/valittu-aikavali
       ::sop/id (first @u/valittu-sopimusnumero)})))

(def valintojen-avaimet
  [::ur/id ::sop/id :aikavali ::lt/kohde ::lt-alus/suunta ::lt/toimenpide ::lt-alus/laji :niput?])

(defrecord Nakymassa? [nakymassa?])
(defrecord HaeLiikennetapahtumat [])
(defrecord LiikennetapahtumatHaettu [tulos])
(defrecord LiikennetapahtumatEiHaettu [virhe])
(defrecord ValitseTapahtuma [tapahtuma])
(defrecord PaivitaValinnat [uudet])
(defrecord HaeKohteet [])
(defrecord KohteetHaettu [tulos])
(defrecord KohteetEiHaettu [virhe])

(defn valinta-wrap [e! app polku]
  (r/wrap (get-in app [:valinnat polku])
          (fn [u]
            (e! (->PaivitaValinnat {polku u})))))

(defn hakuparametrit [app]
  ;; Ei nil arvoja
  (when (and (::sop/id (:valinnat app))
             (::ur/id (:valinnat app)))
    (into {} (filter val (:valinnat app)))))

(defn palvelumuoto->str [rivi]
  (let [pm (::lt/palvelumuoto rivi)]
    (if (= :itse pm)
      (str (lt/palvelumuoto->str pm) " (" (::lt/palvelumuoto-lkm rivi) " kpl)")
      (lt/palvelumuoto->str pm))))

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

    (if (and (empty? alustiedot) (empty? nipputiedot))
      ;; Alustiedot ja nipputiedot ovat tyhjiä,
      ;; jos rivi on vain itsepalveluiden kirjaamista.
      [yleistiedot]
      (concat alustiedot nipputiedot))))

(extend-protocol tuck/Event
  Nakymassa?
  (process-event [{nakymassa? :nakymassa?} app]
    (assoc app :nakymassa? nakymassa?))

  HaeLiikennetapahtumat
  (process-event [_ app]
    (if-not (:liikennetapahtumien-haku-kaynnissa? app)
      (if-let [params (hakuparametrit app)]
        (-> app
            (tt/post! :hae-liikennetapahtumat
                      params
                      {:onnistui ->LiikennetapahtumatHaettu
                       :epaonnistui ->LiikennetapahtumatEiHaettu})
            (assoc :liikennetapahtumien-haku-kaynnissa? true))

        app)

      app))

  LiikennetapahtumatHaettu
  (process-event [{tulos :tulos} app]
    (-> app
        (assoc :liikennetapahtumien-haku-kaynnissa? false)
        (assoc :tapahtumarivit (mapcat tapahtumarivit tulos))))

  LiikennetapahtumatEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Liikennetapahtumien haku epäonnistui! " :danger)
    (assoc app :liikennetapahtumien-haku-kaynnissa? false))

  ValitseTapahtuma
  (process-event [{t :tapahtuma} app]
    (assoc app :valittu-liikennetapahtuma t))

  PaivitaValinnat
  (process-event [{u :uudet} app]
    (let [uudet-valinnat (merge (:valinnat app)
                                (select-keys u valintojen-avaimet))
          haku (tuck/send-async! ->HaeLiikennetapahtumat)]
      (go (haku uudet-valinnat))
      (assoc app :valinnat uudet-valinnat)))

  HaeKohteet
  (process-event [_ app]
    (if-not (:kohteiden-haku-kaynnissa? app)
      (-> app
          (tt/post! :hae-urakan-kohteet
                    {::ur/id (:id @nav/valittu-urakka)}
                    {:onnistui ->KohteetHaettu
                     :epaonnistui ->KohteetEiHaettu})
          (assoc :kohteiden-haku-kaynnissa? true))

      app))

  KohteetHaettu
  (process-event [{tulos :tulos} app]
    (-> app
        (assoc :kohteiden-haku-kaynnissa? false)
        (assoc :urakan-kohteet tulos)))

  KohteetEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Virhe kohteiden haussa!" :danger)
    (-> app
        (assoc :kohteiden-haku-kaynnissa? false))))
