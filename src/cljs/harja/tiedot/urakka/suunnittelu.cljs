(ns harja.tiedot.urakka.suunnittelu
  "Tämä nimiavaruus hallinnoi urakan suunnittelun tietoja"
  (:require [reagent.core :refer [atom] :as r]
            [cljs-time.core :as time]
            [cljs-time.coerce :as tc]
            [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.urakan-toimenpiteet :as urakan-toimenpiteet]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm])

  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]))

(defonce valittu-sopimusnumero (reaction
                                 (first (:sopimukset @nav/valittu-urakka))))

(defn valitse-sopimusnumero! [sn]
  (reset! valittu-sopimusnumero sn))

(defonce urakan-yks-hint-tyot (atom nil))
(defonce urakan-kok-hint-tyot (atom nil))

(defonce urakan-toimenpideinstanssit
         (let [toimenpideinstanssit (atom nil)]
           (run! (let [ur @nav/valittu-urakka]
                   (if ur
                     (go ;; varo ettei muutu uudestaan!
                       (when (= ur @nav/valittu-urakka)
                         (reset! toimenpideinstanssit (<! (urakan-toimenpiteet/hae-urakan-toimenpiteet (:id ur))))))
                     (reset! toimenpideinstanssit nil))))
           toimenpideinstanssit))

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
         (reaction (hoitokaudet @nav/valittu-urakka)))

(defonce valittu-hoitokausi
         (reaction (first @valitun-urakan-hoitokaudet)))

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

(defn yhdista-rivien-hoitokaudet
  "Yhdistää rivejä, jotka sisältävät :alkupvm :loppupvm, siten että kaksi peräkäistä kautta
  yhdistetään samaksi (10-12 ja 1-9 kk:t). Ottaa kaksi funktiota: ryhmittely ja yhdista.
Ryhmittelyfunktion mukaan riveille tehdään group-by. Yhdista funktiolle annetaan 2 riviä 
  aikajärjestyksessä."
  [rivit ryhmittely yhdista]
  (map (fn [[eka toka]]
         (assoc (yhdista eka toka)
           :alkupvm (:alkupvm eka)
           :loppupvm (:loppupvm toka)))
       (mapcat #(partition 2 (sort-by :alkupvm %)) (vals (group-by ryhmittely rivit)))))

(defn jaa-rivien-hoitokaudet
  "Jakaa rivejä, joiden :alkupvm/:loppupvm ovat hoidon alueurakan hoitokausia, vuosiriveiksi.
Yhdestä rivistä tulee kaksi riviä: ensimmäisen vuoden osa (10-12 kk:t) ja jälkimmäisen vuoden
osa (1-9 kk:t). Ottaa 2 funktiota, joille koko rivi annetaan ja jonka tulee palauttaa
  1. ja 2. osa."
  [rivit ensimmainen-osa toinen-osa]
  (mapcat (fn [{:keys [alkupvm loppupvm] :as rivi}]
            [(assoc (ensimmainen-osa rivi)
               :alkupvm alkupvm
               :loppupvm (pvm/vuoden-viim-pvm (pvm/vuosi alkupvm)))
             (assoc (toinen-osa rivi)
               :alkupvm (pvm/vuoden-eka-pvm (pvm/vuosi loppupvm))
               :loppupvm loppupvm)])
          rivit))
          

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


(defn hoitokausien-sisalto-sama?
  "Kertoo onko eri hoitokausien sisältö sama päivämääriä lukuunottamatta.
  Suunniteltu käytettäväksi mm. yks.hint. ja kok.hint. töiden sekä materiaalien suunnittelussa."
  ;; uudelleennimetään muuttujia jos tästä saadaan yleiskäyttöinen esim. kok. hintaisille ja materiaaleille
  [tyorivit-tehtavittain]
  (let [tyorivit-aikajarjestyksessa (map #(sort-by :alkupvm %) tyorivit-tehtavittain)
        tyorivit-ilman-pvmia (into []
                                   (map #(map (fn [tyorivi]
                                                (dissoc tyorivi :alkupvm :loppupvm :id)) %) tyorivit-aikajarjestyksessa))]
      (every? #(apply = %) tyorivit-ilman-pvmia)))

;; FIXME: hoitokausien-sisalto-sama? ja hoitokaudet-samat? pitäisi olla 1 funktio
;; erona on nyt, että hoitokausien-sisalto-sama? ottaa tehtävittäin ryhmitellyt tiedot
;; ja hoitokaudet-samat? ottaa hoitokausittain ryhmitellyt

(defn hoitokaudet-samat? 
  "Testaa onko eri hoitokausien tiedot samat (päivämääriä ja id numeroita lukuunottamatta).
  Ottaa sisään hoitokausittain jaotellut rivit."
  [hoitokausien-rivit]
  (let [vertailumuoto (fn [rivit]
                        ;; vertailtaessa "samuutta" eri hoitokausien välillä poistetaan pvm:t ja id:t
                        (into #{}
                              (map #(dissoc % :alkupvm :loppupvm :id))
                              rivit))
        kaudet (map vertailumuoto hoitokausien-rivit)]
    (apply =  kaudet)))

(defn varoita-ylikirjoituksesta?
  "Ottaa sisään hoitokausittain ryhmitellyt rivit sekä nykyisen hoitokauden [alku loppu]. Tarkistaa, että
kaikki nykyisen hoitokauden jälkeen olevat hoitokaudet ovat kaikki tyhjiä tai kaikki samoja nykyisen hoitokauden kanssa."
  [hoitokausittain-ryhmitellyt-rivit nykyinen-hoitokausi]
  (let [hoitokausi-alku (tc/to-long (first nykyinen-hoitokausi))
        hoitokaudet (into []
                          (comp (drop-while #(> hoitokausi-alku (tc/to-long (ffirst %))))
                                (map second))
                          (sort-by (comp tc/to-long ffirst)
                                   hoitokausittain-ryhmitellyt-rivit))]
    (if (every? empty? (rest hoitokaudet))
      false
      (not (hoitokaudet-samat? hoitokaudet)))))

(defn toiden-kustannusten-summa
  "Laskee yhteen annettujen työrivien kustannusten summan"
  ([tyorivit] (toiden-kustannusten-summa tyorivit :yhteensa))
  ([tyorivit kentta]
  (apply + (map kentta
                tyorivit))))

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

(def kaikki-sopimuksen-kok-hint-rivit
    (reaction (let [sopimus-id (first @valittu-sopimusnumero)]
                  (filter #(= sopimus-id (:sopimus %))
                          @urakan-kok-hint-tyot))))

(def kaikki-sopimuksen-ja-hoitokauden-kok-hint-rivit
    (reaction (let [hk-alku (first @valittu-hoitokausi)]
                  (filter
                      #(pvm/sama-pvm?
                          (:alkupvm %) hk-alku)
                      @kaikki-sopimuksen-kok-hint-rivit))))

(def valitun-hoitokauden-kok-hint-kustannukset
    (reaction (toiden-kustannusten-summa
                  @kaikki-sopimuksen-ja-hoitokauden-kok-hint-rivit
                  :summa)))