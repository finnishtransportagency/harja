(ns harja.tiedot.urakka.toteumat.tiemerkinta-muut-kustannukset
  (:require [reagent.core :refer [atom]]
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

(def +kustannustyypit+ [:muu :arvonmuutos :indeksi])

;; Tila
(def muut-tyot (atom {:valittu-toteuma nil
                      :toteumat nil
                      :valinnat {:urakka nil
                                 :sopimus nil}
                      :laskentakohteet {}
                      ;; uusi-laskentakohde sisältää käyttäjän käsin kirjoittaman laskentakohteen
                      ;; nimen. Sitä käytetään, jos ei lomakkeessa ei ole laskentakohdetta valittuna vaan halutaan luoda uusi
                      :uusi-laskentakohde nil}))

(def valittu-kustannustyyppi (atom nil))
(def valitse-kustannustyyppi! #(reset! valittu-kustannustyyppi %))

(defonce valinnat
  (reaction
    {:urakka (:id @nav/valittu-urakka)
     :sopimus (first @u/valittu-sopimusnumero)
     :sopimuskausi @u/valittu-hoitokausi
     :valittu-kustannustyyppi valittu-kustannustyyppi}))

;; Tapahtumat

(defrecord YhdistaValinnat [valinnat])
(defrecord LaskentakohteetHaettu [laskentakohteet])
(defrecord ToteumatHaettu [tulokset])
(defrecord UusiToteuma [])
(defrecord HaeToteuma [hakuehdot])
(defrecord ValitseToteuma [tyo])
(defrecord MuokkaaToteumaa [uusi-tyo])
(defrecord ToteumaTallennettu [vastaus])
(defrecord LaskentakohdeMuuttui [nimi])

;; Tapahtumien käsittely

(defn- muunna-laskentakohteet [laskentakohteet]
  (reduce merge
          {nil "Ei laskentakohdetta"}
          (mapv
            #(-> {(:id %) (:nimi %)})
            laskentakohteet)))

(defn hae-toteumat [{:keys [urakka sopimus alkupvm loppupvm] :as hakuparametrit}]
  (let [tulos! (t/send-async! ->ToteumatHaettu)]
    (go (let [tyot (<! (k/post! :hae-yllapito-toteumat {:urakka urakka
                                                        :sopimus sopimus
                                                        :alkupvm alkupvm
                                                        :loppupvm loppupvm}))]
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

(defn tallenna-toteuma [{:keys [toteuma urakka sopimus alkupvm loppupvm
                                uusi-laskentakohde] :as mappi} laskentakohteet]
  (let [laskentakohde-luettelossa? (first (filter #(= (second %) uusi-laskentakohde) laskentakohteet))
        laskentakohde (or laskentakohde-luettelossa? (:laskentakohde toteuma))
        tallennettava-toteuma (assoc toteuma
                                :laskentakohde laskentakohde
                                :uusi-laskentakohde (when (and (not laskentakohde-luettelossa?)
                                                               (not (empty? uusi-laskentakohde)))
                                                      uusi-laskentakohde))]
    (k/post! :tallenna-yllapito-toteumat {:urakka-id urakka
                                          :sopimus-id sopimus
                                          :alkupvm alkupvm
                                          :loppupvm loppupvm
                                          :toteumat [tallennettava-toteuma]})))

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
    (hae-toteumat {:urakka (:urakka valinnat)
                   :sopimus (:sopimus valinnat)
                   :alkupvm (first (:sopimuskausi valinnat))
                   :loppupvm (second (:sopimuskausi valinnat))})
    (hae-laskentakohteet {:urakka (:urakka valinnat)})
    (update-in tila [:valinnat] merge valinnat))

  LaskentakohteetHaettu
  (process-event [{:keys [laskentakohteet] :as e} tila]
    (assoc-in tila [:laskentakohteet] (muunna-laskentakohteet laskentakohteet)))

  ToteumatHaettu
  (process-event [{:keys [tulokset] :as e} tila]
    (assoc-in tila [:toteumat] tulokset))

  UusiToteuma
  (process-event [_ tila]
    (assoc-in tila [:valittu-toteuma] {:paivamaara (pvm/nyt)
                                       :laskentakohde [nil ""]}))

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
  (process-event [{:keys [vastaus] :as e} tila]
    (assoc tila :toteumat (:toteumat vastaus)
                :laskentakohteet (muunna-laskentakohteet (:laskentakohteet vastaus))
                :valittu-toteuma nil))

  LaskentakohdeMuuttui
  (process-event [{:keys [nimi] :as e} tila]
    (assoc-in tila [:uusi-laskentakohde] nimi)))
