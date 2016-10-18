(ns harja.palvelin.raportointi.raportit.yleinen
  (:require [clj-time
             [coerce :as c]
             [core :as t]
             [format :as tf]
             [local :as l]]
            [harja.pvm :as pvm]
            [jeesql.core :refer [defqueries]]
            [harja.tyokalut.functor :refer [fmap]]))


(defqueries "harja/palvelin/raportointi/raportit/yleinen.sql")

(defn raportin-otsikko
  [konteksti nimi alkupvm loppupvm]
  (let [kk-vali? (pvm/kyseessa-kk-vali? alkupvm loppupvm)
        kkna-ja-vuonna (pvm/kuukautena-ja-vuonna (l/to-local-date-time alkupvm))]
    (if kk-vali?
      (str konteksti ", " nimi " " kkna-ja-vuonna)
      (str konteksti ", " nimi " ajalta "
           (pvm/pvm alkupvm) " - " (pvm/pvm loppupvm)))))

(defn ryhmittele-tulokset-raportin-taulukolle
  "rivit                   ryhmiteltävät rivit
   ryhmittely-avain        avain, jonka perusteella rivit ryhmitellään. Ryhmän otsikko otetaan suoraan tästä avaimesta.
                           Jos avainta ei löydy yhdeltäkään riviltä, ei ryhmittelyä suoriteta.
   rivi-fn                 funktio, joka ottaa parametrina yhden rivin ja palauttaa sen taulukossa esitettävässä
                           muodossa eli vektorina, jossa arvot sarakkeiden mukaisessa järjestyksessä.
                           Esim. Jos taulukossa on otsikot Nimi ja Ikä, palautetaan riviksi: [Seppo, 42]"
  [rivit ryhmittely-avain rivi-fn]
  (if (every? #(nil? (ryhmittely-avain %)) rivit)
    (mapv
      rivi-fn
      rivit)
    (let [ryhmat (group-by ryhmittely-avain rivit)]
      (into [] (mapcat
                 (fn [ryhma]
                   (reduce
                     conj
                     [{:otsikko ryhma}]
                     (map
                       rivi-fn
                       (get ryhmat ryhma))))
                 (keys ryhmat))))))

(def vuosi-ja-kk-fmt (tf/with-zone (tf/formatter "YYYY/MM") (t/time-zone-for-id "EET")))
(def kk-ja-vuosi-fmt (tf/with-zone (tf/formatter "MM/YYYY") (t/time-zone-for-id "EET")))
(def kk-ja-vv-fmt (tf/with-zone (tf/formatter "MM/YY") (t/time-zone-for-id "EET")))

(defn kk-ja-vv [pvm]
  (tf/unparse kk-ja-vv-fmt (l/to-local-date-time pvm)))
(defn vuosi-ja-kk [pvm]
  (tf/unparse vuosi-ja-kk-fmt (l/to-local-date-time pvm)))

(defn kk-ja-vuosi [pvm]
  (tf/unparse kk-ja-vuosi-fmt (l/to-local-date-time pvm)))

(defn kuukaudet
  ([alku loppu] (kuukaudet alku loppu vuosi-ja-kk-fmt))
  ([alku loppu vuosi-ja-kk-fmt]
   (let [alku  (l/to-local-date-time alku)
         loppu  (l/to-local-date-time loppu)]
     (letfn [(kuukaudet [kk]
               (when-not (t/after? kk loppu)
                 (lazy-seq
                  (cons (tf/unparse vuosi-ja-kk-fmt kk)
                        (kuukaudet (t/plus kk (t/months 1)))))))]
       (kuukaudet alku)))))

(defn kuukausivalit [alku loppu]
  (let [alku (l/to-local-date-time alku)
        loppu (l/to-local-date-time loppu)]
    (letfn [(kuukaudet [kk]
              (when-not (t/after? kk loppu)
                (lazy-seq (cons (fmap c/to-date (pvm/kuukauden-aikavali kk))
                                (kuukaudet (t/plus kk (t/months 1)))))))]
      (kuukaudet alku))))

(defn pylvaat-kuukausittain
  [{:keys [otsikko alkupvm loppupvm kuukausittainen-data piilota-arvo? legend]}]
  [:pylvaat {:otsikko (str otsikko " " (pvm/pvm alkupvm) "-" (pvm/pvm loppupvm))
             :piilota-arvo? piilota-arvo? :legend legend}
   (into []
         (map (juxt identity #(or (kuukausittainen-data %)
                                  (if legend ;jos on monta sarjaa, on mukana legend: tällöin tyhjä on oltava vektori
                                    []
                                    0))))
         (kuukaudet alkupvm loppupvm))])

(defn rivit-kuukausipylvaille-summattuna
  "Muuttaa rivit kuukausivälit sisältävälle pylväsdiagrammille sopivaan muotoon.
  Tekee tämän ryhmittelemällä rivit kuukausille ja summaamalla riveiltä löytyvät arvo-avaimet.

  rivit               Käsiteltävät rivit.
  pvm-avain           Avain, josta rivin aika löytyy. Saman kuukauden rivit ryhmitellään tällä avaimella.
  arvo-avaimet        Avaimet, joiden takaa löytyvät arvot summataan yhteen.

  Lopputuloksena on map, jossa kuukaudet ovat avaimia ja arvona on vectori, jossa esiintyy summatut arvo-avaimet
  järjestyksessä:
  {2015/02 [1 2]
   2015/03 [3 4]}"
  [rivit pvm-avain & arvo-avaimet]
  (let [kuukaudet (group-by
                 (fn [rivi]
                   (pvm/kokovuosi-ja-kuukausi (c/to-date (pvm-avain rivi))))
                 rivit)]
    (reduce
      merge
      (map
        (fn [kuukausi]
          (let [kuukauden-rivit (get kuukaudet kuukausi)
                summat (mapv
                         (fn [arvo-avain]
                           (reduce + (keep arvo-avain kuukauden-rivit)))
                         arvo-avaimet)]
            {kuukausi summat}))
        (keys kuukaudet)))))

(defn rivit-kuukausipylvaille-kentan-eri-arvojen-maaraa-laskien
  "Muuttaa rivit kuukausivälit sisältävälle pylväsdiagrammille sopivaan muotoon.
  Tekee tämän ryhmittelemällä rivit kuukausille ja laskemalla, kuinka monta kertaa arvo-avaimen
  jokainen mahdollinen arvo esiintyy kuukauden aikana.

  rivit               Käsiteltävät rivit
  pvm-avain           Avain, josta rivin aika löytyy. Saman kuukauden rivit ryhmitellään tällä avaimella.
  arvo-avain          Avain, jonka takaa löytyviä arvoja tutkitaan
  mahdolliset-arvot   Mahdolliset arvot, jotka arvo-avaimen takaa löytyvät. Annetaan siinä järjestyksessä jossa
                      ne esiintyvät pylvään legendissä.

  Lopputuloksena on map, jossa kuukaudet ovat avaimia ja arvona on vectori, jossa mahdolliset-arvot laskettuna:
  {2015/02 [1 2]
   2015/03 [3 4]}"
  [rivit pvm-avain arvo-avain mahdolliset-arvot]
  (let [kuukaudet (group-by
                    (fn [rivi]
                      (pvm/kokovuosi-ja-kuukausi (c/to-date (pvm-avain rivi))))
                    rivit)]
    (reduce
      merge
      (map
        (fn [kuukausi]
          (let [kuukauden-rivit (get kuukaudet kuukausi)
                summat (mapv
                         (fn [arvo]
                           (count (filter
                                    #(= (arvo-avain %) arvo)
                                    kuukauden-rivit)))
                         mahdolliset-arvot)]
            {kuukausi summat}))
        (keys kuukaudet)))))

(defn ei-osumia-aikavalilla-teksti
  [nimi alkupvm loppupvm]
  [:otsikko-kuin-pylvaissa (str "Ei " nimi " aikana " (pvm/pvm alkupvm) "-" (pvm/pvm loppupvm))])

(defn rivi [& asiat]
  (into [] (keep identity asiat)))

(defn sarakkeiden-maara [otsikot]
  (count otsikot))

(defn naytettavat-alueet
  "Palauttaa kontekstista riippuen kaikki hallintayksiköt tai urakat, jotka voidaan löytää annetuilla raportin
   parametreilla.

   Tätä tarvitaan esim. silloin kun haetaan toteumia hallintayksiköstä,
   Normaalisti sellaisia urakoita, joilla ei ole toteumia, ei listata raportilla.
   Yleensä kuitenkin halutaan nähdä hallintayksikön kaikki urakat, myös 'nollarivit',
   joten tämän kyselyn avulla voidaan listata kaikki alueet, joita haku koskee."
  [db konteksti parametrit]
  (if (= konteksti :koko-maa)
    (into []
          (hae-kontekstin-hallintayksikot db))
    (into []
          (hae-kontekstin-urakat db parametrit))))

(defn uniikit [avain rivit]
  (into #{}
        (map avain)
        rivit))

(defn laske-uniikit [avain-fn rivit]
  (loop [lkm 0
         nahdyt (transient {})
         [rivi & rivit] rivit]
    (if-not rivi
      lkm
      (let [avain (avain-fn rivi)]
        (recur (if (nahdyt avain)
                 lkm
                 (inc lkm))
               (assoc! nahdyt avain true)
               rivit)))))

(def +erheen-vari+ "#dd0000")