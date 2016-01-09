(ns harja.ui.kartta.esitettavat-asiat
  (:require [harja.pvm :as pvm]
            [clojure.string :as str]
            [harja.loki :refer [log warn]]
            [cljs-time.core :as t]
            [harja.tiedot.urakka.laadunseuranta.laatupoikkeamat :as laatupoikkeamat]
            [harja.tiedot.urakka.laadunseuranta.tarkastukset :as tarkastukset]
            [harja.ui.yleiset :refer [karttakuva]]
            [harja.geo :as geo]))


(defn- oletusalue [asia valittu?]
  (merge
    (or (:sijainti asia)
        (:sijainti (first (:reittipisteet asia))))
    {:color  (if (valittu? asia) "blue" "green")
     :radius 300
     :stroke {:color "black" :width 10}}))

(defmulti
  ^{:private true}
  asia-kartalle :tyyppi-kartalla)

(defn sisaltaako-kuittauksen? [ilmoitus kuittaustyyppi]
  (some #(= (:kuittaustyyppi %) kuittaustyyppi) (get-in ilmoitus [:kuittaukset])))

(defn ilmoituksen-tooltip [ilmoitus]
  (if (empty? (:kuittaukset ilmoitus))
    "Ei kuittauksia"

    (case (last (map :kuittaustyyppi (:kuittaukset ilmoitus)))
      :vastaanotto "Vastaanottokuittaus annettu"
      :vastaus "Vastauskuittaus annettu"
      :aloitus "Aloituskuittaus annettu"
      :lopetus "Lopetuskuittaus annettu"
      :muutos "Muutoskuittaus annettu"
      nil)))

(defmethod asia-kartalle :tiedoitus [ilmoitus valittu?]
  (let [tooltip (or (ilmoituksen-tooltip ilmoitus) "Tiedotus")]
    [(assoc ilmoitus
       :type :ilmoitus
       :nimi tooltip
       :selite {:teksti "Tiedotus"
                :img    (karttakuva "tiedotus-tack-violetti")}
       :alue {:type        :tack-icon
              :scale       (if (valittu? ilmoitus) 1.5 1)
              :img         (karttakuva "tiedotus-tack-violetti")
              :coordinates (get-in ilmoitus [:sijainti :coordinates])})]))

(defmethod asia-kartalle :kysely [ilmoitus valittu?]
  (let [tooltip (or (ilmoituksen-tooltip ilmoitus) "Kysely")
        aloitettu? (sisaltaako-kuittauksen? ilmoitus :aloitus)
        lopetettu? (sisaltaako-kuittauksen? ilmoitus :lopetus)
        ikoni (cond
                lopetettu? (karttakuva "kysely-tack-harmaa") ;; TODO Lisää harmaat ikonit kun valmistuvat.
                aloitettu? (karttakuva "kysely-tack-violetti")
                :else (karttakuva "kysely-tack-punainen"))]
    [(assoc ilmoitus
       :type :ilmoitus
       :nimi tooltip
       :selite {:teksti (cond
                          aloitettu? "Kysely, aloitettu"
                          lopetettu? "Kysely, lopetettu"
                          :else "Kysely, ei aloituskuittausta.")
                :img    ikoni}
       :alue {:type        :tack-icon
              :scale       (if (valittu? ilmoitus) 1.5 1)
              :img         ikoni
              :coordinates (get-in ilmoitus [:sijainti :coordinates])})]))

(defmethod asia-kartalle :toimenpidepyynto [ilmoitus valittu?]
  (let [tooltip (or (ilmoituksen-tooltip ilmoitus) "Toimenpidepyyntö")
        vastaanotettu? (sisaltaako-kuittauksen? ilmoitus :vastaanotettu)
        lopetettu? (sisaltaako-kuittauksen? ilmoitus :lopetus)
        ikoni (cond
                lopetettu? (karttakuva "toimenpidepyynto-tack-harmaa") ;; TODO
                vastaanotettu? (karttakuva "toimenpidepyynto-tack-violetti")
                :else (karttakuva "toimenpidepyynto-tack-punainen"))]
    [(assoc ilmoitus
       :type :ilmoitus
       :nimi tooltip
       :selite {:teksti (cond
                          vastaanotettu? "Toimenpidepyyntö, kuitattu"
                          lopetettu? "Toimenpidepyyntö, lopetettu"
                          :else "Toimenpidepyyntö, kuittaamaton")
                :img    ikoni}
       :alue {:type        :tack-icon
              :scale       (if (valittu? ilmoitus) 1.5 1)
              :img         ikoni
              :coordinates (get-in ilmoitus [:sijainti :coordinates])})]))

(defn selvita-laadunseurannan-ikoni [ikonityyppi tekija]
  (case tekija
    :urakoitsija (str ikonityyppi (karttakuva "-urakoitsija-tack-violetti"))
    :tilaaja (str ikonityyppi (karttakuva "-tilaaja-tack-violetti"))
    :konsultti (str ikonityyppi (karttakuva "-konsultti-tack-violetti"))
    (str ikonityyppi (karttakuva "-tack-violetti"))))

(defn selvita-tarkastuksen-ikoni [tekija]
  (selvita-laadunseurannan-ikoni "tarkastus" tekija))

(defn selvita-laatupoikkeaman-ikoni [tekija]
  (selvita-laadunseurannan-ikoni "havainto" tekija))

(defmethod asia-kartalle :laatupoikkeama [laatupoikkeama valittu?]
  (when-let [sijainti (:sijainti laatupoikkeama)]
    [(assoc laatupoikkeama
            :type :laatupoikkeama
            :nimi (or (:nimi laatupoikkeama)
                      (str "Laatupoikkeama (" (laatupoikkeamat/kuvaile-tekija (:tekija laatupoikkeama)) ")"))
            :selite {:teksti (str "Laatupoikkeama (" (laatupoikkeamat/kuvaile-tekija (:tekija laatupoikkeama)) ")")
                     :img    (selvita-laatupoikkeaman-ikoni (:tekija laatupoikkeama))}
            :alue {:type        :tack-icon
                   :scale       (if (valittu? laatupoikkeama) 1.5 1)
                   :img         (selvita-laatupoikkeaman-ikoni (:tekija laatupoikkeama))
                   :coordinates (if (= :line (get-in laatupoikkeama [:sijainti :type]))
                                  ;; Lopetuspiste. Kai? Ainakin "viimeinen klikkaus" kun käyttää tr-komponenttia
                                  (first (get-in laatupoikkeama [:sijainti :points]))

                                  (get-in laatupoikkeama [:sijainti :coordinates]))})]))

(defmethod asia-kartalle :tarkastus [tarkastus valittu?]
  [(assoc tarkastus
     :type :tarkastus
     :nimi (or (:nimi tarkastus)
               (str (tarkastukset/+tarkastustyyppi->nimi+ (:tyyppi tarkastus)) " (" (laatupoikkeamat/kuvaile-tekija (:tekija tarkastus)) ")"))
     :selite {:teksti (str "Tarkastus (" (laatupoikkeamat/kuvaile-tekija (:tekija tarkastus)) ")")
              :img    (selvita-tarkastuksen-ikoni (:tekija tarkastus))}
     :alue (let [ikoni (selvita-tarkastuksen-ikoni (:tekija tarkastus))
                 skaala (if (valittu? tarkastus) 1.5 1)
                 sijainti (:sijainti tarkastus)]
             (case (:type sijainti)
               :line {:type   :tack-icon-line
                      :scale  skaala
                      :img    ikoni
                      :points (:points sijainti)}
               :multiline {:type :tack-icon-line
                           :scale skaala
                           :img ikoni
                           :points (mapcat :points (:lines sijainti))}
               :point {:type        :tack-icon
                       :scale       skaala
                       :img         ikoni
                       :coordinates (:coordinates sijainti)})))])

(defmethod asia-kartalle :varustetoteuma [varustetoteuma]
  (let [ikoni "varusteet-ja-laitteet-tack-violetti"]
    [(assoc varustetoteuma
      :type :varustetoteuma
      :nimi (or (:selitys-kartalla varustetoteuma) "Varustetoteuma")
      :selite {:teksti "Varustetoteuma"
               :img    (karttakuva ikoni)}
      :alue {:type        :tack-icon
             :img         (karttakuva ikoni)
             :coordinates (get-in (first (:reittipisteet varustetoteuma)) [:sijainti :coordinates])})]))

(def toteuma-varit-ja-nuolet
  [["rgb(255,0,0)" "punainen"]
   ["rgb(255,128,0)" "oranssi"]
   ["rgb(255,255,0)" "keltainen"]
   ["rgb(255,0,255)" "magenta"]
   ["rgb(0,255,0)" "vihrea"]
   ["rgb(0,255,128)" "turkoosi"]
   ["rgb(0,255,255)" "syaani"]
   ["rgb(0,128,255)" "sininen"]
   ["rgb(0,0,255)" "tummansininen"]
   ["rgb(128,0,255)" "violetti"]
   ["rgb(128,255,0)" "lime"]   
   ["rgb(255,0,128)" "pinkki"]])

(let [varien-lkm (count toteuma-varit-ja-nuolet)]
  (defn tehtavan-vari-ja-nuoli [tehtavan-nimi]
    (nth toteuma-varit-ja-nuolet (Math/abs (rem (hash tehtavan-nimi) varien-lkm)))))

(defn arrow-line [optiot geometria]
  (merge optiot
         {:type :arrow-line}
         (case (:type geometria)
           :line
           {:points (:points geometria)}
           
           :multiline
           {:points (mapcat :points (:lines geometria))}

           :point
           {:points [(:coordinates geometria)]})))

(defmethod asia-kartalle :toteuma [toteuma valittu?]
  ;; Piirretään toteuma sen tieverkolle projisoidusta reitistä (ei yksittäisistä reittipisteistä)
  (when-let [reitti (:reitti toteuma)]
    (let [nimi (or
                ;; toteumalla on suoraan nimi
                (:nimi toteuma)
                
                ;; tai nimi muodostetaan yhdistämällä tehtävien toimenpiteet 
                (reduce str
                        (butlast
                         (interleave (map :toimenpide (:tehtavat toteuma))
                                     (repeat ", ")))))
          [vari nuoli] (tehtavan-vari-ja-nuoli nimi)
          toteuma (assoc toteuma
                         :type :toteuma
                         :nimi nimi
                         :selite {:teksti nimi
                                  :vari vari})]
      [(assoc toteuma
              :alue (arrow-line {:width 5
                                 :color vari
                                 :arrow-image (karttakuva (str "images/nuoli-" nuoli))
                                 :scale (if (valittu? toteuma) 1.0 0.75)}
                                reitti))])))

(defn paattele-turpon-ikoni [turpo]
  (let [kt (:korjaavattoimenpiteet turpo)]
    (if (empty? kt)
      [(karttakuva "turvallisuuspoikkeama-tack-oranssi") "Turvallisuuspoikkeama, avoin"]

      (if (some (comp nil? :suoritettu) kt)
        [(karttakuva "turvallisuuspoikkeama-tack-punainen") "Turvallisuuspoikkeama, ei korjauksia"]

        [(karttakuva "turvallisuuspoikkeama-tack-vihrea") "Turvallisuuspoikkeama, kaikki korjattu"]))))

(defmethod asia-kartalle :turvallisuuspoikkeama [tp valittu?]
  (let [[ikoni selite] (paattele-turpon-ikoni tp)
        sijainti (:sijainti tp)
        tyyppi (:type sijainti)
        skaala (if (valittu? tp) 1.5 1)]

    (when sijainti
      [(assoc tp
              :type :turvallisuuspoikkeama
              :nimi (or (:nimi tp) "Turvallisuuspoikkeama")
              :selite {:teksti selite
                       :img    ikoni}
              :alue (case tyyppi
                      :line
                      {:type   :tack-icon-line
                       :color  "black"
                       :scale  skaala
                       :img    ikoni
                       :points (get-in tp [:sijainti :points])}

                      :multiline
                      {:type :tack-icon-line
                       :color "black"
                       :scale skaala
                       :img ikoni
                       :points (mapcat :points (:lines sijainti))}

                      :point
                      {:type :tack-icon
                       :scale skaala
                       :img ikoni
                       :coordinates (get-in tp [:sijainti :coordinates])}))])))

;; TODO: Päällystyksissä ja paikkauksissa on kommentoitua koodia, koska näille dedikoituijen näkymien käyttämät
;; kyselyt palauttavat datan sellaisessa muodossa, että sijainti pitää kaivaa erikseen "kohdeosista".
;; Tilannekuvassa tämä sijaintitieto palautetaan suoraan samassa kyselyssä. Tilannekuva on tällä hetkellä
;; ainoa paikka jossa piirretään päällystyksiä/paikkauksia tämän namespacen avulla, joten päätettiin toteuttaa
;; metodit uudelleen. Kun päällystys/paikkaus-näkymät laitetaan käyttämään tätä uutta paradigmaa, voidaan joko
;; toteuttaa näille omat metodit TAI miettiä, tarviiko tosiaan näiden käyttämä data palauttaa sellaisessa muodossa?
(defmethod asia-kartalle :paallystys [pt valittu?]
  [(assoc pt
     :type :paallystys
     :nimi (or (:nimi pt) "Päällystys")
     :alue (:sijainti pt))]

  #_(mapv
      (fn [kohdeosa]
        (assoc kohdeosa
          :type :paallystys
          :nimi (or (:nimi pt) "Päällystyskohde")
          :alue (:sijainti kohdeosa)))
      (:kohdeosat pt)))

(defmethod asia-kartalle :paikkaus [pt valittu?]
  [(assoc pt
     :type :paikkaus
     :nimi (or (:nimi pt) "Paikkaus")
     :alue (:sijainti pt))]
  #_(mapv
      (fn [kohdeosa]
        (assoc kohdeosa
          :type :paikkaus
          :nimi (or (:nimi pt) "Paikkaus")
          :alue (:sijainti kohdeosa)))
      (:kohdeosat pt)))

(defn- paattele-tyokoneen-ikoni
  [tehtavat lahetetty valittu?]
  ;; TODO Miten päätellään järkevästi mikä ikoni työkoneelle näytetään?
  ;; Ensinnäkin, en ole yhtään varma osuuko nämä suoritettavat tehtävät edes oikeanlaisiin ikoneihin
  ;; Mutta tärkempää on, että työkoneella voi olla useampi tehtävä. Miten se hoidetaan?
  ;; Voisi kuvitella että jotkut tehtävät ovat luonnostaan kiinnostavampia,
  ;; Esim jos talvella aurataan paljon mutta suolataan vain vähän (ja yleensä aurataan kun suolataan),
  ;; niin silloin pitäisi näyttää suolauksen ikoni silloin harvoin kun sitä tehdään.
  (let [ikonikartta {"auraus ja sohjonpoisto"          ["auraus-tai-sohjonpoisto" "Auraus tai sohjonpoisto"]
                     "suolaus"                         ["suolaus" "Suolaus"]
                     "pistehiekoitus"                  ["pistehiekoitus" "Pistehiekoitus"]
                     "linjahiekoitus"                  ["linjahiekoitus" "Linjahiekoitus"]
                     "lumivallien madaltaminen"        ["lumivallien-madaltaminen" "Lumivallien madaltaminen"]
                     "sulamisveden haittojen torjunta" ["sulamisveden haittojen torjunta" "Sulamisveden haittojen torjunta"]
                     "kelintarkastus"                  ["talvihoito" "Talvihoito"]

                     "tiestotarkastus"                 ["tiestotarkastus" "Tiestötarkastus" ]
                     "koneellinen niitto"              ["koneellinen-niitto" "Koneellinen niitto" ]
                     "koneellinen vesakonraivaus"      ["koneellinen-vesakonraivaus" "Koneellinen vesakonraivaus" ]

                     "liikennemerkkien puhdistus"      ["liikennemerkkien-puhdistus" "Liikennemerkkien puhdistus" ]

                     "sorateiden muokkaushoylays"      ["sorateiden-muokkaushoylays" "Sorateiden muokkaushöyläys" ]
                     "sorateiden polynsidonta"         ["sorateiden-polynsidonta" "Sorateiden pölynsidonta" ]
                     "sorateiden tasaus"               ["sorateiden-tasaus" "Sorateiden tasaus" ]
                     "sorastus"                        ["sorastus" "Sorastus" ]

                     "harjaus"                         ["harjaus" "Harjaus" ]
                     "pinnan tasaus"                   ["pinnan-tasaus" "Pinnan tasaus" ]
                     "paallysteiden paikkaus"          ["paallysteiden-paikkaus" "Päällysteiden paikkaus" ]
                     "paallysteiden juotostyot"        ["paallysteiden-juotostyot" "Päällysteiden juotostyöt" ]

                     "siltojen puhdistus"              ["siltojen-puhdistus" "Siltojen puhdistus" ]

                     "l- ja p-alueiden puhdistus"      ["l-ja-p-alueiden-puhdistus" "L- ja P-alueiden puhdistus" ]
                     "muu"                             ["tuntematon-koneen-tekema-tyo" "Muu" ]}
        tila (cond
               valittu? "valittu"
               (and lahetetty (t/before? lahetetty (t/now)) (> 20 (t/in-minutes (t/interval lahetetty (t/now)))))
               "sininen"
               :else "harmaa")
        [ikoni selite] (or (get ikonikartta (first tehtavat)) ["tuntematon-koneen-tekema-tyo" "Muu"])]
    [(karttakuva (str ikoni "-sticker-" tila)) (karttakuva (str ikoni "-sticker-sininen")) selite]))

(defmethod asia-kartalle :tyokone [tyokone valittu?]
  (let [[img selite-img selite-teksti] (paattele-tyokoneen-ikoni
                                         (:tehtavat tyokone)
                                         (or (:lahetysaika tyokone) (:vastaanotettu tyokone))
                                         (valittu? tyokone))]
    [(assoc tyokone
       :type :tyokone
       :nimi (or (:nimi tyokone) (str/capitalize (name (:tyokonetyyppi tyokone))))
       :selite {:teksti selite-teksti
                :img    [(karttakuva "sticker-sininen") selite-img]}
       :alue (if-let [reitti (:reitti tyokone)]
               {:type      :sticker-icon-line
                :points    reitti
                :direction (+ (- Math/PI) (* (/ Math/PI 180) (:suunta tyokone)))
                :img       img}
               {:type        :sticker-icon
                :coordinates (:sijainti tyokone)
                :direction   (+ (- Math/PI) (* (/ Math/PI 180) (:suunta tyokone)))
                :img         img}))]))

(defmethod asia-kartalle :default [asia _]
  (warn "Kartalla esitettävillä asioilla pitää olla :tyyppi-kartalla avain!, sain: " (pr-str asia))
  nil)

(defn- valittu? [valittu tunniste asia]
  (let [tunniste (if (vector? tunniste) tunniste [tunniste])]
    (and
     (not (nil? valittu))
     (= (get-in asia tunniste) (get-in valittu tunniste)))))

;; Palauttaa joukon vektoreita joten kutsu (mapcat kartalla-xf @jutut)
;; Tämä sen takia, että aiemmin toteumille piirrettiin "itse toteuma" viivana, ja jokaiselle reittipisteelle
;; oma merkki. Tästä luovuttiin, mutta pidetään vielä kiinni siitä että täältä palautetaan joukko vektoreita,
;; jos vastaisuudessa tulee samankaltaisia tilanteita.
(defn kartalla-xf
  ([asia] (kartalla-xf asia nil nil))
  ([asia valittu] (kartalla-xf asia valittu [:id]))
  ([asia valittu tunniste] (asia-kartalle asia (partial valittu? valittu tunniste))))

(defn kartalla-esitettavaan-muotoon
  ([asiat] (kartalla-esitettavaan-muotoon asiat nil nil))
  ([asiat valittu] (kartalla-esitettavaan-muotoon asiat valittu [:id]))
  ([asiat valittu tunniste]
   (kartalla-esitettavaan-muotoon asiat valittu tunniste nil))
  ([asiat valittu tunniste asia-xf]
   (let [extent (volatile! nil)]
     (with-meta 
       (into []
             (comp (or asia-xf identity)
                   (mapcat #(kartalla-xf % valittu (or tunniste [:id])))
                   (geo/laske-extent-xf extent))
             asiat)
       {:extent @extent}))))
