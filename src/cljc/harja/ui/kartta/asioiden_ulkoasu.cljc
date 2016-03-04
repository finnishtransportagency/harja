(ns harja.ui.kartta.asioiden-ulkoasu
  (:require [harja.ui.kartta.varit.puhtaat :as puhtaat]
            [harja.ui.kartta.ikonit :refer [sijainti-ikoni pinni-ikoni nuoli-ikoni]]
            [clojure.string :as str]))

(def +valitun-skaala+ 1.5)
(def +normaali-skaala+ 1)
(def +zindex+ 4)
(def +normaali-leveys+ 6)
(def +valitun-leveys+ 8)
(def +normaali-vari+ "black")
(def +valitun-vari+ "blue")



(defn monivarinen-viiva-leveyksilla-ja-asetuksilla
  "[varit/musta 0 {} varit/punainen 2 {} varit/sininen 4 {:dash [10 10]}]

  Palauttaa monivärisen viivan. Ensimmäinen parametri kolmesta on väri, toinen on leveyden erotus
  vrt. oletusleveyteen, ja kolmas on viivan lisäasetukset."
  [& viiva-leveys-asetukset]
  (let [asetukset (partition 3 viiva-leveys-asetukset)
        viivat (mapv (fn [[vari erotus muut-asetukset]]
                       (merge {:color vari :width (- +normaali-leveys+ erotus)} muut-asetukset))
                     asetukset)
        kapein-viiva (apply min (map :width viivat))]
    (if (> kapein-viiva 1)
      viivat

      ;; Kasvatetaan kaikkien viivojen leveyttä, jos kapein viiva oli 0 tai alle
      (let [lisattava-leveys (- (- kapein-viiva 2))]
        (map
          (fn[{:keys [width] :as viiva}]
            (assoc viiva :width (+ lisattava-leveys width)))
          viivat)))))

(defn monivarinen-viiva-leveyksilla
  "[varit/musta 0 varit/punainen 2 varit/sininen 4]

  Palauttaa monivärisen viivan. Alin viiva on ensimmäisen parametrin värinen,
  n's viiva on n'nnen parametrin värinen. Joka toinen parametri määrittelee, kuinka paljon
  kapeampi viivan leveys pitäisi olla verrattuna oletusleveyteen. Jos kapein viiva on liian kapea,
  levennetään kaikkia viivoja."
  [& viiva-ja-leveyden-erotus-alternating]
  (apply monivarinen-viiva-leveyksilla-ja-asetuksilla
         (flatten (interleave (partition 2 viiva-ja-leveyden-erotus-alternating) (repeat {})))))

(defn viiva-mustalla-rajalla
  "Palauttaa kaksivärisen viivan, jossa alin viiva on oletusleveyksinen musta viiva,
  ja päällimmäinen on <väri> viiva, jonka leveys on oletus-2"
  [vari]
  (monivarinen-viiva-leveyksilla puhtaat/musta 0 vari 2))

(defn monivarinen-viiva
  "Palauttaa monivärisen viivan. Alin viiva on ensimmäisen parametrin värinen,
  n's viiva on n'nnen parametrin värinen. Viivat ovat aina 2 yksikköä kapeampia kuin edellinen.
  Levein viiva on oletusleveyden levyinen, paitsi jos kapein viiva on <2 yksikköä leveä. Tällöin kaikkia
  viivoja levennetään vaaditun verran."
  [& varit]
  (apply monivarinen-viiva-leveyksilla (interleave varit (range 0 100 2))))

;; Koneellista generointia varten!
(def toteuma-varit-ja-nuolet
  [[(viiva-mustalla-rajalla puhtaat/punainen) "punainen"]
   [(viiva-mustalla-rajalla puhtaat/oranssi) "oranssi"]
   [(viiva-mustalla-rajalla puhtaat/keltainen) "keltainen"]
   [(viiva-mustalla-rajalla puhtaat/magenta) "magenta"]
   [(viiva-mustalla-rajalla puhtaat/vihrea) "vihrea"]
   [(viiva-mustalla-rajalla puhtaat/turkoosi) "turkoosi"]
   [(viiva-mustalla-rajalla puhtaat/syaani) "syaani"]
   [(viiva-mustalla-rajalla puhtaat/sininen) "sininen"]
   [(viiva-mustalla-rajalla puhtaat/tummansininen) "tummansininen"]
   [(viiva-mustalla-rajalla puhtaat/violetti) "violetti"]
   [(viiva-mustalla-rajalla puhtaat/lime) "lime"]
   [(viiva-mustalla-rajalla puhtaat/pinkki) "pinkki"]])

;;;;;;;;;;
;;; VÄRIMÄÄRITTELYT
;;; Kaikki värit pitäisi olla määriteltynä näillä muutamilla riveillä.
;;;;;;;;;;

(def ikonien-varit
  {;; Tilallisten sijainti-ikonien sisempi väri
   :tiedoitus                  "magenta"
   :kysely                     "syaani"
   :toimenpidepyynto           "oranssi"
   :turvallisuuspoikkeama      "punainen"

   ;; tilaa osoittavat värit (sijaint-ikonin ulompi väri)
   :ilmoitus-auki              "punainen"
   :ilmoitus-kaynnissa         "musta"
   :ilmoitus-lopetettu         "harmaa"

   :kt-tyhja                   "oranssi"
   :kt-avoimia                 "punainen"
   :kt-valmis                  "vihrea"

   ;; Pienemmät ikonit (pinnit)
   :laatupoikkeama             "tummansininen"
   :laatupoikkeama-tilaaja     "tummansininen"
   :laatupoikkeama-konsultti   "tummansininen"
   :laatupoikkeama-urakoitsija "sininen"
   :tarkastus                  "punainen"
   :varustetoteuma             "violetti"
   :yllapito                   "pinkki"})

(def viivojen-varit
  {:yllapito-aloitettu puhtaat/keltainen
   :yllapito-valmis puhtaat/lime
   :yllapito-muu puhtaat/syaani
   :yllapito-pohja puhtaat/musta
   :yllapito-katkoviiva puhtaat/tummanharmaa

   :ok-tarkastus puhtaat/musta
   :ei-ok-tarkastus puhtaat/punainen})


(def auraus-tasaus-ja-kolmas [(monivarinen-viiva-leveyksilla puhtaat/musta 0 puhtaat/oranssi 2 puhtaat/violetti 6) "oranssi"])
(def auraus-ja-hiekoitus [(monivarinen-viiva-leveyksilla puhtaat/musta 0 puhtaat/oranssi 2 puhtaat/pinkki 6) "oranssi"])
(def auraus-ja-suolaus [(monivarinen-viiva-leveyksilla puhtaat/musta 0 puhtaat/oranssi 2 puhtaat/syaani 6) "oranssi"])

;; Mäppi muotoa
;; {#{"tehtävän nimi"} ["viivan väri" "nuolen tiedoston nimi"]
;;  #{"foo"}           [{:color "väri"} "tiedoston nimi"]
;;  #{"bar"}           [[{:color "b" :width 10} {:color "r" :width 6}] "tiedoston nimi"]}
;; Avaimet ovat settejä, koska yhdistelmätoimenpiteille halutaan tehdä omat suunnitellut tyylit.
;; Näissä tapauksissa tehtävät tulevat vektorissa, eikä tietenkään kannata luottaa järjestykseen
(def tehtavien-varit
  {;; yhdistelmätoimenpiteet
   #{"AURAUS JA SOHJONPOISTO" "PINNAN TASAUS" "PISTEHIEKOITUS"} auraus-tasaus-ja-kolmas
   #{"AURAUS JA SOHJONPOISTO" "PINNAN TASAUS" "LINJAHIEKOITUS"} auraus-tasaus-ja-kolmas
   #{"AURAUS JA SOHJONPOISTO" "PINNAN TASAUS" "SUOLAUS"}        auraus-tasaus-ja-kolmas
   #{"AURAUS JA SOHJONPOISTO" "PINNAN TASAUS" "LIUOSSUOLAUS"}   auraus-tasaus-ja-kolmas
   #{"AURAUS JA SOHJONPOISTO" "PISTEHIEKOITUS"}                 auraus-ja-hiekoitus
   #{"AURAUS JA SOHJONPOISTO" "LINJAHIEKOITUS"}                 auraus-ja-hiekoitus
   #{"AURAUS JA SOHJONPOISTO" "SUOLAUS"}                        auraus-ja-suolaus
   #{"AURAUS JA SOHJONPOISTO" "LIUOSSUOLAUS"}                   auraus-ja-suolaus
   ;; tilannekuva/talvihoito
   #{"AURAUS JA SOHJONPOISTO"}                                  [(viiva-mustalla-rajalla puhtaat/oranssi) "oranssi"]
   #{"SUOLAUS"}                                                 [(viiva-mustalla-rajalla puhtaat/syaani) "syaani"]
   #{"LIUOSSUOLAUS"}                                            [(viiva-mustalla-rajalla puhtaat/tummansininen) "tummansininen"]
   #{"PISTEHIEKOITUS"}                                          [(viiva-mustalla-rajalla puhtaat/pinkki) "pinkki"]
   #{"LINJAHIEKOITUS"}                                          [(viiva-mustalla-rajalla puhtaat/magenta) "magenta"]
   #{"PINNAN TASAUS"}                                           [(viiva-mustalla-rajalla puhtaat/violetti) "violetti"]
   #{"LUMIVALLIEN MADALTAMINEN"}                                [(viiva-mustalla-rajalla puhtaat/punainen) "punainen"]
   #{"SULAMISVEDEN HAITTOJEN TORJUNTA"}                         [(viiva-mustalla-rajalla puhtaat/keltainen) "keltainen"]
   #{"AURAUSVIITOITUS JA KINOSTIMET"}                           [(viiva-mustalla-rajalla puhtaat/lime) "lime"]
   #{"LUMENSIIRTO"}                                             [(viiva-mustalla-rajalla puhtaat/sininen) "sininen"]
   #{"PAANNEJAAN POISTO"}                                       [(viiva-mustalla-rajalla puhtaat/turkoosi) "turkoosi"]
   #{"MUU"}                                                     [(viiva-mustalla-rajalla puhtaat/lime) "lime"]
   ;; tilannekuva/kesähoito
   #{"SORATEIDEN POLYNSIDONTA"}                                 [(viiva-mustalla-rajalla puhtaat/oranssi) "oranssi"]
   #{"SORASTUS"}                                                [(viiva-mustalla-rajalla puhtaat/syaani) "syaani"]
   #{"SORATEIDEN TASAUS"}                                       [(viiva-mustalla-rajalla puhtaat/tummansininen) "tummansininen"]
   #{"SORATEIDEN MUOKKAUSHOYLAYS"}                              [(viiva-mustalla-rajalla puhtaat/pinkki) "pinkki"]
   #{"PAALLYSTEIDEN PAIKKAUS"}                                  [(viiva-mustalla-rajalla puhtaat/magenta) "magenta"]
   #{"PAALLYSTEIDEN JUOTOSTYOT"}                                [(viiva-mustalla-rajalla puhtaat/violetti) "violetti"]
   #{"KONEELLINEN NIITTO"}                                      [(viiva-mustalla-rajalla puhtaat/punainen) "punainen"]
   #{"KONEELLINEN VESAKONRAIVAUS"}                              [(viiva-mustalla-rajalla puhtaat/keltainen) "keltainen"]
   #{"HARJAUS"}                                                 [(viiva-mustalla-rajalla puhtaat/lime) "lime"]
   #{"LIIKENNEMERKKIEN PUHDISTUS"}                              [(viiva-mustalla-rajalla puhtaat/sininen) "sininen"]
   #{"L- JA P-ALUEIDEN PUHDISTUS"}                              [(viiva-mustalla-rajalla puhtaat/turkoosi) "turkoosi"]
   #{"SILTOJEN PUHDISTUS"}                                      [(viiva-mustalla-rajalla puhtaat/lime) "lime"]})

;;;;;;;;;;
;;; Värimäärittelyt loppuu
;;;;;;;;;;

;; Toteuman ja työkoneen käyttämät värit määritellään tehtavien-varit mäpissä.
(defn toteuman-ikoni [vari]
  (pinni-ikoni vari))

(defn toteuman-nuoli [nuolen-vari]
  {:paikka [:taitokset :loppu]
   :tyyppi :nuoli
   :img    (nuoli-ikoni nuolen-vari)})

(defn tyokoneen-ikoni [nuolen-vari rotaatio]
  {:paikka   [:loppu]
   :tyyppi   :nuoli
   :img      (nuoli-ikoni nuolen-vari)
   :rotation rotaatio})

(defn yllapidon-ikoni []
  {:paikka [:loppu]
   :tyyppi :merkki
   :img (:yllapito ikonien-varit)})

(defn yllapidon-viiva [valittu? avoin? tila tyyppi]
  (let [;; Pohjimmaisen viivan leveys on X, ja seuraavien viivojen leveys on aina 2 kapeampi.
        leveydet (range (cond
                          (and valittu? avoin?) (+ 2 +valitun-leveys+)
                          avoin? (+ 2 +normaali-leveys+)
                          valittu? +valitun-leveys+
                          :else +normaali-leveys+) 0 -2)
        tila (if (keyword? tila)
               tila
               (keyword (str/lower-case (or tila "muu"))))]
    [{:color (:yllapito-pohja viivojen-varit)
     :width (nth leveydet 0)}
     {:color (case tila
               :aloitettu (:yllapito-aloitettu viivojen-varit)
               :valmis (:yllapito-valmis viivojen-varit)
               (:yllapito-muu viivojen-varit))
      :width (nth leveydet 1)}
     {:color (:yllapito-katkoviiva viivojen-varit)
      :dash (if (= tyyppi :paikkaus) [3 9] [10 5])
      :width (nth leveydet 2)}]))

(defn turvallisuuspoikkeaman-ikoni [kt-tila]
  (sijainti-ikoni (:turvallisuuspoikkeama ikonien-varit)
                  (case kt-tila
                    :tyhja (:kt-tyhja ikonien-varit)
                    :avoimia (:kt-avoimia ikonien-varit)
                    :valmis (:kt-valmis ikonien-varit))))

(defn varustetoteuman-ikoni []
  (pinni-ikoni (:varustetoteuma ikonien-varit)))

(defn tarkastuksen-ikoni [valittu? ok? reitti?]
  (cond
    reitti? nil
    (not ok?) (pinni-ikoni (:tarkastus ikonien-varit))
    (and valittu? ok?) (pinni-ikoni (:tarkastus ikonien-varit)))) ;; Ei näytetä ok-tarkastuksia jos ei ole valittu

(defn tarkastuksen-reitti [ok?]
  (if ok? {:color (:ok-tarkastus viivojen-varit)
           :width 2}
          {:color (:ei-ok-tarkastus viivojen-varit)}))

(defn laatupoikkeaman-ikoni [tekija]
  (pinni-ikoni (case tekija
                 :tilaaja (:laatupoikkeama-tilaaja ikonien-varit)
                 :konsultti (:laatupoikkeama-konsultti ikonien-varit)
                 :urakoitsija (:laatupoikkeama-urakoitsija ikonien-varit)
                 (:laatupoikkeama ikonien-varit))))

(defn kyselyn-ikoni [tila]
  (case tila
    :kuittaamaton (sijainti-ikoni (:kysely ikonien-varit) (:ilmoitus-kaynnissa ikonien-varit))
    :vastaanotto (sijainti-ikoni (:kysely ikonien-varit) (:ilmoitus-kaynnissa ikonien-varit))
    :aloitus (sijainti-ikoni (:kysely ikonien-varit) (:ilmoitus-kaynnissa ikonien-varit))
    :lopetus (sijainti-ikoni (:kysely ikonien-varit) (:ilmoitus-lopetettu ikonien-varit))))

(defn toimenpidepyynnon-ikoni [tila]
  (case tila
    :kuittaamaton (sijainti-ikoni (:toimenpidepyynto ikonien-varit) (:ilmoitus-auki ikonien-varit))
    :vastaanotto (sijainti-ikoni (:toimenpidepyynto ikonien-varit) (:ilmoitus-kaynnissa ikonien-varit))
    :aloitus (sijainti-ikoni (:toimenpidepyynto ikonien-varit) (:ilmoitus-kaynnissa ikonien-varit))
    :lopetus (sijainti-ikoni (:toimenpidepyynto ikonien-varit) (:ilmoitus-lopetettu ikonien-varit))))


(defn tiedotuksen-ikoni [tila]
  (case tila
    :kuittaamaton (sijainti-ikoni (:tiedoitus ikonien-varit) (:ilmoitus-auki ikonien-varit))
    :vastaanotto (sijainti-ikoni (:tiedoitus ikonien-varit) (:ilmoitus-auki ikonien-varit))
    :aloitus (sijainti-ikoni (:tiedoitus ikonien-varit) (:ilmoitus-kaynnissa ikonien-varit))
    :lopetus (sijainti-ikoni (:tiedoitus ikonien-varit) (:ilmoitus-lopetettu ikonien-varit))))

(defn ilmoituksen-ikoni [{:keys [ilmoitustyyppi tila] :as ilmoitus}]
  (case ilmoitustyyppi
    :kysely (kyselyn-ikoni tila)
    :toimenpidepyynto (toimenpidepyynnon-ikoni tila)
    :tiedoitus (tiedotuksen-ikoni tila)))

(def ^{:doc "TR-valinnan viivatyyli"}
  tr-viiva {:color  puhtaat/tummanharmaa
            :dash [15 15]
            :zindex 20})

(def ^{:doc "TR-valinnan ikoni"}
  tr-ikoni {:img    (pinni-ikoni "musta")
            :zindex 21})
