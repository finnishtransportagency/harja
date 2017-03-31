(ns harja.tiedot.urakka.paallystyksen-maksuerat
  "Päällystysurakan maksuerät"
  (:require [reagent.core :refer [atom] :as r]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [tuck.core :as t]
            [harja.loki :refer [log]]
            [cljs.core.async :refer [<! >! chan]]
            [harja.tiedot.urakka.yllapito :as yllapito-tiedot]
            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

;; Tila
(def tila (atom {:valinnat {:urakka nil
                            :sopimus nil
                            :vuosi nil
                            :tienumero nil
                            :kohdenumero nil}
                 :maksuerat nil}))



(def valinnat
  (reaction
    {:urakka (:id @nav/valittu-urakka)
     :sopimus (first @u/valittu-sopimusnumero)
     :vuosi @u/valittu-urakan-vuosi
     :tienumero @yllapito-tiedot/tienumero
     :kohdenumero @yllapito-tiedot/kohdenumero}))

;; Tapahtumat

(defrecord PaivitaValinnat [valinnat])
(defrecord HaeMaksuerat [valinnat])
(defrecord MaksueratHaettu [tulokset])
(defrecord MaksueratTallennettu [vastaus])

;; Tapahtumien käsittely

(defn- maksuerarivi-grid-muotoon [maksuerarivi]
  (let [assoc-params (apply concat (map-indexed
                                     (fn [index teksti]
                                       [(keyword (str "maksuera" (inc index))) teksti])
                                     (:maksuerat maksuerarivi)))]
    (apply assoc maksuerarivi assoc-params)))

(defn- hae-maksuerat [{:keys [urakka sopimus vuosi] :as hakuparametrit}]
  (let [tulos! (t/send-async! ->MaksueratHaettu)]
    (go (let [maksuerat (<! (k/post! :hae-paallystyksen-maksuerat {:urakka-id urakka
                                                                   :sopimus-id sopimus
                                                                   :vuosi vuosi}))
              maksuerat-grid-muodossa (mapv maksuerarivi-grid-muotoon maksuerat)]
          (when-not (k/virhe? maksuerat)
            (tulos! maksuerat-grid-muodossa))))))

(extend-protocol t/Event

  PaivitaValinnat
  (process-event [{:keys [valinnat] :as e} tila]
    (hae-maksuerat valinnat)
    (update-in tila [:valinnat] merge valinnat))

  HaeMaksuerat
  (process-event [{:keys [valinnat] :as e} tila]
    (hae-maksuerat {:urakka (:urakka valinnat)
                    :sopimus (:sopimus valinnat)
                    :alkupvm (first (:sopimuskausi valinnat))
                    :loppupvm (second (:sopimuskausi valinnat))})
    tila)

  MaksueratHaettu
  (process-event [{:keys [tulokset] :as e} tila]
    (assoc-in tila [:maksuerat] tulokset)))