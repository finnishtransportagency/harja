(ns harja.tiedot.urakka.suunnittelu
  "Tämä nimiavaruus hallinnoi urakan suunnittelun tietoja"
  (:require [reagent.core :refer [atom] :as r]
            [cljs-time.core :as time]
            [cljs-time.coerce :as tc]
            [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.urakan-toimenpiteet :as urakan-toimenpiteet]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm])

  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]))

(defn yhdista-rivien-hoitokaudet
  "Yhdistää rivejä, jotka sisältävät :alkupvm :loppupvm, siten että kaksi peräkäistä kautta
  yhdistetään samaksi (10-12 ja 1-9 kk:t). Ottaa kaksi funktiota: ryhmittely ja yhdista.
Ryhmittelyfunktion mukaan riveille tehdään group-by. Yhdista funktiolle annetaan 2 riviä 
  aikajärjestyksessä."
  [rivit ryhmittely yhdista]
  (let [ryhmitellyt-rivit
        (mapcat #(partition 2 (sort-by :alkupvm %)) (vals (group-by ryhmittely rivit)))]
    (map (fn [[eka toka]]
           (assoc (yhdista eka toka)
             :alkupvm (:alkupvm eka)
             :loppupvm (:loppupvm toka)))
         ryhmitellyt-rivit)))

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




(def kaikki-sopimuksen-kok-hint-rivit
    (reaction (let [sopimus-id (first @u/valittu-sopimusnumero)]
                  (filter #(= sopimus-id (:sopimus %))
                          @u/urakan-kok-hint-tyot))))

(def kaikki-sopimuksen-ja-hoitokauden-kok-hint-rivit
    (reaction (let [hk-alku (first @u/valittu-hoitokausi)]
                  (filter
                      #(pvm/sama-pvm?
                          (:alkupvm %) hk-alku)
                      @kaikki-sopimuksen-kok-hint-rivit))))

(defn valitun-hoitokauden-yks-hint-kustannukset [urakka]
  (reaction (transduce (map #(* (:maara %) (:yksikkohinta %)))
                       + 0
                       (get (u/ryhmittele-hoitokausittain (into []
                                                                (filter (fn [t]
                                                                          (= (:sopimus t) (first @u/valittu-sopimusnumero))))
                                                                @u/urakan-yks-hint-tyot)
                                                          (u/hoito-tai-sopimuskaudet urakka)) @u/valittu-hoitokausi))))

(def valitun-hoitokauden-kok-hint-kustannukset
    (reaction (toiden-kustannusten-summa
                  @kaikki-sopimuksen-ja-hoitokauden-kok-hint-rivit
                  :summa)))

(defn prosessoi-tyorivit [ur rivit]
  (if (= :hoito (:tyyppi ur))
    (yhdista-rivien-hoitokaudet rivit (juxt :tehtava :sopimus)
                                (fn [eka toka]
                                  (assoc eka
                                    :maara-kkt-10-12 (:maara eka)
                                    :maara-kkt-1-9 (:maara toka)

                                    ;; Määrä on kausien yhteenlaskettu, jotta yhteensä tiedot näkyvät
                                    :maara (+ (or (:maara eka) 0)
                                              (or (:maara toka) 0))
                                    :yhteensa-kkt-10-12 (when-let [hinta (:yksikkohinta eka)]
                                                          (* (or (:maara eka) 0) hinta))
                                    :yhteensa-kkt-1-9 (when-let [hinta (:yksikkohinta eka)]
                                                        (* (or (:maara toka) 0) hinta))
                                    :yhteensa (when-let [hinta (:yksikkohinta eka)]
                                                (* (+ (or (:maara eka) 0)
                                                      (or (:maara toka) 0))
                                                   hinta))
                                    )))
    (mapv #(assoc % :yhteensa (when-let [hinta (:yksikkohinta %)]
                                (* (or (:maara %) 0) hinta)))
          rivit)))

(defn monista-tuleville-teksti [urakkatyyppi]
  (if (= urakkatyyppi :hoito)
    "Monista kaikki yo. tiedot tulevillekin hoitokausille"
    "Monista kaikki yo. tiedot tulevillekin sopimuskausille"))
