(ns harja.palvelin.raportointi.raportit.soratietarkastus
  (:require [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.kyselyt.tarkastukset :as tarkastukset-q]
            [harja.kyselyt.toimenpideinstanssit :refer [hae-urakan-toimenpideinstanssi]]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [taoensso.timbre :as log]
            [harja.domain.roolit :as roolit]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
            [harja.math :as math]))

(def laatupoikkeama-syyt {1 "Vähintään yksi mittaustulos arvoltaan 1"
                          2 "Vähintään yksi mittaustulos arvoltaan 2 yhtenäisellä 20m tie­osuudella hoito­luokassa II tai III."})

(defn laatupoikkeama-tapahtunut? [tarkastus]
  (let [kuntoarvot ((juxt :polyavyys :tasaisuus :kiinteys) tarkastus)
        tien-pituus (get-in tarkastus [:tr :metrit])]
    (cond
      (some #(= % 1) kuntoarvot)
      1

      (and tien-pituus (> tien-pituus 20)
           (or (= (:hoitoluokka tarkastus) 2)
               (= (:hoitoluokka tarkastus) 3))
           (some #(= % 2) kuntoarvot))
      2
      :default
      nil)))

(defn tarkastuksien-laatupoikkeamat [tarkastukset]
  (sort (distinct (keep laatupoikkeama-tapahtunut? tarkastukset))))

(defn muodosta-raportin-rivit
  "Muodostaa annetuista tarkastukset-riveistä raportilla näytettävät rivit eli yhdistää rivit niin,
  että sama tieosuus ja sama päivä esiintyy aina yhdellä rivillä.
  Jokaisella yhdistetyllä rivillä lasketaan yhteen saman päivän ja tien tarkastuksista saadut kuntoarvot (1-5),
  toisin sanoen kuinka monessa mittauksessa mikäkin kuntoarvo esiintyi.
  Ei säilytä rivien järjestystä."
  [tarkastukset]
  (let [tarkastusryhmat (group-by
                          (fn [rivi]
                            [(pvm/paivan-alussa (:aika rivi))
                             (get-in rivi [:tr :numero])
                             (get-in rivi [:tr :alkuosa])
                             (get-in rivi [:tr :alkuetaisyys])
                             (get-in rivi [:tr :loppuosa])
                             (get-in rivi [:tr :loppuetaisyys])
                             (:hoitoluokka rivi)])
                          tarkastukset)
        yhdistetyt-rivit
        (mapv (fn [tarkastusryhma]
                (let [tarkastukset (get tarkastusryhmat tarkastusryhma)
                      yhdistettava-rivi (first tarkastukset)
                      laske-kuntoarvon-summa (fn [rivit arvo]
                                               ;; Laskee annetun kuntoarvon summan annettujen
                                               ;; rivien kaikista mittausluokista
                                               (reduce
                                                 (fn [nykysumma seuraava-rivi]
                                                   (let [kuntoarvot ((juxt :polyavyys :tasaisuus :kiinteys) seuraava-rivi)]
                                                     (+ nykysumma (count (filter #(= % arvo) kuntoarvot)))))
                                                 0
                                                 rivit))
                      laatuarvot (mapv (fn [arvo]
                                         (laske-kuntoarvon-summa tarkastukset arvo))
                                       (range 1 6))
                      laatuarvot-yhteensa (reduce + laatuarvot)]
                  (merge yhdistettava-rivi
                         (zipmap (range 1 6)
                                 (map (juxt
                                        ;; laatuarvon summa
                                        #(nth laatuarvot %)
                                        ;; laatuarvon summan osuus
                                        #(Math/round (math/osuus-prosentteina (nth laatuarvot %) laatuarvot-yhteensa)))
                                      (range 5)))
                         {:laatuarvot-yhteensa laatuarvot-yhteensa
                          :laatuarvo-1+2-summa (+ (first laatuarvot) (second laatuarvot))
                          :laatupoikkeamat (tarkastuksien-laatupoikkeamat tarkastukset)})))
              (keys tarkastusryhmat))]
    yhdistetyt-rivit))

(defn hae-tarkastukset-urakalle [db user {:keys [urakka-id alkupvm loppupvm tienumero]}]
  (tarkastukset-q/hae-urakan-soratietarkastukset-raportille db
                                                            urakka-id
                                                            alkupvm
                                                            loppupvm
                                                            (not (nil? tienumero))
                                                            tienumero
                                                            (roolit/urakoitsija? user)))

(defn hae-tarkastukset-hallintayksikolle [db user {:keys [hallintayksikko-id alkupvm loppupvm tienumero urakkatyyppi]}]
  (tarkastukset-q/hae-hallintayksikon-soratietarkastukset-raportille db
                                                                     hallintayksikko-id
                                                                     (when urakkatyyppi (name urakkatyyppi))
                                                                     alkupvm
                                                                     loppupvm
                                                                     (not (nil? tienumero))
                                                                     tienumero
                                                                     (roolit/urakoitsija? user)))

(defn hae-tarkastukset-koko-maalle [db user {:keys [alkupvm loppupvm tienumero urakkatyyppi]}]
  (tarkastukset-q/hae-koko-maan-soratietarkastukset-raportille db
                                                               (when urakkatyyppi (name urakkatyyppi))
                                                               alkupvm
                                                               loppupvm
                                                               (not (nil? tienumero))
                                                               tienumero
                                                               (roolit/urakoitsija? user)))

(defn hae-tarkastukset [db user {:keys [konteksti urakka-id hallintayksikko-id alkupvm loppupvm tienumero urakkatyyppi]}]
  (case konteksti
    :urakka
    (hae-tarkastukset-urakalle db
                               user
                               {:urakka-id urakka-id
                                :alkupvm alkupvm
                                :loppupvm loppupvm
                                :tienumero tienumero})
    :hallintayksikko
    (hae-tarkastukset-hallintayksikolle db
                                        user
                                        {:hallintayksikko-id hallintayksikko-id
                                         :alkupvm alkupvm
                                         :loppupvm loppupvm
                                         :tienumero tienumero
                                         :urakkatyyppi urakkatyyppi})
    :koko-maa
    (hae-tarkastukset-koko-maalle db
                                  user
                                  {:alkupvm alkupvm
                                   :loppupvm loppupvm
                                   :tienumero tienumero
                                   :urakkatyyppi urakkatyyppi})))

(def taulukon-otsikot
  [{:leveys 8 :otsikko "Päi\u00ADvä\u00ADmää\u00ADrä"}
   {:leveys 5 :otsikko "Tie"}
   {:leveys 6 :otsikko "Aosa"}
   {:leveys 6 :otsikko "Aet"}
   {:leveys 6 :otsikko "Losa"}
   {:leveys 6 :otsikko "Let"}
   {:leveys 5 :otsikko "Hoi\u00ADto\u00ADluok\u00ADka"}
   {:leveys 8 :otsikko "1" :tyyppi :arvo-ja-osuus}
   {:leveys 8 :otsikko "2" :tyyppi :arvo-ja-osuus}
   {:leveys 8 :otsikko "3" :tyyppi :arvo-ja-osuus}
   {:leveys 8 :otsikko "4" :tyyppi :arvo-ja-osuus}
   {:leveys 8 :otsikko "5" :tyyppi :arvo-ja-osuus}
   {:leveys 8 :otsikko "Yht" :tyyppi :arvo-ja-osuus}
   {:leveys 8 :otsikko "1+2" :tyyppi :arvo-ja-osuus}
   {:leveys 10 :otsikko "Laa\u00ADtu\u00ADpoik\u00ADke\u00ADa\u00ADma"}])

(def tr-kentat [[:tr :numero]
                [:tr :alkuosa]
                [:tr :alkuetaisyys]
                [:tr :loppuosa]
                [:tr :loppuetaisyys]])

(defn raportti-rivi [rivi]
  (vec (concat
         ;; päivämäärä
         [(pvm/pvm (:aika rivi))]

         ;; tie,aosa,aet,losa,let
         (map #(get-in rivi %) tr-kentat)

         ;; hoitoluokka
         [(fmt/roomalaisena-numerona (:hoitoluokka rivi))]

         ;; arvot ja prosentit 1-5
         (map (fn [numero]
                [:arvo-ja-osuus {:arvo (get-in rivi [numero 0]) :osuus (get-in rivi [numero 1])}])
              (range 1 6))

         ;; yhteensä, 1+2 yhteensä
         [[:arvo-ja-osuus {:arvo (:laatuarvot-yhteensa rivi) :osuus 100}]
          [:arvo-ja-osuus {:arvo (:laatuarvo-1+2-summa rivi) :osuus (+ (get-in rivi [1 1])
                                                                       (get-in rivi [2 1]))}]
          ;; laatupoikkeamat
          (when-not (empty? (:laatupoikkeamat rivi))
            (str "Kyllä" " (" (clojure.string/join ", " (:laatupoikkeamat rivi)) ")"))])))

(defn yhteensa-rivi [naytettavat-rivit]
  (let [laatuarvo-summat (map (fn [arvo]
                                (reduce + (keep #(get-in % [arvo 0])  naytettavat-rivit)))
                              (range 1 6))
        laatuarvot-1+2-summa (+ (first laatuarvo-summat)
                                (second laatuarvo-summat))
        laatuarvo-summat-yhteensa (reduce + laatuarvo-summat)
        laatuarvot-1+2-osuus (if (not= laatuarvo-summat-yhteensa 0)
                               (Math/round (* (float (/ laatuarvot-1+2-summa
                                                        laatuarvo-summat-yhteensa)) 100))
                               0)]
    (vec (concat
           ["Yhteensä" nil nil nil nil nil nil]
           (map
             (fn [numero]
               [:arvo-ja-osuus {:arvo (nth laatuarvo-summat numero)
                                :osuus (Math/round (math/osuus-prosentteina
                                                     (nth laatuarvo-summat numero) laatuarvo-summat-yhteensa))}])
             (range 5))
           [[:arvo-ja-osuus {:arvo laatuarvo-summat-yhteensa
                             :osuus 100}]
            [:arvo-ja-osuus {:arvo laatuarvot-1+2-summa
                             :osuus laatuarvot-1+2-osuus}]
            nil]))))

(defn suorita [db user {:keys [urakka-id hallintayksikko-id alkupvm loppupvm tienumero urakkatyyppi] :as parametrit}]
  (let [konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        tarkastukset (map konv/alaviiva->rakenne
                          (hae-tarkastukset db user
                                            {:konteksti konteksti
                                                :urakka-id urakka-id
                                                :hallintayksikko-id hallintayksikko-id
                                                :alkupvm alkupvm
                                                :loppupvm loppupvm
                                                :tienumero tienumero
                                                :urakkatyyppi urakkatyyppi}))
        naytettavat-rivit (muodosta-raportin-rivit tarkastukset)
        ainakin-yksi-poikkeama? (true? (some
                                         #(not (empty? (:laatupoikkeamat %)))
                                         naytettavat-rivit))
        raportin-nimi "Soratietarkastusraportti"
        otsikko (raportin-otsikko
                  (case konteksti
                    :urakka (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio db hallintayksikko-id)))
                    :koko-maa "KOKO MAA")
                  raportin-nimi alkupvm loppupvm)
        ryhmittellyt-rivit (yleinen/ryhmittele-tulokset-raportin-taulukolle
                             (reverse (sort-by (fn [rivi]
                                                 [(:aika rivi)
                                                  (get-in rivi [:tr :numero])])
                                               naytettavat-rivit))
                             :urakka
                             raportti-rivi)
        kylla-loppuiset-rivi-indeksit (fn [ryhmittellyt-rivit]
                                        (into #{} (keep-indexed
                                                    (fn [index rivi]
                                                      (when (and (vector? rivi)
                                                                 (not (nil? (last rivi)))
                                                                 (.contains (last rivi) "Kyllä"))
                                                        index))
                                                    ryhmittellyt-rivit)))]
    [:raportti {:orientaatio :landscape
                :nimi        raportin-nimi}
     [:taulukko {:otsikko otsikko
                 :tyhja (when (empty? naytettavat-rivit) "Ei raportoitavia tarkastuksia.")
                 :korosta-rivit (kylla-loppuiset-rivi-indeksit ryhmittellyt-rivit)
                 :viimeinen-rivi-yhteenveto? true
                 :sheet-nimi raportin-nimi}
      taulukon-otsikot
      (remove nil?
              (conj
                ;; Raportin varsinainen data
                ryhmittellyt-rivit
                ;; Yhteensä-rivi, jos tarvitaan
                (when (not (empty? naytettavat-rivit))
                  (yhteensa-rivi naytettavat-rivit))))]
     ;; Poikkeamien selitykset
     (when ainakin-yksi-poikkeama?
       [:yhteenveto
        (mapv
          (fn [avain]
            [avain (get laatupoikkeama-syyt avain)])
          (keys laatupoikkeama-syyt))])]))
