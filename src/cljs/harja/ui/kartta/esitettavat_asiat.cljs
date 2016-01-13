(ns harja.ui.kartta.esitettavat-asiat
  (:require [harja.pvm :as pvm]
            [clojure.string :as str]
            [harja.loki :refer [log warn]]
            [cljs-time.core :as t]
            [harja.tiedot.urakka.laadunseuranta.laatupoikkeamat :as laatupoikkeamat]
            [harja.tiedot.urakka.laadunseuranta.tarkastukset :as tarkastukset]
            [harja.ui.yleiset :refer [karttakuva]]
            [harja.geo :as geo]))

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

(defn tack-icon [optiot geometria]
  (merge {:color "black"}                                   ;; Käytetään oletuksena mustaa viivaa
         optiot
         (case (:type geometria)
           :line
           {:type   :tack-icon-line
            :points (:points geometria)}

           :multiline
           {:type   :tack-icon-line
            :points (mapcat :points (:lines geometria))}

           :point
           {:type        :tack-icon
            :coordinates (:coordinates geometria)})))

(defn- oletusalue [asia valittu?]
  (merge
    (or (:sijainti asia)
        (:sijainti (first (:reittipisteet asia))))
    {:color  (if (valittu? asia) "blue" "green")
     :radius 300
     :stroke {:color "black" :width 10}}))

(def +valitun-skaala+ 1.5)
(def +normaali-skaala+ 1)

(defn- laske-skaala [asia valittu?]
  (if (valittu? asia) +valitun-skaala+ +normaali-skaala+))

(defmulti
  ^{:private true}
  asia-kartalle :tyyppi-kartalla)

(defn sisaltaako-kuittauksen? [ilmoitus kuittaustyyppi]
  (some #(= (:kuittaustyyppi %) kuittaustyyppi) (get-in ilmoitus [:kuittaukset])))

(defn ilmoituksen-tooltip [ilmoitus oletusteksti]
  (if (empty? (:kuittaukset ilmoitus))
    "Ei kuittauksia"

    (case (last (map :kuittaustyyppi (:kuittaukset ilmoitus)))
      :vastaanotto "Vastaanottokuittaus annettu"
      :vastaus "Vastauskuittaus annettu"
      :aloitus "Aloituskuittaus annettu"
      :lopetus "Lopetuskuittaus annettu"
      :muutos "Muutoskuittaus annettu"
      oletusteksti)))

(defn- tack-ikoni [ilmoitus ikoni valittu?]
  (tack-icon
   {:scale (laske-skaala ilmoitus valittu?)
    :img ikoni}
   (:sijainti ilmoitus)))

(defn- musta-tack-ikoni [ilmoitus ikoni valittu?]
  (assoc (tack-ikoni ilmoitus ikoni valittu?)
         :color "black"))

(defn- levea-tack-ikoni-varilla [pt ikoni valittu? vari]
  (assoc (tack-ikoni pt ikoni valittu?)
         :color vari
         :width (when (:avoin? pt) 8)))

(defmethod asia-kartalle :tiedoitus [ilmoitus valittu?]
  (assoc ilmoitus
         :type :ilmoitus
         :nimi (ilmoituksen-tooltip ilmoitus "Tiedotus")
         :selite {:teksti "Tiedotus"
                  :img    (karttakuva "tiedotus-tack-violetti")}
         :alue (tack-ikoni ilmoitus (karttakuva "tiedotus-tack-violetti") valittu?)))

(defmethod asia-kartalle :kysely [ilmoitus valittu?]
  (let [aloitettu? (sisaltaako-kuittauksen? ilmoitus :aloitus)
        lopetettu? (sisaltaako-kuittauksen? ilmoitus :lopetus)
        ikoni (karttakuva (cond
                            lopetettu? "kysely-tack-harmaa"
                            aloitettu? "kysely-tack-violetti"
                            :else "kysely-tack-punainen"))]
    (assoc ilmoitus
           :type :ilmoitus
           :nimi (ilmoituksen-tooltip ilmoitus "Kysely")
           :selite {:teksti (cond
                              aloitettu? "Kysely, aloitettu"
                              lopetettu? "Kysely, lopetettu"
                              :else "Kysely, ei aloituskuittausta.")
                    :img    ikoni}
           :alue (tack-ikoni ilmoitus ikoni valittu?))))

(defmethod asia-kartalle :toimenpidepyynto [ilmoitus valittu?]
  (let [vastaanotettu? (sisaltaako-kuittauksen? ilmoitus :vastaanotettu)
        lopetettu? (sisaltaako-kuittauksen? ilmoitus :lopetus)
        ikoni (karttakuva (cond
                            lopetettu? "toimenpidepyynto-tack-harmaa"
                            vastaanotettu? "toimenpidepyynto-tack-violetti"
                            :else "toimenpidepyynto-tack-punainen"))]
    (assoc ilmoitus
           :type :ilmoitus
           :nimi (ilmoituksen-tooltip ilmoitus "Toimenpidepyyntö")
           :selite {:teksti (cond
                              vastaanotettu? "Toimenpidepyyntö, kuitattu"
                              lopetettu? "Toimenpidepyyntö, lopetettu"
                              :else "Toimenpidepyyntö, kuittaamaton")
                    :img    ikoni}
           :alue (tack-ikoni ilmoitus ikoni valittu?))))

(defn selvita-laadunseurannan-ikoni [ikonityyppi tekija]
  (karttakuva
   (case tekija
     :urakoitsija (str ikonityyppi "-urakoitsija-tack-violetti")
     :tilaaja (str ikonityyppi "-tilaaja-tack-violetti")
     :konsultti (str ikonityyppi "-konsultti-tack-violetti")
     (str ikonityyppi "-tack-violetti"))))

(defn selvita-tarkastuksen-ikoni [tekija]
  (selvita-laadunseurannan-ikoni "tarkastus" tekija))

(defn selvita-laatupoikkeaman-ikoni [tekija]
  (selvita-laadunseurannan-ikoni "havainto" tekija))

(defn otsikko-tekijalla [etuliite laatupoikkeama]
  (str etuliite " (" (laatupoikkeamat/kuvaile-tekija (:tekija laatupoikkeama)) ")"))

(defmethod asia-kartalle :laatupoikkeama [laatupoikkeama valittu?]
  (when (:sijainti laatupoikkeama)
    (let [ikoni (selvita-laatupoikkeaman-ikoni (:tekija laatupoikkeama))
          otsikko (otsikko-tekijalla "Laatupoikkeama" laatupoikkeama)]
      (assoc laatupoikkeama
             :type :laatupoikkeama
             :nimi (or (:nimi laatupoikkeama) otsikko)
             :selite {:teksti otsikko
                      :img    ikoni}
             :alue (tack-ikoni laatupoikkeama ikoni valittu?)))))

(defmethod asia-kartalle :tarkastus [tarkastus valittu?]
  (let [ikoni (selvita-tarkastuksen-ikoni (:tekija tarkastus))]
    (assoc tarkastus
           :type :tarkastus
           :nimi (or (:nimi tarkastus)
                     (otsikko-tekijalla (tarkastukset/+tarkastustyyppi->nimi+ (:tyyppi tarkastus)) tarkastus))
           :selite {:teksti (otsikko-tekijalla "Tarkastus" tarkastus)
                    :img    ikoni}
           :alue (tack-ikoni tarkastus ikoni valittu?))))

(defmethod asia-kartalle :varustetoteuma [varustetoteuma]
  (let [ikoni (karttakuva "varusteet-ja-laitteet-tack-violetti")]
    (assoc varustetoteuma
           :type :varustetoteuma
           :nimi (or (:selitys-kartalla varustetoteuma) "Varustetoteuma")
           :selite {:teksti "Varustetoteuma"
                    :img    ikoni}
           :alue (tack-ikoni varustetoteuma ikoni false))))

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

(defmethod asia-kartalle :toteuma [toteuma valittu?]
  ;; Piirretään toteuma sen tieverkolle projisoidusta reitistä (ei yksittäisistä reittipisteistä)
  (when-let [reitti (:reitti toteuma)]
    (let [nimi (or
                ;; toteumalla on suoraan nimi
                (:nimi toteuma)
                ;; tai nimi muodostetaan yhdistämällä tehtävien toimenpiteet
                (str/join ", " (map :toimenpide (:tehtavat toteuma))))
          [vari nuoli] (tehtavan-vari-ja-nuoli nimi)]
      (assoc toteuma
             :type :toteuma
             :nimi nimi
             :selite {:teksti nimi
                      :vari   vari}
             :alue (arrow-line {:width       5
                                :color       vari
                                :arrow-image (karttakuva (str "images/nuoli-" nuoli))
                                :scale       (if (valittu? toteuma) 1.0 0.75)}
                               reitti)))))

(defn paattele-turpon-ikoni [turpo]
  (let [kt (:korjaavattoimenpiteet turpo)]
    (if (empty? kt)
      [(karttakuva "turvallisuuspoikkeama-tack-oranssi") "Turvallisuuspoikkeama, avoin"]

      (if (some (comp nil? :suoritettu) kt)
        [(karttakuva "turvallisuuspoikkeama-tack-punainen") "Turvallisuuspoikkeama, ei korjauksia"]

        [(karttakuva "turvallisuuspoikkeama-tack-vihrea") "Turvallisuuspoikkeama, kaikki korjattu"]))))

(defmethod asia-kartalle :turvallisuuspoikkeama [tp valittu?]
  (let [[ikoni selite] (paattele-turpon-ikoni tp)]
    (when (:sijainti tp)
      (assoc tp
             :type :turvallisuuspoikkeama
             :nimi (or (:nimi tp) "Turvallisuuspoikkeama")
             :selite {:teksti selite
                      :img    ikoni}
             :alue (musta-tack-ikoni tp ikoni valittu?)))))

(defn- paattele-yllapidon-ikoni-ja-viivan-vari
  [teksti asia]
  (let [[ikonin-vari viivan-vari] (if (:tila asia)
                                    (case (name (:tila asia))
                                      "aloitettu" ["keltainen" "rgb(255,255,0)"]
                                      "valmis" ["vihrea" "rgb(0,255,0)"]
                                      ["sininen" "rgb(0,128,255)"])

                                    ["sininen" "rgb(0,128,255)"])] ;; tila on nil jos vasta suunnitteilla
    [(karttakuva
      (str teksti "-tack-" ikonin-vari)) viivan-vari]))

;; TODO: Päällystyksissä ja paikkauksissa on kommentoitua koodia, koska näille dedikoituijen näkymien käyttämät
;; kyselyt palauttavat datan sellaisessa muodossa, että sijainti pitää kaivaa erikseen "kohdeosista".
;; Tilannekuvassa tämä sijaintitieto palautetaan suoraan samassa kyselyssä. Tilannekuva on tällä hetkellä
;; ainoa paikka jossa piirretään päällystyksiä/paikkauksia tämän namespacen avulla, joten päätettiin toteuttaa
;; metodit uudelleen. Kun päällystys/paikkaus-näkymät laitetaan käyttämään tätä uutta paradigmaa, voidaan joko
;; toteuttaa näille omat metodit TAI miettiä, tarviiko tosiaan näiden käyttämä data palauttaa sellaisessa muodossa?
(defn- paikkaus-paallystys [pt valittu? tyo teksti]
  (let [[ikoni viiva] (paattele-yllapidon-ikoni-ja-viivan-vari tyo pt)]
    (assoc pt
           :nimi (or (:nimi pt) teksti)
           :selite {:teksti teksti
                    :img    ikoni}
           :alue (levea-tack-ikoni-varilla pt ikoni valittu? viiva))))

(defmethod asia-kartalle :paallystys [pt valittu?]
  (assoc (paikkaus-paallystys pt valittu? "paallystystyo" "Päällystys")
         :type :paallystys))

(defmethod asia-kartalle :paikkaus [pt valittu?]
  (assoc (paikkaus-paallystys pt valittu? "paikkaustyo" "Paikkaus")
         :type :paikkaus))

(def ikonikartta {"auraus ja sohjonpoisto"          ["auraus-tai-sohjonpoisto" "Auraus tai sohjonpoisto"]
                  "suolaus"                         ["suolaus" "Suolaus"]
                  "pistehiekoitus"                  ["pistehiekoitus" "Pistehiekoitus"]
                  "linjahiekoitus"                  ["linjahiekoitus" "Linjahiekoitus"]
                  "lumivallien madaltaminen"        ["lumivallien-madaltaminen" "Lumivallien madaltaminen"]
                  "sulamisveden haittojen torjunta" ["sulamisveden haittojen torjunta" "Sulamisveden haittojen torjunta"]
                  "kelintarkastus"                  ["talvihoito" "Talvihoito"]

                  "tiestotarkastus"                 ["tiestotarkastus" "Tiestötarkastus"]
                  "koneellinen niitto"              ["koneellinen-niitto" "Koneellinen niitto"]
                  "koneellinen vesakonraivaus"      ["koneellinen-vesakonraivaus" "Koneellinen vesakonraivaus"]

                  "liikennemerkkien puhdistus"      ["liikennemerkkien-puhdistus" "Liikennemerkkien puhdistus"]

                  "sorateiden muokkaushoylays"      ["sorateiden-muokkaushoylays" "Sorateiden muokkaushöyläys"]
                  "sorateiden polynsidonta"         ["sorateiden-polynsidonta" "Sorateiden pölynsidonta"]
                  "sorateiden tasaus"               ["sorateiden-tasaus" "Sorateiden tasaus"]
                  "sorastus"                        ["sorastus" "Sorastus"]

                  "harjaus"                         ["harjaus" "Harjaus"]
                  "pinnan tasaus"                   ["pinnan-tasaus" "Pinnan tasaus"]
                  "paallysteiden paikkaus"          ["paallysteiden-paikkaus" "Päällysteiden paikkaus"]
                  "paallysteiden juotostyot"        ["paallysteiden-juotostyot" "Päällysteiden juotostyöt"]

                  "siltojen puhdistus"              ["siltojen-puhdistus" "Siltojen puhdistus"]

                  "l- ja p-alueiden puhdistus"      ["l-ja-p-alueiden-puhdistus" "L- ja P-alueiden puhdistus"]
                  "muu"                             ["tuntematon-koneen-tekema-tyo" "Muu"]})

(defn- paattele-tyokoneen-ikoni
  [tehtavat lahetetty valittu?]
  ;; TODO Miten päätellään järkevästi mikä ikoni työkoneelle näytetään?
  ;; Ensinnäkin, en ole yhtään varma osuuko nämä suoritettavat tehtävät edes oikeanlaisiin ikoneihin
  ;; Mutta tärkempää on, että työkoneella voi olla useampi tehtävä. Miten se hoidetaan?
  ;; Voisi kuvitella että jotkut tehtävät ovat luonnostaan kiinnostavampia,
  ;; Esim jos talvella aurataan paljon mutta suolataan vain vähän (ja yleensä aurataan kun suolataan),
  ;; niin silloin pitäisi näyttää suolauksen ikoni silloin harvoin kun sitä tehdään.
  (let [tila (cond
               valittu? "valittu"
               (and lahetetty (t/before? lahetetty (t/now)) (> 20 (t/in-minutes (t/interval lahetetty (t/now)))))
               "sininen"
               :else "harmaa")
        [ikoni selite] (or (get ikonikartta (first tehtavat)) ["tuntematon-koneen-tekema-tyo" "Muu"])]
    [(karttakuva (str ikoni "-sticker-" tila)) (karttakuva (str ikoni "-sticker-sininen")) selite]))

(defn muunna-tyokoneen-suunta [kulma]
  (+ (- Math/PI) (* (/ Math/PI 180) kulma)))

(defmethod asia-kartalle :tyokone [tyokone valittu?]
  (let [[img selite-img selite-teksti] (paattele-tyokoneen-ikoni
                                         (:tehtavat tyokone)
                                         (or (:lahetysaika tyokone) (:vastaanotettu tyokone))
                                         (valittu? tyokone))]
    (assoc tyokone
           :type :tyokone
           :nimi (or (:nimi tyokone) (str/capitalize (name (:tyokonetyyppi tyokone))))
           :selite {:teksti selite-teksti
                    :img    [(karttakuva "sticker-sininen") selite-img]}
           :alue (if-let [reitti (:reitti tyokone)]
                   {:type      :sticker-icon-line
                    :points    reitti
                    :direction (muunna-tyokoneen-suunta (:suunta tyokone))
                    :img       img}
                   {:type        :sticker-icon
                    :coordinates (:sijainti tyokone)
                    :direction   (muunna-tyokoneen-suunta (:suunta tyokone))
                    :img         img}))))

(defmethod asia-kartalle :default [asia _]
  (warn "Kartalla esitettävillä asioilla pitää olla :tyyppi-kartalla avain!, sain: " (pr-str asia))
  nil)

(defn- valittu? [valittu tunniste asia]
  (let [tunniste (if (vector? tunniste) tunniste [tunniste])]
    (and
      (not (nil? valittu))
      (= (get-in asia tunniste) (get-in valittu tunniste)))))

(defn- tallenna-selitteet-xf [selitteet]
  (fn [xf]
    (fn
      ([] (xf))
      ([result] (xf result))
      ([result input]
       (when-let [selite (:selite input)]
         (vswap! selitteet conj selite))
       (xf result input)))))

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
   (let [extent (volatile! nil)
         selitteet (volatile! #{})]
     (with-meta 
       (into []
             (comp (or asia-xf identity)
                   (map #(kartalla-xf % valittu (or tunniste [:id])))
                   (geo/laske-extent-xf extent)
                   (tallenna-selitteet-xf selitteet))
             asiat)
       {:extent @extent
        :selitteet @selitteet}))))

