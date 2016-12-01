(ns harja.tiedot.urakka.toteumat.tiemerkinta-muut-tyot
  (:require [reagent.core :refer [atom]]
            [harja.tiedot.urakka.suunnittelu.muut-tyot :as muut-tyot]
            [cljs.core.async :refer [<! >! chan]]
            [harja.loki :refer [log logt]]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.navigaatio :as nav]
            [tuck.core :as t]
            [harja.asiakas.kommunikaatio :as k]
            [harja.pvm :as pvm]
            [harja.ui.protokollat :as protokollat])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

;; Tila

(def muut-tyot (atom {:valittu-toteuma nil
                      :toteumat nil
                      :valinnat {:urakka nil
                                 :sopimus nil}
                      :laskentakohteet {}}))

(defonce valinnat
  (reaction
    {:urakka (:id @nav/valittu-urakka)
     :sopimus (first @u/valittu-sopimusnumero)
     :sopimuskausi @u/valittu-hoitokausi}))

;; Tapahtumat

(defrecord YhdistaValinnat [valinnat])
(defrecord LaskentakohteetHaettu [laskentakohteet])
(defrecord ToteumatHaettu [tulokset])
(defrecord UusiToteuma [])
(defrecord HaeToteuma [hakuehdot])
(defrecord ValitseToteuma [tyo])
(defrecord MuokkaaToteumaa [uusi-tyo])
(defrecord ToteumaTallennettu [toteumat])

;; Tapahtumien käsittely

(defn hae-toteumat [{:keys [urakka] :as hakuparametrit}]
  (let [tulos! (t/send-async! ->ToteumatHaettu)]
    (go (let [tyot (<! (k/post! :hae-yllapito-toteumat {:urakka urakka}))]
          (when-not (k/virhe? tyot)
            (tulos! tyot))))))

(defn hae-laskentakohteet [{:keys [urakka] :as hakuparametrit}]
  (let [tulos! (t/send-async! ->LaskentakohteetHaettu)]
    (go (let [laskentakohteet (<! (k/post! :hae-laskentakohteet {:urakka urakka}))]
          (when-not (k/virhe? laskentakohteet)
            (tulos! laskentakohteet))))))

(defn hae-toteuma [id urakka]
  (let [tulos! (t/send-async! ->ValitseToteuma)]
    (go (let [tyo (<! (k/post! :hae-yllapito-toteuma {:urakka urakka
                                                      :id id}))]
          (when-not (k/virhe? tyo)
            (tulos! tyo))))))

(defn tallenna-toteuma [toteuma urakka-id]
  (k/post! :tallenna-yllapito-toteuma (assoc toteuma :urakka urakka-id)))

(def laskentakohdehaku
    (reify protokollat/Haku
      (hae [_ teksti]
        (go (let [haku second
                  laskentakohteet (:laskentakohteet @muut-tyot)
                  itemit (if (< (count teksti) 1)
                           laskentakohteet
                           (filter #(not= (.indexOf (.toLowerCase (haku %))
                                                    (.toLowerCase teksti)) -1)
                                   laskentakohteet))]
              (vec (sort itemit)))))))

(extend-protocol t/Event

  YhdistaValinnat
  (process-event [{:keys [valinnat] :as e} tila]
    (hae-toteumat {:urakka (:urakka valinnat)})
    (hae-laskentakohteet {:urakka (:urakka valinnat)})
    (update-in tila [:valinnat] merge valinnat))

  LaskentakohteetHaettu
  (process-event [{:keys [laskentakohteet] :as e} tila]
    (assoc-in tila [:laskentakohteet] (reduce merge
                                              {nil "Ei laskentakohdetta"}
                                              (mapv
                                                      #(-> {(:id %) (:nimi %)})
                                                      laskentakohteet))))

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

  ToteumaTallennettu
  (process-event [{:keys [toteumat] :as e} tila]
    (assoc-in tila [:toteumat] toteumat)))