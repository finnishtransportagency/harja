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
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen]))

(defn laske-luvun-osuus [numerot index]
  "Ottaa luvun numerot-vectorista annetulla indeksillä ja jakaa sen vectorin kaikkien lukujen summalla."
  (let [osoittaja (nth numerot index)
        nimittaja (reduce + numerot)]
    (if (not= nimittaja 0)
      (* (float (/ osoittaja
                   nimittaja))
         100)
      0.0)))

(def laatupoikkeama-syyt {1 "Vähintään yksi mittaustulos arvoltaan 1"
                          2 "Vähintään yksi mittaustulos arvoltaan 2 yhtenäisellä 20m tie­osuudella hoito­luokassa II tai III."})

(defn laatupoikkeama-tapahtunut? [tarkastus]
  (let [kuntoarvot ((juxt :polyavyys :tasaisuus :kiinteys) tarkastus)
        tien-pituus (get-in tarkastus [:tr :metrit])]
    ; Vähintään yksi kuntoarvo sisältää arvon 1?
    (cond
      (some #(= % 1) kuntoarvot)
      1

      (and (> tien-pituus 20)
           (or (= (:hoitoluokka tarkastus) 2)
               (= (:hoitoluokka tarkastus) 3))
           (some #(= % 2) kuntoarvot))
      2

      :default
      nil)))

(defn muodosta-raportin-rivit [tarkastukset]
  "Muodostaa annetuista tarkastukset-riveistä raportilla näytettävät rivit eli yhdistää rivit niin,
  että sama tieosuus ja sama päivä esiintyy aina yhdellä rivillä.
  Jokaisella yhdistetyllä rivillä lasketaan yhteen saman päivän ja tien tarkastuksista saadut kuntoarvot (1-5),
  toisin sanoen kuinka monessa mittauksessa mikäkin kuntoarvo esiintyi."
  (let [tarkastusryhmat (group-by
                          (fn [rivi]
                            [(pvm/paivan-alussa (:aika rivi))
                             (get-in rivi [:tr :numero])
                             (get-in rivi [:tr :alkuosa])
                             (get-in rivi [:tr :alkuetaisyys])
                             (get-in rivi [:tr :loppuosa])
                             (get-in rivi [:tr :loppuetaisyys])
                             (:hoitoluokka rivi)])
                          tarkastukset)]
    (mapv (fn [tarkastusryhma]
            (let [tarkastukset (get tarkastusryhmat tarkastusryhma)
                  yhdistettava-rivi (first tarkastukset)
                  laske-kuntoarvon-summa (fn [rivit arvo]
                                           "Laskee annetun kuntoarvon summan annettujen rivien kaikista mittausluokista"
                                           (reduce
                                             (fn [nykysumma seuraava-rivi]
                                               (let [kuntoarvot ((juxt :polyavyys :tasaisuus :kiinteys) seuraava-rivi)]
                                                 (+ nykysumma (count (filter #(= % arvo) kuntoarvot)))))
                                             0
                                             rivit))
                  laatuarvot (mapv (fn [arvo]
                                     (laske-kuntoarvon-summa tarkastukset arvo))
                                   (range 1 6))]
              (-> yhdistettava-rivi
                  ; Laatuarvojen summat
                  (assoc :laatuarvo-1-summa (nth laatuarvot 0))
                  (assoc :laatuarvo-2-summa (nth laatuarvot 1))
                  (assoc :laatuarvo-3-summa (nth laatuarvot 2))
                  (assoc :laatuarvo-4-summa (nth laatuarvot 3))
                  (assoc :laatuarvo-5-summa (nth laatuarvot 4))
                  (assoc :laatuarvot-yhteensa (reduce + laatuarvot))
                  (assoc :laatuarvo-1+2-summa (reduce + [(first laatuarvot)
                                                         (second laatuarvot)]))
                  ; Laatupoikkeama
                  (assoc :laatupoikkeama (laatupoikkeama-tapahtunut? yhdistettava-rivi))
                  ; Laatuarvojen prosenttiosuudet
                  (assoc :laatuarvo-1-osuus (Math/round (laske-luvun-osuus laatuarvot 0)))
                  (assoc :laatuarvo-2-osuus (Math/round (laske-luvun-osuus laatuarvot 1)))
                  (assoc :laatuarvo-3-osuus (Math/round (laske-luvun-osuus laatuarvot 2)))
                  (assoc :laatuarvo-4-osuus (Math/round (laske-luvun-osuus laatuarvot 3)))
                  (assoc :laatuarvo-5-osuus (Math/round (laske-luvun-osuus laatuarvot 4))))))
          (keys tarkastusryhmat))))

(defn hae-tarkastukset-urakalle [db {:keys [urakka-id alkupvm loppupvm tienumero]}]
  (tarkastukset-q/hae-urakan-soratietarkastukset-raportille db
                                                            urakka-id
                                                            alkupvm
                                                            loppupvm
                                                            (not (nil? tienumero))
                                                            tienumero))

(defn hae-tarkastukset-hallintayksikolle [db {:keys [hallintayksikko-id alkupvm loppupvm tienumero]}]
  (tarkastukset-q/hae-hallintayksikon-soratietarkastukset-raportille db
                                                                     hallintayksikko-id
                                                                     alkupvm
                                                                     loppupvm
                                                                     (not (nil? tienumero))
                                                                     tienumero))

(defn hae-tarkastukset-koko-maalle [db {:keys [alkupvm loppupvm tienumero]}]
  (tarkastukset-q/hae-koko-maan-soratietarkastukset-raportille db
                                                               alkupvm
                                                               loppupvm
                                                               (not (nil? tienumero))
                                                               tienumero))

(defn hae-tarkastukset [db {:keys [konteksti urakka-id hallintayksikko-id alkupvm loppupvm tienumero]}]
  (case konteksti
    :urakka
    (hae-tarkastukset-urakalle db
                               {:urakka-id urakka-id
                                :alkupvm   alkupvm
                                :loppupvm  loppupvm
                                :tienumero tienumero})
    :hallintayksikko
    (hae-tarkastukset-hallintayksikolle db
                                        {:hallintayksikko-id hallintayksikko-id
                                         :alkupvm            alkupvm
                                         :loppupvm           loppupvm
                                         :tienumero          tienumero})
    :koko-maa
    (hae-tarkastukset-koko-maalle db
                                  {:alkupvm   alkupvm
                                   :loppupvm  loppupvm
                                   :tienumero tienumero})))



(defn suorita [db user {:keys [urakka-id hallintayksikko-id alkupvm loppupvm tienumero] :as parametrit}]
  (roolit/vaadi-rooli user "tilaajan kayttaja")
  (let [konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        tarkastukset (map konv/alaviiva->rakenne
                          (hae-tarkastukset db {:konteksti          konteksti
                                                :urakka-id          urakka-id
                                                :hallintayksikko-id hallintayksikko-id
                                                :alkupvm            alkupvm
                                                :loppupvm           loppupvm
                                                :tienumero          tienumero}))
        naytettavat-rivit (muodosta-raportin-rivit tarkastukset)
        ainakin-yksi-poikkeama? (true? (some
                                        #(not (nil? (:laatupoikkeama %)))
                                            naytettavat-rivit))
        raportin-nimi "Soratietarkastusraportti"
        otsikko (raportin-otsikko
                  (case konteksti
                    :urakka (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio db hallintayksikko-id)))
                    :koko-maa "KOKO MAA")
                  raportin-nimi alkupvm loppupvm)]
    [:raportti {:orientaatio :landscape
                :nimi        raportin-nimi}
     [:taulukko {:otsikko                    otsikko
                 :tyhja                      (if (empty? naytettavat-rivit) "Ei raportoitavia tarkastuksia.")
                 :viimeinen-rivi-yhteenveto? true}
      [{:leveys 10 :otsikko "Päi\u00ADvä\u00ADmää\u00ADrä"}
       {:leveys 5 :otsikko "Tie"}
       {:leveys 6 :otsikko "Aosa"}
       {:leveys 6 :otsikko "Aet"}
       {:leveys 6 :otsikko "Losa"}
       {:leveys 6 :otsikko "Let"}
       {:leveys 6 :otsikko "Hoi\u00ADto\u00ADluok\u00ADka"}
       {:leveys 8 :otsikko "1"}
       {:leveys 8 :otsikko "2"}
       {:leveys 8 :otsikko "3"}
       {:leveys 8 :otsikko "4"}
       {:leveys 8 :otsikko "5"}
       {:leveys 8 :otsikko "Yht"}
       {:leveys 8 :otsikko "1+2"}
       {:leveys 8 :otsikko "Laa\u00ADtu\u00ADpoik\u00ADke\u00ADa\u00ADma"}]
      (keep identity
            (conj
              (yleinen/ryhmittele-tulokset-raportin-taulukolle
                naytettavat-rivit
                :urakka
                (fn [rivi]
                  [(pvm/pvm (:aika rivi))
                   (get-in rivi [:tr :numero])
                   (get-in rivi [:tr :alkuosa])
                   (get-in rivi [:tr :alkuetaisyys])
                   (get-in rivi [:tr :loppuosa])
                   (get-in rivi [:tr :loppyetaisyys])
                   (:hoitoluokka rivi)
                   (str (:laatuarvo-1-summa rivi) " (" (:laatuarvo-1-osuus rivi) "%)")
                   (str (:laatuarvo-2-summa rivi) " (" (:laatuarvo-2-osuus rivi) "%)")
                   (str (:laatuarvo-3-summa rivi) " (" (:laatuarvo-3-osuus rivi) "%)")
                   (str (:laatuarvo-4-summa rivi) " (" (:laatuarvo-4-osuus rivi) "%)")
                   (str (:laatuarvo-5-summa rivi) " (" (:laatuarvo-5-osuus rivi) "%)")
                   (str (:laatuarvot-yhteensa rivi) " (100%)")
                   (str (:laatuarvo-1+2-summa rivi) " (" (+ (:laatuarvo-1-osuus rivi)
                                                            (:laatuarvo-2-osuus rivi)) "%)")
                   (when (:laatupoikkeama rivi)
                     (str "Kyllä" " (" (:laatupoikkeama rivi) ")"))]))
              (when (not (empty? naytettavat-rivit))
                (let [laske-laatuarvojen-kokonaissumma (fn [arvo-avain rivit]
                                                         (reduce + (mapv arvo-avain rivit)))
                      laatuarvo-summat [(laske-laatuarvojen-kokonaissumma :laatuarvo-1-summa naytettavat-rivit)
                                        (laske-laatuarvojen-kokonaissumma :laatuarvo-2-summa naytettavat-rivit)
                                        (laske-laatuarvojen-kokonaissumma :laatuarvo-3-summa naytettavat-rivit)
                                        (laske-laatuarvojen-kokonaissumma :laatuarvo-4-summa naytettavat-rivit)
                                        (laske-laatuarvojen-kokonaissumma :laatuarvo-5-summa naytettavat-rivit)]
                      laatuarvot-1+2-summa (reduce + [(first laatuarvo-summat)
                                                      (second laatuarvo-summat)])
                      laatuarvo-summat-yhteensa (reduce + laatuarvo-summat)
                      laatuarvot-1+2-osuus (if (not= laatuarvo-summat-yhteensa 0)
                                             (Math/round (* (float (/ laatuarvot-1+2-summa
                                                                      laatuarvo-summat-yhteensa)) 100))
                                             0)]

                  ["Yhteensä" nil nil nil nil nil nil
                   (str (nth laatuarvo-summat 0) " (" (Math/round (laske-luvun-osuus laatuarvo-summat 0)) "%)")
                   (str (nth laatuarvo-summat 1) " (" (Math/round (laske-luvun-osuus laatuarvo-summat 1)) "%)")
                   (str (nth laatuarvo-summat 2) " (" (Math/round (laske-luvun-osuus laatuarvo-summat 2)) "%)")
                   (str (nth laatuarvo-summat 3) " (" (Math/round (laske-luvun-osuus laatuarvo-summat 3)) "%)")
                   (str (nth laatuarvo-summat 4) " (" (Math/round (laske-luvun-osuus laatuarvo-summat 4)) "%)")
                   (str (reduce + [(first laatuarvo-summat) (second laatuarvo-summat)]) " (100%)")
                   (str laatuarvot-1+2-summa " (" laatuarvot-1+2-osuus "%)")
                   nil]))))]
     (when ainakin-yksi-poikkeama?
       [:yhteenveto
        (mapv
          (fn [avain]
            [avain (get laatupoikkeama-syyt avain)])
          (keys laatupoikkeama-syyt))])]))