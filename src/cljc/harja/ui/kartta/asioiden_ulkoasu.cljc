(ns harja.ui.kartta.asioiden-ulkoasu
  (:require [harja.ui.kartta.varit.puhtaat :as puhtaat]
            [harja.ui.kartta.ikonit :refer [sijainti-ikoni pinni-ikoni nuoli-ikoni]]
            [clojure.string :as str]

            [harja.domain.laadunseuranta.tarkastus :as domain-tarkastukset]
            [harja.domain.vesivaylat.turvalaite :as tu]
            [harja.domain.laadunseuranta.tarkastus :as tarkastus-domain]
            [harja.domain.kanavat.kohde :as kohde]
            [harja.domain.kanavat.kohteenosa :as osa]
            [harja.domain.kanavat.hairiotilanne :as ht]
            [harja.domain.tielupa :as tielupa]
            [harja.ui.kartta.varit :as varit]))

(def +valitun-skaala+ 1.5)
(def +normaali-skaala+ 1)
(def +zindex+ 4)
(def +normaali-leveys+ 6)
(def +valitun-leveys+ 8)
(def +normaali-vari+ "black")
(def +valitun-vari+ "blue")
(def +tyokoneviivan-dash+ [5 10])


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
          (fn [{:keys [width] :as viiva}]
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
  (monivarinen-viiva-leveyksilla puhtaat/musta-raja 0 vari 2))

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

;; Merkkijono, koska tämä on osa tiedoston nimeä
(def tiepuolen-ikonien-varit
  {;; Tilallisten sijainti-ikonien ulompi väri
   :tiedoitus "oranssi"
   :kysely "syaani"
   :toimenpidepyynto "punainen"
   :turvallisuuspoikkeama "magenta"
   :suolatoteuma "harmaa"

   ;; tilaa osoittavat värit (sijaint-ikonin sisempi väri)
   :ilmoitus-auki "punainen"
   :ilmoitus-kaynnissa "sininen"
   :ilmoitus-lopetettu "vihrea"

   ;; Turpon ikonin tila tulee korjaavien toimenpiteiden mukaan (sisempi väri)
   :kt-tyhja "oranssi"
   :kt-avoimia "punainen"
   :kt-valmis "vihrea"

   ;; Pienemmät ikonit (pinnit)
   :laatupoikkeama "violetti"
   :laatupoikkeama-tilaaja "violetti"
   :laatupoikkeama-konsultti "violetti"
   :laatupoikkeama-urakoitsija "magenta"
   :ok-tarkastus "sininen"
   :ok-tarkastus-tilaaja "sininen"
   :ok-tarkastus-konsultti "sininen"
   :ok-tarkastus-urakoitsija "tummansininen"
   :ei-ok-tarkastus "punainen"
   :ei-ok-tarkastus-tilaaja "punainen"
   :ei-ok-tarkastus-konsultti "punainen"
   :ei-ok-tarkastus-urakoitsija "oranssi"
   :tarkastus-vakiohavainnolla "keltainen"
   :varustetoteuma "tummansininen"
   :varuste "oranssi"
   :yllapito "pinkki"

   :tietyoilmoitus "oranssi"})

(def vesivaylien-ikonien-varit
  {"merimajakka" "magenta"
   "muu merkki" "oranssi"
   "poiju" "keltainen"
   "reunamerkki" "lime"
   "sektoriloisto" "vihrea"
   "suuntaloisto" "vaaleanharmaa"
   "tunnusmajakka" "punainen"
   "tutkamerkki" "violetti"
   "viitta" {true "tummansininen"
             false "sininen"}
   "apuloisto" "pinkki"
   "kummeli" "syaani"
   "linjamerkki" "turkoosi"})

(defn turvalaitteiden-varit [tyyppi kiintea?]
  (or (let [vari (get vesivaylien-ikonien-varit (when tyyppi (str/lower-case tyyppi)))]
        (if (map? vari) (get vari kiintea?) vari))
      "musta"))

(def tiepuolen-viivojen-varit
  {:yllapito-aloitettu puhtaat/keltainen
   :yllapito-valmis puhtaat/lime
   :yllapito-muu puhtaat/tummansininen
   :yllapito-pohja puhtaat/musta
   :yllapito-katkoviiva puhtaat/tummanharmaa

   :laatupoikkeama puhtaat/violetti
   :laatupoikkeama-tilaaja puhtaat/violetti
   :laatupoikkeama-konsultti puhtaat/violetti
   :laatupoikkeama-urakoitsija puhtaat/magenta

   :ok-tarkastus puhtaat/sininen
   :ok-tarkastus-tilaaja puhtaat/sininen
   :ok-tarkastus-konsultti puhtaat/sininen
   :ok-tarkastus-urakoitsija puhtaat/tummansininen
   :ei-ok-tarkastus puhtaat/punainen
   :ei-ok-tarkastus-tilaaja puhtaat/punainen
   :ei-ok-tarkastus-konsultti puhtaat/punainen
   :ei-ok-tarkastus-urakoitsija puhtaat/oranssi})


(def auraus-tasaus-ja-kolmas [(monivarinen-viiva-leveyksilla puhtaat/musta-raja 0 puhtaat/oranssi 2 puhtaat/violetti 6) "oranssi"])
(def auraus-ja-hiekoitus [(monivarinen-viiva-leveyksilla puhtaat/musta-raja 0 puhtaat/oranssi 2 puhtaat/pinkki 6) "oranssi"])
(def auraus-ja-suolaus [(monivarinen-viiva-leveyksilla puhtaat/musta-raja 0 puhtaat/oranssi 2 puhtaat/syaani 6) "oranssi"])

(defn tarkastus-vakiohavainnolla
  [kantavari]
  (monivarinen-viiva-leveyksilla-ja-asetuksilla puhtaat/musta-raja 0 {} kantavari 2 {} puhtaat/keltainen 6 {:dash [10 10]}))

(defn tarkastus-vakiohavainnolla-luminen-tai-liukas
  [kantavari]
  (monivarinen-viiva-leveyksilla-ja-asetuksilla puhtaat/musta-raja 0 {} kantavari 2 {} puhtaat/valkoinen 6 {:dash [10 10]}))

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
   #{"AURAUS JA SOHJONPOISTO" "PINNAN TASAUS" "SUOLAUS"} auraus-tasaus-ja-kolmas
   ;; Liuossuolausta ei ymmärtääkseni enää seurata, mutta kesälomien takia tässä on korjauksen
   ;; hetkellä pieni informaatiouupelo. Nämä rivit voi poistaa tulevaisuudessa, jos lukija
   ;; kokee tietävänsä asian varmaksi.
   ;;#{"AURAUS JA SOHJONPOISTO" "PINNAN TASAUS" "LIUOSSUOLAUS"}   auraus-tasaus-ja-kolmas
   #{"AURAUS JA SOHJONPOISTO" "PISTEHIEKOITUS"} auraus-ja-hiekoitus
   #{"AURAUS JA SOHJONPOISTO" "LINJAHIEKOITUS"} auraus-ja-hiekoitus
   #{"AURAUS JA SOHJONPOISTO" "SUOLAUS"} auraus-ja-suolaus
   ;; Liuossuolausta ei ymmärtääkseni enää seurata, mutta kesälomien takia tässä on korjauksen
   ;; hetkellä pieni informaatiouupelo. Nämä rivit voi poistaa tulevaisuudessa, jos lukija
   ;; kokee tietävänsä asian varmaksi.
   ;;#{"AURAUS JA SOHJONPOISTO" "LIUOSSUOLAUS"}                   auraus-ja-suolaus
   ;; tilannekuva/talvihoito
   #{"AURAUS JA SOHJONPOISTO"} [(viiva-mustalla-rajalla puhtaat/oranssi) "oranssi"]
   #{"SUOLAUS"} [(viiva-mustalla-rajalla puhtaat/syaani) "syaani"]
   ;; Liuossuolausta ei ymmärtääkseni enää seurata, mutta kesälomien takia tässä on korjauksen
   ;; hetkellä pieni informaatiouupelo. Nämä rivit voi poistaa tulevaisuudessa, jos lukija
   ;; kokee tietävänsä asian varmaksi.
   ;;#{"LIUOSSUOLAUS"}                                            [(viiva-mustalla-rajalla puhtaat/tummansininen) "tummansininen"]
   #{"PISTEHIEKOITUS"} [(viiva-mustalla-rajalla puhtaat/pinkki) "pinkki"]
   #{"LINJAHIEKOITUS"} [(viiva-mustalla-rajalla puhtaat/magenta) "magenta"]
   #{"PINNAN TASAUS"} [(viiva-mustalla-rajalla puhtaat/violetti) "violetti"]
   #{"LUMIVALLIEN MADALTAMINEN"} [(viiva-mustalla-rajalla puhtaat/punainen) "punainen"]
   #{"SULAMISVEDEN HAITTOJEN TORJUNTA"} [(viiva-mustalla-rajalla puhtaat/keltainen) "keltainen"]
   #{"AURAUSVIITOITUS JA KINOSTIMET"} [(viiva-mustalla-rajalla puhtaat/lime) "lime"]
   #{"LUMENSIIRTO"} [(viiva-mustalla-rajalla puhtaat/sininen) "sininen"]
   #{"LUMEN SIIRTO"} [(viiva-mustalla-rajalla puhtaat/sininen) "sininen"]
   #{"PAANNEJAAN POISTO"} [(viiva-mustalla-rajalla puhtaat/turkoosi) "turkoosi"]
   #{"MUU"} [(viiva-mustalla-rajalla puhtaat/lime) "lime"]
   ;; tilannekuva/kesähoito
   #{"SORATEIDEN PÖLYNSIDONTA"} [(viiva-mustalla-rajalla puhtaat/oranssi) "oranssi"]
   #{"SORASTUS"} [(viiva-mustalla-rajalla puhtaat/syaani) "syaani"]
   #{"SORASTUS KM"} [(viiva-mustalla-rajalla puhtaat/syaani) "syaani"]
   #{"SORATEIDEN TASAUS"} [(viiva-mustalla-rajalla puhtaat/tummansininen) "tummansininen"]
   #{"SORATEIDEN MUOKKAUSHÖYLÄYS"} [(viiva-mustalla-rajalla puhtaat/pinkki) "pinkki"]
   #{"PÄÄLLYSTEIDEN PAIKKAUS"} [(viiva-mustalla-rajalla puhtaat/magenta) "magenta"]
   #{"PÄÄLLYSTEIDEN JUOTOSTYÖT"} [(viiva-mustalla-rajalla puhtaat/violetti) "violetti"]
   #{"KONEELLINEN NIITTO"} [(viiva-mustalla-rajalla puhtaat/punainen) "punainen"]
   #{"KONEELLINEN VESAKONRAIVAUS"} [(viiva-mustalla-rajalla puhtaat/keltainen) "keltainen"]
   #{"HARJAUS"} [(viiva-mustalla-rajalla puhtaat/lime) "lime"]
   #{"LIIKENNEMERKKIEN PUHDISTUS"} [(viiva-mustalla-rajalla puhtaat/sininen) "sininen"]
   #{"L- JA P-ALUEIDEN PUHDISTUS"} [(viiva-mustalla-rajalla puhtaat/turkoosi) "turkoosi"]
   #{"SILTOJEN PUHDISTUS"} [(viiva-mustalla-rajalla puhtaat/lime) "lime"]
   ;; tilannekuva/yllapito
   #{"ASFALTOINTI"} [(viiva-mustalla-rajalla puhtaat/musta-raja) "musta"]
   #{"TIEMERKINTÄ"} [(viiva-mustalla-rajalla puhtaat/keltainen) "keltainen"]
   #{"KUUMENNUS"} [(viiva-mustalla-rajalla puhtaat/punainen) "punainen"]
   #{"SEKOITUS TAI STABILOINTI"} [(viiva-mustalla-rajalla puhtaat/vihrea) "vihrea"]
   #{"TURVALAITE"} [(viiva-mustalla-rajalla puhtaat/oranssi) "oranssi"]
   #{"JYRAYS"} [(viiva-mustalla-rajalla puhtaat/magenta) "magenta"]
   #{"TILAAJAN LAADUNVALVONTA"} [(viiva-mustalla-rajalla puhtaat/pinkki) "pinkki"]})

;;;;;;;;;;
;;; Värimäärittelyt loppuu
;;;;;;;;;;

;; Toteuman ja työkoneen käyttämät värit määritellään tehtavien-varit mäpissä.
(defn toteuman-ikoni [vari]
  (pinni-ikoni vari))

(defn toteuman-nuoli [nuolen-vari]
  [{:paikka [:loppu]
    :tyyppi :nuoli
    :img (nuoli-ikoni nuolen-vari)}
   {:paikka [:taitokset]
    :scale 0.8
    :tyyppi :nuoli
    :img (nuoli-ikoni "musta")}])

(defn tyokoneen-nuoli [nuolen-vari]
  (toteuman-nuoli nuolen-vari))

(defn yllapidon-viiva [valittu? tila tyyppi]
  (let [;; Pohjimmaisen viivan leveys on X, ja seuraavien viivojen leveys on aina 2 kapeampi.
        leveydet (range (cond
                          valittu? +valitun-leveys+
                          :else +normaali-leveys+) 0 -2)]
    [{:color (:yllapito-pohja tiepuolen-viivojen-varit)
      :width (nth leveydet 0)}
     {:color (case tila
               :kesken (:yllapito-aloitettu tiepuolen-viivojen-varit)
               :valmis (:yllapito-valmis tiepuolen-viivojen-varit)
               (:yllapito-muu tiepuolen-viivojen-varit))
      :width (nth leveydet 1)}
     {:color (:yllapito-katkoviiva tiepuolen-viivojen-varit)
      :dash (if (= tyyppi :paikkaus) [3 9] [10 5])
      :width (nth leveydet 2)}]))

(defn turvallisuuspoikkeaman-ikoni [kt-tila]
  (sijainti-ikoni (case kt-tila
                    :tyhja (:kt-tyhja tiepuolen-ikonien-varit)
                    :avoimia (:kt-avoimia tiepuolen-ikonien-varit)
                    :valmis (:kt-valmis tiepuolen-ikonien-varit))
                  (:turvallisuuspoikkeama tiepuolen-ikonien-varit)))

(defn turvalaitteen-ikoni-ja-selite [turvalaite]
  [(pinni-ikoni (turvalaitteiden-varit (::tu/tyyppi turvalaite)
                                       (::tu/kiintea turvalaite)))
   (str/capitalize
     (str (when (and (= (::tu/tyyppi turvalaite) "viitta")
                    (::tu/kiintea turvalaite))
           "Kiinteä ")
         (::tu/tyyppi turvalaite)))])


(defn suolatoteuman-nuoli [valittu?]
  [{:paikka [:loppu]
    :tyyppi :nuoli
    :img (nuoli-ikoni "musta")}
   {:paikka [:taitokset]
    :scale 0.8
    :tyyppi :nuoli
    :img (nuoli-ikoni "musta")}])

(defn varustetoteuman-ikoni []
  (pinni-ikoni (:varustetoteuma tiepuolen-ikonien-varit)))

(defn varusteen-ikoni []
  (pinni-ikoni (:varuste tiepuolen-ikonien-varit)))

(defn tietyoilmoituksen-ikoni []
  (pinni-ikoni (:tietyoilmoitus tiepuolen-ikonien-varit)))

(defn tietyoilmoituksen-viiva []
  [{:color puhtaat/musta-raja
    :width 8}
   {:color puhtaat/oranssi
    :width 6}
   {:color puhtaat/musta-raja
    :dash [3 9]
    :width 4}
   {:color puhtaat/musta-raja
    :dash [3 9]
    :width 3}])

(defn suolatoteuman-viiva []
  [{:color puhtaat/musta-raja
    :width 10}
   {:color puhtaat/harmaa
    :width 8}
   {:color puhtaat/musta-raja
    :dash [3 9]
    :width 6}
   {:color puhtaat/sininen
    :dash [3 9]
    :width 5}])

(defn tarkastuksen-ikoni [{:keys [ok? tekija] :as tarkastus} reitti?]
  (cond
    reitti? nil
    (not ok?)
    (pinni-ikoni (case tekija
                   :tilaaja (:ei-ok-tarkastus-tilaaja tiepuolen-ikonien-varit)
                   :konsultti (:ei-ok-tarkastus-konsultti tiepuolen-ikonien-varit)
                   :urakoitsija (:ei-ok-tarkastus-urakoitsija tiepuolen-ikonien-varit)
                   (:ei-ok-tarkastus tiepuolen-ikonien-varit)))

    (tarkastus-domain/tarkastus-sisaltaa-havaintoja? tarkastus)
    (pinni-ikoni (:tarkastus-vakiohavainnolla tiepuolen-ikonien-varit))

    ok?
    (pinni-ikoni (case tekija
                   :tilaaja (:ok-tarkastus-tilaaja tiepuolen-ikonien-varit)
                   :konsultti (:ok-tarkastus-konsultti tiepuolen-ikonien-varit)
                   :urakoitsija (:ok-tarkastus-urakoitsija tiepuolen-ikonien-varit)
                   (:ok-tarkastus tiepuolen-ikonien-varit)))))

(defn tarkastuksen-reitti [{:keys [ok? tekija] :as tarkastus}]
  (if-not ok? ;;laadunalitus
    {:color (case tekija
              :tilaaja (:ei-ok-tarkastus-tilaaja tiepuolen-viivojen-varit)
              :konsultti (:ei-ok-tarkastus-konsultti tiepuolen-viivojen-varit)
              :urakoitsija (:ei-ok-tarkastus-urakoitsija tiepuolen-viivojen-varit)
              (:ei-ok-tarkastus tiepuolen-viivojen-varit))}
    (if (domain-tarkastukset/tarkastus-sisaltaa-havaintoja? tarkastus)
      ;; on vakiohavaintoja. Erikoiskeissi lumista tai liukasta.
      (if (domain-tarkastukset/luminen-tai-liukas-vakiohavainto? tarkastus)
        (tarkastus-vakiohavainnolla-luminen-tai-liukas (case tekija
                                                         :tilaaja (:ok-tarkastus-tilaaja tiepuolen-viivojen-varit)
                                                         :konsultti (:ok-tarkastus-konsultti tiepuolen-viivojen-varit)
                                                         :urakoitsija (:ok-tarkastus-urakoitsija tiepuolen-viivojen-varit)
                                                         (:ok-tarkastus tiepuolen-viivojen-varit)))
        (tarkastus-vakiohavainnolla (case tekija
                                      :tilaaja (:ok-tarkastus-tilaaja tiepuolen-viivojen-varit)
                                      :konsultti (:ok-tarkastus-konsultti tiepuolen-viivojen-varit)
                                      :urakoitsija (:ok-tarkastus-urakoitsija tiepuolen-viivojen-varit)
                                      (:ok-tarkastus tiepuolen-viivojen-varit))))
      ;; kaikki OK
      {:color (case tekija
                :tilaaja (:ok-tarkastus-tilaaja tiepuolen-viivojen-varit)
                :konsultti (:ok-tarkastus-konsultti tiepuolen-viivojen-varit)
                :urakoitsija (:ok-tarkastus-urakoitsija tiepuolen-viivojen-varit)
                (:ok-tarkastus tiepuolen-viivojen-varit))})))

(defn laatupoikkeaman-ikoni [tekija]
  (pinni-ikoni (case tekija
                 :tilaaja (:laatupoikkeama-tilaaja tiepuolen-ikonien-varit)
                 :konsultti (:laatupoikkeama-konsultti tiepuolen-ikonien-varit)
                 :urakoitsija (:laatupoikkeama-urakoitsija tiepuolen-ikonien-varit)
                 (:laatupoikkeama tiepuolen-ikonien-varit))))

(defn laatupoikkeaman-reitti [tekija]
  {:color (case tekija
            :tilaaja (:laatupoikkeama-tilaaja tiepuolen-viivojen-varit)
            :konsultti (:laatupoikkeama-konsultti tiepuolen-viivojen-varit)
            :urakoitsija (:laatupoikkeama-urakoitsija tiepuolen-viivojen-varit)
            (:laatupoikkeama tiepuolen-viivojen-varit))})

(defn kyselyn-ikoni [tila]
  (sijainti-ikoni
    (case tila
      :kuittaamaton (:ilmoitus-kaynnissa tiepuolen-ikonien-varit)
      :vastaanotettu (:ilmoitus-kaynnissa tiepuolen-ikonien-varit)
      :aloitettu (:ilmoitus-kaynnissa tiepuolen-ikonien-varit)
      :lopetettu (:ilmoitus-lopetettu tiepuolen-ikonien-varit))
    (:kysely tiepuolen-ikonien-varit)))

(defn toimenpidepyynnon-ikoni [tila]
  (sijainti-ikoni
    (case tila
      :kuittaamaton (:ilmoitus-auki tiepuolen-ikonien-varit)
      :vastaanotettu (:ilmoitus-kaynnissa tiepuolen-ikonien-varit)
      :aloitettu (:ilmoitus-kaynnissa tiepuolen-ikonien-varit)
      :lopetettu (:ilmoitus-lopetettu tiepuolen-ikonien-varit))
    (:toimenpidepyynto tiepuolen-ikonien-varit)))


(defn tiedotuksen-ikoni [tila]
  (sijainti-ikoni
    (case tila
      :kuittaamaton (:ilmoitus-auki tiepuolen-ikonien-varit)
      :vastaanotettu (:ilmoitus-kaynnissa tiepuolen-ikonien-varit)
      :aloitettu (:ilmoitus-kaynnissa tiepuolen-ikonien-varit)
      :lopetettu (:ilmoitus-lopetettu tiepuolen-ikonien-varit))
    (:tiedoitus tiepuolen-ikonien-varit)))

(defn ilmoituksen-ikoni [{:keys [ilmoitustyyppi tila] :as ilmoitus}]
  (case ilmoitustyyppi
    :kysely (kyselyn-ikoni tila)
    :toimenpidepyynto (toimenpidepyynnon-ikoni tila)
    :tiedoitus (tiedotuksen-ikoni tila)))

(def ^{:doc "TR-valinnan viivatyyli"}
tr-viiva {:color puhtaat/tummanharmaa
          :dash [15 15]
          :zindex 20})

(def ^{:doc "TR-valinnan ikoni"}
tr-ikoni {:img (pinni-ikoni "musta")
          :zindex 21})

(def tietyomaa
  [{:color puhtaat/musta-raja
    :width 8}
   {:color puhtaat/punainen
    :width 6}
   {:color puhtaat/musta-raja
    :dash [3 9]
    :width 4}
   {:color puhtaat/keltainen
    :dash [3 9]
    :width 3}])

(def paikkaukset
  [{:color puhtaat/musta-raja
    :width 8}
   {:color puhtaat/vihrea
    :width 6}])

(defn tehtavan-viivat-tyokoneelle [viivat]
  (let [levein (or (apply max (map :width viivat)) +normaali-leveys+)]
    (concat
      ;; Näiden pohjalle tulevien viivojen pitää olla leveämpiä
      ;; kuin "varsinaisen viivan", sillä palvelinpäässä viivat piirretään
      ;; leveimmästä kapeimpaan, jolloin osa katkoviivasta voi peittyä.
      [{:width (+ 2 levein) :color puhtaat/musta-raja}
       {:width (+ 1 levein) :color puhtaat/harmaa}]
      (mapv #(assoc % :dash +tyokoneviivan-dash+) viivat))))

(def kohteenosa-pinni-varit
  {#{:sama-kohde :silta}
   ["turkoosi" (str "Kohteeseen kuuluva " (osa/fmt-kohteenosa-tyyppi :silta))]
   #{:sama-kohde :rautatiesilta}
   ["lime" (str "Kohteeseen kuuluva " (osa/fmt-kohteenosa-tyyppi :rautatiesilta))]
   #{:sama-kohde :sulku}
   ["vihrea" (str "Kohteeseen kuuluva " (osa/fmt-kohteenosa-tyyppi :sulku))]

   #{:varattu :silta}
   ["pinkki" (str "Toisen kohteen " (osa/fmt-kohteenosa-tyyppi :silta))]
   #{:varattu :rautatiesilta}
   ["punainen" (str "Toisen kohteen " (osa/fmt-kohteenosa-tyyppi :rautatiesilta))]
   #{:varattu :sulku}
   ["oranssi" (str "Toisen kohteen " (osa/fmt-kohteenosa-tyyppi :sulku))]

   #{:vapaa :silta}
   ["syaani" (str "Vapaa " (osa/fmt-kohteenosa-tyyppi :silta))]
   #{:vapaa :rautatiesilta}
   ["sininen" (str "Vapaa " (osa/fmt-kohteenosa-tyyppi :rautatiesilta))]
   #{:vapaa :sulku}
   ["tummansininen" (str "Vapaa " (osa/fmt-kohteenosa-tyyppi :sulku))]})

(defn kohteenosa-kohteiden-luonnissa [osa sama-kohde?]
  (cond sama-kohde?
        (let [[vari teksti] (kohteenosa-pinni-varit #{:sama-kohde (::osa/tyyppi osa)})]
          [(pinni-ikoni vari) teksti])

        (and (some? (::osa/kohde osa))
             (not (:poistettu osa)))
        (let [[vari teksti] (kohteenosa-pinni-varit #{:varattu (::osa/tyyppi osa)})]
          [(pinni-ikoni vari) teksti])

        :default
        (let [[vari teksti] (kohteenosa-pinni-varit #{:vapaa (::osa/tyyppi osa)})]
          [(pinni-ikoni vari) teksti])))

(defn kan-kohde [kohde]
  [(pinni-ikoni "syaani") "Kohde"])

(defn kan-toimenpide [toimenpide]
  [(pinni-ikoni "sininen") "Toimenpide"])

(defn kan-hairiotilanne [hairio]
  (if (= :valmis (::ht/korjauksen-tila hairio))
    [(pinni-ikoni "vihrea") "Korjattu häiriö"]
    [(pinni-ikoni "punainen") "Häiriötilanne"]))

(defn tielupa [lupa]
  (case (::tielupa/tyyppi lupa)
    :liittymalupa
    [(tielupa/tyyppi-fmt (::tielupa/tyyppi lupa))
     (pinni-ikoni "punainen")
     (viiva-mustalla-rajalla puhtaat/punainen)
     nil]

    :mainoslupa
    [(tielupa/tyyppi-fmt (::tielupa/tyyppi lupa))
     (pinni-ikoni "oranssi")
     (viiva-mustalla-rajalla puhtaat/oranssi)
     nil]

    :mainosilmoitus
    [(tielupa/tyyppi-fmt (::tielupa/tyyppi lupa))
     (pinni-ikoni "keltainen")
     (viiva-mustalla-rajalla puhtaat/keltainen)
     nil]

    :opastelupa
    [(tielupa/tyyppi-fmt (::tielupa/tyyppi lupa))
     (pinni-ikoni "vihrea")
     (viiva-mustalla-rajalla puhtaat/vihrea)
     nil]

    :suoja-aluerakentamislupa
    [(tielupa/tyyppi-fmt (::tielupa/tyyppi lupa))
     (pinni-ikoni "syaani")
     (viiva-mustalla-rajalla puhtaat/syaani)
     nil]

    :tilapainen-myyntilupa
    [(tielupa/tyyppi-fmt (::tielupa/tyyppi lupa))
     (pinni-ikoni "sininen")
     (viiva-mustalla-rajalla puhtaat/sininen)
     nil]

    :tilapainen-liikennemerkkijarjestely
    [(tielupa/tyyppi-fmt (::tielupa/tyyppi lupa))
     (pinni-ikoni "tummansininen")
     (viiva-mustalla-rajalla puhtaat/tummansininen)
     nil]

    :tietyolupa
    [(tielupa/tyyppi-fmt (::tielupa/tyyppi lupa))
     (pinni-ikoni "violetti")
     (viiva-mustalla-rajalla puhtaat/violetti)
     nil]

    :vesihuoltolupa
    [(tielupa/tyyppi-fmt (::tielupa/tyyppi lupa))
     (pinni-ikoni "magenta")
     (viiva-mustalla-rajalla puhtaat/magenta)
     nil]

    :johto-ja-kaapelilupa
    [(tielupa/tyyppi-fmt (::tielupa/tyyppi lupa))
     (pinni-ikoni "pinkki")
     (viiva-mustalla-rajalla puhtaat/pinkki)
     nil]

    [nil nil nil nil]))
