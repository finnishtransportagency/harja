(ns harja.tiedot.urakka
  "Tämä nimiavaruus hallinnoi urakan usealle toiminnolle yhteisiä tietoja"
  (:require [reagent.core :refer [atom] :as r]
            [cljs-time.core :as time]
            [cljs-time.coerce :as tc]
            [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.urakan-toimenpiteet :as urakan-toimenpiteet]
            [harja.tiedot.urakka.toteumat :as toteumat]
            [harja.tiedot.urakka.muut-tyot :as muut-tyot]
            [harja.tiedot.urakka.organisaatio :as organisaatio]
            [harja.loki :refer [log tarkkaile!]]
            [harja.pvm :as pvm]
            [harja.atom :refer-macros [reaction<!]])

  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   ))

(defonce valittu-sopimusnumero (let [val (atom nil)]
                                 (run! (reset! val (first (:sopimukset @nav/valittu-urakka))))
                                 val))

(defonce urakan-yks-hint-tyot (atom nil))
(defonce urakan-kok-hint-tyot (atom nil))

(defn valitse-sopimusnumero! [sn]
  (reset! valittu-sopimusnumero sn))

(defonce urakan-toimenpideinstanssit
         (let [toimenpideinstanssit (atom nil)]
           (run! (let [ur @nav/valittu-urakka]
                   (if ur
                     (go ;; varo ettei muutu uudestaan!
                       (when (= ur @nav/valittu-urakka)
                         (reset! toimenpideinstanssit (<! (urakan-toimenpiteet/hae-urakan-toimenpiteet (:id ur))))))
                     (reset! toimenpideinstanssit nil))))
           toimenpideinstanssit))

(defonce urakan-toimenpideinstanssit+kaikki+muut (reaction
                                                   (let [urakan-toimenpideinstanssit @urakan-toimenpideinstanssit]
                                                     (-> urakan-toimenpideinstanssit
                                                         (conj {:tpi_nimi "Muut"})))))

(defonce valittu-toimenpideinstanssi
         (let [val (atom nil)]
           (run! (reset! val (first @urakan-toimenpideinstanssit)))
           val))

(defn valitse-toimenpideinstanssi! [tpi]
  (reset! valittu-toimenpideinstanssi tpi))

(defn hoitokaudet
  "Palauttaa urakan hoitokaudet, jos kyseessä on hoidon alueurakka. Muille urakoille palauttaa
urakan sopimuskaudet. Sopimuskaudet ovat sopimuksen kesto jaettuna sopimusvuosille (ensimmäinen
ja viimeinen voivat olla vajaat)."
  [ur]
  (let [ensimmainen-vuosi (pvm/vuosi (:alkupvm ur))
        viimeinen-vuosi (pvm/vuosi (:loppupvm ur))]
    (if (= :hoito (:tyyppi ur))
      ;; Hoidon alueurakan hoitokaudet
      (mapv (fn [vuosi]
              [(pvm/hoitokauden-alkupvm vuosi)
               (pvm/hoitokauden-loppupvm (inc vuosi))])
            (range ensimmainen-vuosi viimeinen-vuosi))
      ;; Muiden urakoiden sopimusaika pilkottuna vuosiin
      (if (= ensimmainen-vuosi viimeinen-vuosi)
        ;; Jos alku- ja loppuvuosi on sama, palautetaan vain 1 kausi
        [[(:alkupvm ur) (:loppupvm ur)]]

        ;; Muutoin palautetaan [ensimmäisen vuoden osa] .. [täydet vuodet] .. [viimeisen vuoden osa]
        (vec (concat [[(:alkupvm ur) (pvm/vuoden-viim-pvm ensimmainen-vuosi)]]
                     (mapv (fn [vuosi]
                             [(pvm/vuoden-eka-pvm vuosi) (pvm/vuoden-viim-pvm vuosi)])
                           (range (inc ensimmainen-vuosi) viimeinen-vuosi))
                     [[(pvm/vuoden-eka-pvm viimeinen-vuosi) (:loppupvm ur)]]))))))

(defonce valitun-urakan-hoitokaudet
  (reaction (when-let [ur @nav/valittu-urakka]
              (hoitokaudet ur))))

(defn paattele-valittu-hoitokausi [hoitokaudet]
  (when-not (empty? hoitokaudet)
    (let [[alku-pvm _] (first hoitokaudet)
          [_ loppu-pvm] (last hoitokaudet)
          nyt (pvm/nyt)]
      (cond
        ;; Jos urakka ei ole vielä alkanut, valitaan 1. hoitokausi
        (pvm/ennen? nyt alku-pvm)
        (first hoitokaudet)

        ;; Jos urakka on jo päättynyt, valitaan viimeinen hoitokausi
        (pvm/jalkeen? nyt loppu-pvm)
        (last hoitokaudet)

        ;; Jos urakka on käynnissä, valitaan hoitokausi, joka on käynnissä
        :default
        (first (filter (fn [[alku loppu]]
                         (pvm/valissa? nyt alku loppu))
                       hoitokaudet))))))


(defonce valittu-hoitokausi
  (reaction (paattele-valittu-hoitokausi @valitun-urakan-hoitokaudet)))


(defonce valittu-aikavali (reaction [(first @valittu-hoitokausi) (second @valittu-hoitokausi)]))

(defn valitse-hoitokausi! [hk]
  (reset! valittu-hoitokausi hk))

;; rivit ryhmitelty tehtävittäin, rivissä oltava :alkupvm ja :loppupvm
(defn jaljella-olevien-hoitokausien-rivit
  "Palauttaa ne rivit joiden loppupvm on joku jaljella olevien kausien pvm:stä"
  [rivit-tehtavittain jaljella-olevat-kaudet]
  (mapv (fn [tehtavan-rivit]
          (filter (fn [tehtavan-rivi]
                    (some #(pvm/sama-pvm? (second %) (:loppupvm tehtavan-rivi)) jaljella-olevat-kaudet))
                  tehtavan-rivit)) rivit-tehtavittain))

(defn tulevat-hoitokaudet [ur hoitokausi]
  (drop-while #(not (pvm/sama-pvm? (second %) (second hoitokausi)))
              (hoitokaudet ur)))

(defn rivit-tulevillekin-kausille [ur rivit hoitokausi]
  (into []
        (mapcat (fn [[alku loppu]]
                  (map (fn [rivi]
                         ;; tässä hoitokausien alkupvm ja loppupvm liitetään töihin
                         (assoc rivi :alkupvm alku :loppupvm loppu)) rivit)))
        (tulevat-hoitokaudet ur hoitokausi)))



;; fixme if you can, man. En saanut kohtuullisessa ajassa tätä generalisoitua
;; siistiksi osaksi rivit-tulevillekin-kausille-funktiota
(defn rivit-tulevillekin-kausille-kok-hint-tyot [ur rivit hoitokausi]
  (into []
        (mapcat (fn [[alku loppu]]
                  (map (fn [rivi]
                         ;; maksupvm:n vuotta täytyy päivittää eikä se välttämättä ole sama kuin työn :vuosi
                         (let [tyon-kalenteri-vuosi (if (<= 10 (:kuukausi rivi) 12)
                                                      (pvm/vuosi alku)
                                                      (pvm/vuosi loppu))
                               maksupvmn-vuoden-erotus (if (:maksupvm rivi)
                                                         (- (time/year (:maksupvm rivi)) (:vuosi rivi))
                                                         0)
                               uusi-maksupvm (if (:maksupvm rivi)
                                               (pvm/luo-pvm (+ tyon-kalenteri-vuosi maksupvmn-vuoden-erotus)
                                                            (- (time/month (:maksupvm rivi)) 1)
                                                            (time/day (:maksupvm rivi)))
                                               nil)]
                           (assoc rivi :alkupvm alku
                                       :loppupvm loppu
                                       :vuosi tyon-kalenteri-vuosi
                                       :maksupvm uusi-maksupvm)))
                       rivit)))
        (tulevat-hoitokaudet ur hoitokausi)))

(defn ryhmittele-hoitokausittain
  "Ottaa rivejä, jotka sisältävät :alkupvm ja :loppupvm, ja palauttaa ne ryhmiteltynä hoitokausiin.
  Palauttaa mäpin, jossa avaimena on hoitokauden [alku loppu] ja arvona on sen hoitokauden rivit.
  Jos sekvenssi hoitokausia on annettu, varmistetaan että mäpissä on kaikille niille avaimet. Tällä tavalla
  voidaan luoda tyhjät ryhmät myös hoitokausille, joilla ei ole yhtään riviä."
  ([rivit] (ryhmittele-hoitokausittain rivit nil))
  ([rivit hoitokaudet]
   (loop [ryhmitelty (group-by (juxt :alkupvm :loppupvm)
                               rivit)
          [kausi & hoitokaudet] hoitokaudet]
     (if-not kausi
       ryhmitelty
       (if (contains? ryhmitelty kausi)
         (recur ryhmitelty hoitokaudet)
         (recur (assoc ryhmitelty kausi []) hoitokaudet))))))

(defonce urakan-valittu-valilehti (atom :yleiset))
(defonce suunnittelun-valittu-valilehti (atom :kokonaishintaiset))
(defonce toteumat-valilehti (atom :yksikkohintaiset-tyot))

(defonce urakan-toimenpiteet-ja-tehtavat
  (reaction<! [ur (:id @nav/valittu-urakka)]
              (when ur
                (urakan-toimenpiteet/hae-urakan-toimenpiteet-ja-tehtavat ur))))

(defonce urakan-organisaatio
  (reaction<! [ur (:id @nav/valittu-urakka)]
              (when ur
                (organisaatio/hae-urakan-organisaatio ur))))

(defonce muutoshintaiset-tyot
  (reaction<! [ur (:id @nav/valittu-urakka)
               suunnittelun-sivu @suunnittelun-valittu-valilehti
               toteuman-sivu @toteumat-valilehti]
              (when (and ur (or
                             (= :muut suunnittelun-sivu)
                             (= :muut-tyot toteuman-sivu)))
                (muut-tyot/hae-urakan-muutoshintaiset-tyot ur))))

(defonce muut-tyot-hoitokaudella
  (reaction<! [ur (:id @nav/valittu-urakka)
               sopimus-id (first @valittu-sopimusnumero)
               aikavali @valittu-hoitokausi
               sivu @toteumat-valilehti]
              (when (and ur sopimus-id aikavali (= :muut-tyot sivu))
                (toteumat/hae-urakan-muut-tyot ur sopimus-id aikavali))))

(defonce erilliskustannukset-hoitokaudella
  (reaction<! [ur (:id @nav/valittu-urakka)
               aikavali @valittu-hoitokausi
               sivu @toteumat-valilehti]
              (when (and ur aikavali (= :erilliskustannukset sivu))
                (toteumat/hae-urakan-erilliskustannukset ur aikavali))))

(defn vaihda-urakkatyyppi
  [urakka-id uusi-urakkatyyppi]
  (k/post! :tallenna-urakan-tyyppi
           {:urakka-id urakka-id
            :urakkatyyppi uusi-urakkatyyppi}))