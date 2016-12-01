(ns harja.tiedot.urakka.toteumat.tiemerkinta-muut-tyot
  (:require [reagent.core :refer [atom]]
            [harja.tiedot.urakka.suunnittelu.muut-tyot :as muut-tyot]
            [cljs.core.async :refer [<! >! chan]]
            [harja.loki :refer [log logt]]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.navigaatio :as nav]
            [tuck.core :as t]
            [harja.asiakas.kommunikaatio :as k]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

;; Tila

(def muut-tyot (atom {:valittu-toteuma nil
                      :toteumat nil
                      :valinnat {:urakka nil
                                 :sopimus nil}}))

(defonce valinnat
  (reaction
    {:urakka (:id @nav/valittu-urakka)
     :sopimus (first @u/valittu-sopimusnumero)
     :sopimuskausi @u/valittu-hoitokausi}))

;; Tapahtumat

(defrecord YhdistaValinnat [valinnat])
(defrecord ToteumatHaettu [tulokset])
(defrecord UusiToteuma [])
(defrecord HaeToteuma [hakuehdot])
(defrecord ValitseToteuma [tyo])
(defrecord MuokkaaToteumaa [uusi-tyo])
(defrecord TallennaToteuma [tyo])
(defrecord ToteumaTallennettu [toteumat])

;; Tapahtumien käsittely

(defn hae-toteumat [{:keys [urakka] :as hakuparametrit}]
  (let [tulos! (t/send-async! ->ToteumatHaettu)]
    (go (let [tyot (<! (k/post! :hae-yllapito-toteumat {:urakka urakka}))]
          (when-not (k/virhe? tyot)
            (tulos! tyot))))))

(defn hae-toteuma [id urakka]
  (let [tulos! (t/send-async! ->ValitseToteuma)]
    (go (let [tyo (<! (k/post! :hae-yllapito-toteuma {:urakka urakka
                                                      :id id}))]
          (when-not (k/virhe? tyo)
            (tulos! tyo))))))

(defn tallenna-toteuma [toteuma urakka-id]
  (k/post! :tallenna-yllapito-toteuma (assoc toteuma :urakka urakka-id)))

(extend-protocol t/Event

  YhdistaValinnat
  (process-event [{:keys [valinnat] :as e} tila]
    (hae-toteumat {:urakka (:urakka valinnat)})
    (update-in tila [:valinnat] merge valinnat))

  ToteumatHaettu
  (process-event [{:keys [tulokset] :as e} tila]
    (assoc-in tila [:toteumat] tulokset))

  UusiToteuma
  (process-event [_ tila]
    (assoc-in tila [:valittu-toteuma] {:paivamaara (pvm/nyt)}))

  HaeToteuma
  (process-event [{:keys [hakuehdot] :as e} tila]
    (hae-toteuma (:id hakuehdot) (:urakka hakuehdot))
    tila)

  ValitseToteuma
  (process-event [{:keys [tyo] :as e} tila]
    (assoc-in tila [:valittu-toteuma] tyo))

  MuokkaaToteumaa
  (process-event [{:keys [uusi-tyo] :as e} tila]
    (assoc-in tila [:valittu-toteuma] uusi-tyo))

  TallennaToteuma
  (process-event [{:keys [tyo] :as e} tila]
    ;; TODO
    (assoc-in tila [:valittu-toteuma] tyo))

  ToteumaTallennettu
  (process-event [{:keys [toteumat] :as e} tila]
    (assoc-in tila [:toteumat] toteumat)))