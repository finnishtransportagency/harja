(ns harja.tiedot.urakka.paallystyksen-maksuerat
  "Päällystysurakan maksuerät"
  (:require [reagent.core :refer [atom] :as r]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [tuck.core :as t]
            [harja.loki :refer [log]]
            [cljs.core.async :refer [<! >! chan]]
            [harja.tiedot.urakka.yllapito :as yllapito-tiedot]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.viesti :as viesti]
            [harja.asiakas.tapahtumat :as tapahtumat])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

;; Tila
(def tila (atom {:valinnat {:urakka-id nil
                            :sopimus-id nil
                            :vuosi nil
                            :tienumero nil
                            :kohdenumero nil}
                 :maksuerat nil}))



(def valinnat
  (reaction
    {:urakka-id (:id @nav/valittu-urakka)
     :sopimus-id (first @u/valittu-sopimusnumero)
     :vuosi @u/valittu-urakan-vuosi
     :tienumero @yllapito-tiedot/tienumero
     :kohdenumero @yllapito-tiedot/kohdenumero}))

;; Tapahtumat

(defrecord PaivitaValinnat [valinnat])
(defrecord HaeMaksuerat [valinnat])
(defrecord MaksueratHaettu [vastaus])
(defrecord MaksueratTallennettu [vastaus])
(defrecord TallennaMaksuerat [parametrit])

;; Tapahtumien käsittely

(defn maksuerarivi-grid-muotoon
  "Ottaa mapin, jossa yksittäiset maksuerät löytyvät :maksuerat avaimesta
   Palauttaa mapin, jossa jokainen yksittäinen maksuerä löytyy omasta avaimesta."
  [maksuerarivi]
  (let [assoc-params (apply concat (map-indexed
                                     (fn [index teksti]
                                       [(keyword (str "maksuera" (inc index))) teksti])
                                     (:maksuerat maksuerarivi)))]
    (apply assoc
           (dissoc maksuerarivi :maksuerat)
           assoc-params)))

(defn maksuerarivi-tallennusmuotoon
  "Ottaa mapin, jossa yksittäinen maksuerä on oman avaimen takana.
   Palauttaa mapin, jossa yksittäiset maksuerät löytyvät mapissa :maksuerat avaimesta"
  [maksuerarivi]
  (let [maksueranumerot (take-while #(some? (maksuerarivi (keyword (str "maksuera" %))))
                                    (map inc (range)))
        maksuera-avaimet (map #(keyword (str "maksuera" %)) maksueranumerot)]
    (assoc
      (apply dissoc maksuerarivi maksuera-avaimet)
      :maksuerat
      (mapv maksuerarivi maksuera-avaimet))))

(defn- hae-maksuerat [{:keys [urakka-id sopimus-id vuosi] :as hakuparametrit}]
  (let [tulos! (t/send-async! ->MaksueratHaettu)]
    (go (let [maksuerat (<! (k/post! :hae-paallystyksen-maksuerat {:urakka-id urakka-id
                                                                   :sopimus-id sopimus-id
                                                                   :vuosi vuosi}))
              maksuerat-grid-muodossa (mapv maksuerarivi-grid-muotoon maksuerat)]
          (when-not (k/virhe? maksuerat)
            (tulos! maksuerat-grid-muodossa))))))

(defn- tallenna-maksuerat [{:keys [urakka-id sopimus-id vuosi maksuerat]}]
  (let [tulos! (t/send-async! ->MaksueratTallennettu)]
    (go (let [vastaus (<! (k/post! :tallenna-paallystyksen-maksuerat {:urakka-id urakka-id
                                                                      :sopimus-id sopimus-id
                                                                      :vuosi vuosi
                                                                      :maksuerat maksuerat}))]
          (if (k/virhe? vastaus)
            (viesti/nayta! "Tallentaminen epäonnistui" :warning viesti/viestin-nayttoaika-lyhyt)
            (tulos! (mapv maksuerarivi-grid-muotoon vastaus)))))))

(extend-protocol t/Event

  PaivitaValinnat
  (process-event [{:keys [valinnat] :as e} tila]
    (hae-maksuerat valinnat)
    (update-in tila [:valinnat] merge valinnat))

  HaeMaksuerat
  (process-event [{:keys [valinnat] :as e} tila]
    (hae-maksuerat {:urakka-id (:urakka-id valinnat)
                    :sopimus-id (:sopimus-id valinnat)
                    :alkupvm (first (:sopimuskausi valinnat))
                    :loppupvm (second (:sopimuskausi valinnat))})
    tila)

  MaksueratHaettu
  (process-event [{:keys [vastaus] :as e} tila]
    (assoc-in tila [:maksuerat] vastaus))

  TallennaMaksuerat
  (process-event [{:keys [parametrit] :as e} tila]
    (tallenna-maksuerat {:urakka-id (:urakka-id parametrit)
                         :sopimus-id (:sopimus-id parametrit)
                         :vuosi (:vuosi parametrit)
                         :maksuerat (:maksuerat parametrit)})
    tila)

  MaksueratTallennettu
  (process-event [{:keys [vastaus] :as e} tila]
    (tapahtumat/julkaise! {:aihe :paallystyksen-maksuerat-tallennettu})
    (assoc-in tila [:maksuerat] vastaus)))