(ns harja.tiedot.kanavat.hallinta.kohteiden-luonti
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [cljs.core.async :as async]
            [clojure.set :as set]
            [harja.pvm :as pvm]
            [harja.id :refer [id-olemassa?]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.viesti :as viesti]
            [harja.tyokalut.tuck :as tt]

            [harja.domain.kanavat.kanava :as kanava]
            [harja.domain.kanavat.kanavan-kohde :as kohde]
            [harja.domain.muokkaustiedot :as m])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def tila (atom {:nakymassa? false
                 :kohteiden-haku-kaynnissa? false
                 :kohdelomake-auki? false
                 :kohderivit nil
                 :kanavat nil
                 :lomakkeen-tiedot nil}))

(defrecord Nakymassa? [nakymassa?])
(defrecord HaeKohteet [])
(defrecord KohteetHaettu [tulos])
(defrecord KohteetEiHaettu [virhe])
(defrecord AvaaKohdeLomake [])
(defrecord SuljeKohdeLomake [])
(defrecord ValitseKanava [kanava])
(defrecord LisaaKohteita [tiedot])
(defrecord TallennaKohteet [])
(defrecord KohteetTallennettu [tulos])
(defrecord KohteetEiTallennettu [virhe])

(defn kohderivit [tulos]
  (mapcat
    (fn [kanava-ja-kohteet]
      (map
        (fn [kohde]
          (-> kohde
              (assoc ::kanava/id (::kanava/id kanava-ja-kohteet))
              (assoc ::kanava/nimi (::kanava/nimi kanava-ja-kohteet))
              (assoc :rivin-teksti (str
                                     (when-let [nimi (::kanava/nimi kanava-ja-kohteet)]
                                       (str nimi ", "))
                                     (when-let [nimi (::kohde/nimi kohde)]
                                       (str nimi ", "))
                                     (when-let [tyyppi (kohde/tyyppi->str (::kohde/tyyppi kohde))]
                                       (str tyyppi))))))
        (::kanava/kohteet kanava-ja-kohteet)))
    tulos))

(defn kanavat [tulos]
  (map #(select-keys % #{::kanava/id ::kanava/nimi}) tulos))

(defn kohteet-voi-tallentaa? [kohteet]
  (and (:kanava kohteet)
       (not-empty (:kohteet kohteet))
       (every?
         (fn [kohde]
           (and (::kohde/tyyppi kohde)))
         (:kohteet kohteet))))

(defn muokattavat-kohteet [app]
  (get-in app [:lomakkeen-tiedot :kohteet]))

(defn tallennusparametrit [lomake]
  (let [kanava-id (get-in lomake [:kanava ::kanava/id])
        params (->> (:kohteet lomake)
                    (map #(assoc % ::kohde/kanava-id kanava-id))
                    (map #(set/rename-keys % {:id ::kohde/id
                                              :poistettu ::m/poistettu?}))
                    (map #(select-keys
                            %
                            [::kohde/nimi
                             ::kohde/id
                             ::kohde/kanava-id
                             ::kohde/tyyppi
                             ::m/poistettu?])))]
    params))

(extend-protocol tuck/Event
  Nakymassa?
  (process-event [{nakymassa? :nakymassa?} app]
    (assoc app :nakymassa? nakymassa?))

  HaeKohteet
  (process-event [_ app]
    (if-not (:kohteiden-haku-kaynnissa? app)
      (-> app
          (tt/get! :hae-kanavat-ja-kohteet
                   {:onnistui ->KohteetHaettu
                    :epaonnistui ->KohteetEiHaettu})
          (assoc :kohteiden-haku-kaynnissa? true))

      app))

  KohteetHaettu
  (process-event [{tulos :tulos} app]
    (-> app
        (assoc :kohderivit (kohderivit tulos))
        (assoc :kanavat (kanavat tulos))
        (assoc :kohteiden-haku-kaynnissa? false)))

  KohteetEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Kohteiden haku epäonnistui!" :danger)
    (-> app
        (assoc :kohteiden-haku-kaynnissa? false)))

  AvaaKohdeLomake
  (process-event [_ app]
    (assoc app :kohdelomake-auki? true))

  SuljeKohdeLomake
  (process-event [_ app]
    (-> app
        (assoc :kohdelomake-auki? false)
        (assoc :lomakkeen-tiedot nil)))

  ValitseKanava
  (process-event [{kanava :kanava} app]
    (-> app
        (assoc-in [:lomakkeen-tiedot :kanava] kanava)
        (assoc-in [:lomakkeen-tiedot :kohteet]
                  (filter
                    (fn [kohde]
                      (= (::kanava/id kohde)
                         (::kanava/id kanava)))
                    (:kohderivit app)))))

  LisaaKohteita
  (process-event [{tiedot :tiedot} app]
    (assoc-in app [:lomakkeen-tiedot :kohteet] tiedot))

  TallennaKohteet
  (process-event [_ {tiedot :lomakkeen-tiedot :as app}]
    (if-not (:kohteiden-haku-kaynnissa? app)
      (-> app
          (tt/post! :lisaa-kanavalle-kohteita
                    (tallennusparametrit tiedot)
                    {:onnistui ->KohteetTallennettu
                     :epaonnistui ->KohteetEiTallennettu})

          (assoc :kohteiden-tallennus-kaynnissa? true))

      app))

  KohteetTallennettu
  (process-event [{tulos :tulos} app]
    (log "Saatiin tulos" (pr-str tulos))
    (-> app
        (assoc :kohderivit (kohderivit tulos))
        (assoc :kanavat (kanavat tulos))
        (assoc :kohdelomake-auki? false)
        (assoc :lomakkeen-tiedot nil)
        (assoc :kohteiden-tallennus-kaynnissa? false)))

  KohteetEiTallennettu
  (process-event [_ app]
    (viesti/nayta! "Kohteiden tallennus epäonnistui!" :danger)
    (-> app
        (assoc :kohteiden-tallennus-kaynnissa? false))))