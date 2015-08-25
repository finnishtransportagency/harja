(ns harja.tiedot.tilannekuva.historiakuva
  (:require [reagent.core :refer [atom]]

            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log]]
            [cljs.core.async :refer [<!]]
            [harja.atom :refer-macros [reaction<!] :refer [paivita-periodisesti]]
            [harja.tiedot.navigaatio :as nav]
            [harja.pvm :as pvm]
            [cljs-time.core :as t])

  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

;; Mitä haetaan?
(defonce hae-toimenpidepyynnot? (atom true))
(defonce hae-kyselyt? (atom true))
(defonce hae-tiedoitukset? (atom true))
(defonce hae-turvallisuuspoikkeamat? (atom true))
(defonce hae-tarkastukset? (atom true))
(defonce hae-havainnot? (atom true))
(defonce hae-onnettomuudet? (atom true))
(defonce hae-paikkaustyot? (atom true))
(defonce hae-paallystystyot? (atom true))

;; Millä ehdoilla haetaan?
(defonce valittu-aikasuodatin (atom :lyhyt))
(defonce lyhyen-suodattimen-asetukset (atom {:pvm (pvm/nyt) :kellonaika "12:00" :plusmiinus 12}))
(defonce pitkan-suodattimen-asetukset (atom {:alku (first (pvm/kuukauden-aikavali (pvm/nyt)))
                                             :loppu (second (pvm/kuukauden-aikavali (pvm/nyt)))}))

(defonce nakymassa? (atom false))
(defonce taso-historiakuva (atom false))

;; Haetaan/päivitetään toimenpidekoodit kun tullaan näkymään
(defonce toimenpidekoodit (reaction<! [nakymassa? @nakymassa?]
                                      (when nakymassa?
                                        (go (let [res (<! (k/post! :hae-toimenpidekoodit-historiakuvaan {:urakka (:id @nav/valittu-urakka)}))]
                                              res)))))

(defonce naytettavat-toteumatyypit (reaction
                                     (mapv :nimi @toimenpidekoodit)))

(defonce valitut-toteumatyypit (atom #{}))

(def haetut-asiat (atom nil))

(defn oletusalue [asia]
  {:type        :circle
   :coordinates (:sijainti asia)
   :color       "green"
   :radius      5000
   :stroke      {:color "black" :width 10}})

(defmulti kartalla-xf :tyyppi)
(defmethod kartalla-xf :ilmoitus [ilmoitus]
  (assoc ilmoitus
    :type :ilmoitus
    :alue (oletusalue ilmoitus)))

(defmethod kartalla-xf :havainto [havainto]
  (assoc havainto
    :type :havainto
    :alue (oletusalue havainto)))

(defmethod kartalla-xf :tarkastus [tarkastus]
  (assoc tarkastus
    :type :tarkastus
    :alue (oletusalue tarkastus)))

(defmethod kartalla-xf :toteuma [toteuma]
  (assoc toteuma
    :type :toteuma
    :alue {
           :type   :arrow-line
           :points (mapv :sijainti (sort-by
                                     :aika
                                     pvm/ennen?
                                     (:reittipisteet toteuma)))}))

(defmethod kartalla-xf :turvallisuuspoikkeama [tp]
  (assoc tp
    :type :turvallisuuspoikkeama
    :alue (oletusalue tp)))

(defmethod kartalla-xf :paallystystyo [pt]
  (assoc pt
    :type :paallystystyo
    :alue (oletusalue pt)))

(defmethod kartalla-xf :paikkaustyo [pt]
  (assoc pt
    :type :paikkaustyo
    :alue (oletusalue pt)))

(defmethod kartalla-xf :default [_])

(def historiakuvan-asiat-kartalla
  (reaction
    @haetut-asiat
    (when @taso-historiakuva
      (into [] (map kartalla-xf) @haetut-asiat))))

(defn kasaa-parametrit []
  {:hallintayksikko @nav/valittu-hallintayksikko-id
   :urakka          (:id @nav/valittu-urakka)
   :alue            @nav/kartalla-nakyva-alue
   :alku            (if (= @valittu-aikasuodatin :lyhyt)
                      (t/minus (pvm/->pvm-aika (str (pvm/pvm (:pvm @lyhyen-suodattimen-asetukset))
                                                    " "
                                                    (:kellonaika @lyhyen-suodattimen-asetukset)))
                               (t/hours (:plusmiinus @lyhyen-suodattimen-asetukset)))

                      (:alku @pitkan-suodattimen-asetukset))

   :loppu           (if (= @valittu-aikasuodatin :lyhyt)
                      (t/plus (pvm/->pvm-aika (str (pvm/pvm (:pvm @lyhyen-suodattimen-asetukset))
                                                   " "
                                                   (:kellonaika @lyhyen-suodattimen-asetukset)))
                              (t/hours (:plusmiinus @lyhyen-suodattimen-asetukset)))

                      (:loppu @pitkan-suodattimen-asetukset))})

(defn hae-asiat []
  (log "Hae historia! " (pr-str @valittu-aikasuodatin))
  (go
    (let [yhdista (fn [& tulokset]
                    (apply (comp vec concat) (remove k/virhe? tulokset)))
          tulos (yhdista
                  #_(when @hae-toimenpidepyynnot? (<! (k/post! :hae-toimenpidepyynnot (kasaa-parametrit))))
                  #_(when @hae-tiedoitukset? (<! (k/post! :hae-tiedoitukset (kasaa-parametrit))))
                  #_(when @hae-kyselyt? (<! (k/post! :hae-kyselyt (kasaa-parametrit))))
                  #_(when @hae-turvallisuuspoikkeamat? (<! (k/post! :hae-turvallisuuspoikkeamat (kasaa-parametrit))))
                  #_(when @hae-tarkastukset? (<! (k/post! :hae-urakan-tarkastukset (kasaa-parametrit))))
                  #_(when @hae-onnettomuudet? (<! (k/post! :hae-urakan-onnettomuudet (kasaa-parametrit))))
                  #_(when @hae-havainnot? (<! (k/post! :hae-urakan-havainnot (kasaa-parametrit))))
                  #_(when @hae-paikkaustyot? (<! (k/post! :hae-paikkaustyot (kasaa-parametrit))))
                  #_(when @hae-paallystystyot? (<! (k/post! :hae-paallystystyot (kasaa-parametrit))))
                  (when-not (empty? @valitut-toteumatyypit)
                    (<! (k/post! :hae-toteumat-historiakuvaan (assoc
                                                        (kasaa-parametrit)
                                                        :toimenpidekoodit
                                                        (mapv
                                                          :id
                                                          (filter
                                                            (fn [{:keys [nimi]}]
                                                              (get @valitut-toteumatyypit nimi))
                                                            @toimenpidekoodit)))))))]
      (reset! haetut-asiat tulos))))

(def +sekuntti+ 1000)
(def +minuutti+ (* 60 +sekuntti+))

(def +intervalli+ (* 1 +minuutti+))
(def +bufferi+ (* 1 +sekuntti+))

(def asioiden-haku (reaction<!
                     [_ @hae-toimenpidepyynnot?
                      _ @hae-kyselyt?
                      _ @hae-tiedoitukset?
                      _ @hae-turvallisuuspoikkeamat?
                      _ @hae-tarkastukset?
                      _ @hae-havainnot?
                      _ @hae-onnettomuudet?
                      _ @hae-paikkaustyot?
                      _ @hae-paallystystyot?
                      _ @toimenpidekoodit
                      _ @naytettavat-toteumatyypit
                      _ @valitut-toteumatyypit
                      _ @valittu-aikasuodatin
                      _ @lyhyen-suodattimen-asetukset
                      _ @pitkan-suodattimen-asetukset
                      _ @nakymassa?
                      _ @nav/kartalla-nakyva-alue
                      _ @nav/valittu-urakka
                      _ @nav/valittu-hallintayksikko-id]
                     {:odota +bufferi+}
                     (when (and @nakymassa?
                                (or
                                  (and
                                    (= @valittu-aikasuodatin :lyhyt)
                                    (not (some nil? (vals @lyhyen-suodattimen-asetukset))))
                                  (and
                                    (= @valittu-aikasuodatin :pitka)
                                    (not (some nil? (vals @pitkan-suodattimen-asetukset))))))
                       (hae-asiat))))

(def lopeta-asioiden-haku (paivita-periodisesti asioiden-haku +intervalli+))

