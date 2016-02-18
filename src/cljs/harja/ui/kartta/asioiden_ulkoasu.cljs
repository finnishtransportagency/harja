(ns harja.ui.kartta.asioiden-ulkoasu
  (:require [harja.ui.kartta.varit.puhtaat :as puhtaat]
            [harja.ui.dom :refer [sijainti-ikoni pinni-ikoni nuoli-ikoni]]
            [harja.ui.kartta.varit.alpha :as alpha]))

(def +valitun-skaala+ 1.5)
(def +normaali-skaala+ 1)
(def +zindex+ 4)
(def +normaali-leveys+ 6) ;;Openlayers default
(def +valitun-leveys+ 8)
(def +normaali-vari+ "black")
(def +valitun-vari+ "blue")

;; Koneellista generointia varten!
(def toteuma-varit-ja-nuolet
  [[{:color puhtaat/punainen} "punainen"]
   [{:color puhtaat/oranssi} "oranssi"]
   [{:color puhtaat/keltainen} "keltainen"]
   [{:color puhtaat/magenta} "magenta"]
   [{:color puhtaat/vihrea} "vihrea"]
   [{:color puhtaat/turkoosi} "turkoosi"]
   [{:color puhtaat/syaani} "syaani"]
   [{:color puhtaat/sininen} "sininen"]
   [{:color puhtaat/tummansininen} "tummansininen"]
   [{:color puhtaat/violetti} "violetti"]
   [{:color puhtaat/lime} "lime"]
   [{:color puhtaat/pinkki} "pinkki"]])

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

;;;;;;;;;;
;;; VÄRIMÄÄRITTELYT
;;; Kaikki värit pitäisi olla määriteltynä näillä muutamilla riveillä.
;;;;;;;;;;

(def ikonien-varit
  {;; Isommat, "tilalliset" sijainti-ikonit
   :tiedotus              "syaani"
   :kysely                "magenta"
   :toimenpidepyynto      "oranssi"
   :turvallisuuspoikkeama "punainen"

   ;; tilaa osoittavat värit (sijaint-ikonit)
   :ilmoitus-lopetettu "harmaa"
   :ilmoitus-kaynnissa "musta"
   :ilmoitus-auki "punainen"

   :kt-tyhja "oranssi"
   :kt-avoimia "punainen"
   :kt-valmis "vihrea"

   ;; Pienemmät ikonit
   :laatupoikkeama        "tummansininen"
   :tarkastus             "keltainen"
   :varustetoteuma        "violetti"
   :yllapito              "vihrea"})

(def viivojen-varit
  {:yllapito-aloitettu puhtaat/keltainen
   :yllapito-valmis puhtaat/vihrea
   :yllapito-muu puhtaat/sininen})


(def auraus-tasaus-ja-kolmas [(monivarinen-viiva puhtaat/sininen puhtaat/oranssi puhtaat/violetti) "oranssi"])
(def auraus-ja-hiekoitus [])
(def auraus-ja-suolaus [])

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
   #{"LUMIVALLIEN MADALTAMINEN"}                                []
   #{"SULAMISVEDEN HAITTOJEN TORJUNTA"}                         []
   #{"AURAUSVIITOITUS JA KINOSTIMET"}                           []
   #{"LUMENSIIRTO"}                                             []
   #{"PAANNEJAAN POISTO"}                                       []
   #{"MUU"}                                                     []
   ;; tilannekuva/kesähoito
   #{"SORATEIDEN POLYNSIDONTA"}                                 []
   #{"SORASTUS"}                                                []
   #{"SORATEIDEN TASAUS"}                                       []
   #{"SORATEIDEN MUOKKAUSHOYLAYS"}                              []
   #{"PAALLYSTEIDEN PAIKKAUS"}                                  []
   #{"PAALLYSTEIDEN JUOTOSTYOT"}                                []
   #{"KONEELLINEN NIITTO"}                                      []
   #{"KONEELLINEN VESAKONRAIVAUS"}                              []
   #{"HARJAUS"}                                                 []
   #{"LIIKENNEMERKKIEN PUHDISTUS"}                              []
   #{"L- JA P-ALUEIDEN PUHDISTUS"}                              []
   #{"SILTOJEN PUHDISTUS"}                                      []})
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
  {:paikka   :loppu
   :tyyppi   :nuoli
   :img      (nuoli-ikoni nuolen-vari)
   :rotation rotaatio})

(defn yllapidon-ikoni []
  {:paikka :loppu
   :tyyppi :merkki
   :img (:yllapito ikonien-varit)})

(defn yllapidon-viiva [valittu? avoin? tila]
  {:color (case (keyword (clojure.string/lower-case tila))
            :aloitettu (:yllapito-aloitettu viivojen-varit)
            :valmis (:yllapito-valmis viivojen-varit)
            (:yllapito-muu viivojen-varit))
   :width (if avoin?
            (if valittu? (+ 2 +valitun-leveys+) (+ 2 +normaali-leveys+))
            (if valittu? +valitun-leveys+ +normaali-leveys+))})

(defn turvallisuuspoikkeaman-ikoni [kt-tila]
  (sijainti-ikoni (case kt-tila
                    :tyhja (:kt-tyhja ikonien-varit)
                    :avoimia (:kt-avoimia ikonien-varit)
                    :valmis (:kt-valmis ikonien-varit)) (:turvallisuuspoikkeama ikonien-varit)))

(defn varustetoteuman-ikoni []
  (pinni-ikoni (:varustetoteuma ikonien-varit)))

(defn tarkastuksen-ikoni [valittu? ok?]
  (cond
    (not ok?) (pinni-ikoni (:tarkastus ikonien-varit))
    (and valittu? ok?) (pinni-ikoni (:tarkastus ikonien-varit)))) ;; Ei näytetä ok-tarkastuksia jos ei ole valittu

(defn tarkastuksen-reitti [ok?]
  (when ok? {:color alpha/vaaleanharmaa})) ;; Muuten oletusasetukset

(defn laatupoikkeaman-ikoni []
  (pinni-ikoni (:laatupoikkeama ikonien-varit)))

(defn toimenpidepyynnon-ikoni [lopetettu? vastaanotettu?]
  (cond
    lopetettu? (sijainti-ikoni (:ilmoitus-lopetettu ikonien-varit) (:toimenpidepyynto ikonien-varit))
    vastaanotettu? (sijainti-ikoni (:toimenpidepyynto ikonien-varit))
    :else (sijainti-ikoni (:ilmoitus-auki ikonien-varit) (:toimenpidepyynto ikonien-varit))))

(defn kyselyn-ikoni [lopetettu? aloitettu?]
  (cond
    lopetettu? (sijainti-ikoni (:ilmoitus-lopetettu ikonien-varit) (:kysely ikonien-varit))
    aloitettu? (sijainti-ikoni (:ilmoitus-kaynnissa ikonien-varit) (:kysely ikonien-varit))
    :else (sijainti-ikoni (:ilmoitus-auki ikonien-varit) (:kysely ikonien-varit))))

(defn tiedotuksen-ikoni []
  (sijainti-ikoni (:tiedotus ikonien-varit)))