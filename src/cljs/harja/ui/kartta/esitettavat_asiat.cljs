(ns harja.ui.kartta.esitettavat-asiat
  (:require [harja.pvm :as pvm]
            [clojure.string :as str]
            [harja.loki :refer [log]]
            [cljs-time.core :as t]
            [harja.tiedot.urakka.laadunseuranta.laatupoikkeamat :as laatupoikkeamat]
            [harja.tiedot.urakka.laadunseuranta.tarkastukset :as tarkastukset]))

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
                :img    "kartta-tiedotus-violetti.svg"}
       :alue {:type        :tack-icon
              :scale       (if (valittu? ilmoitus) 1.5 1)
              :img         "kartta-tiedotus-violetti.svg"
              :coordinates (get-in ilmoitus [:sijainti :coordinates])})]))

(defmethod asia-kartalle :kysely [ilmoitus valittu?]
  (let [tooltip (or (ilmoituksen-tooltip ilmoitus) "Kysely")
        aloitettu? (sisaltaako-kuittauksen? ilmoitus :aloitus)
        lopetettu? (sisaltaako-kuittauksen? ilmoitus :lopetus)
        ikoni (cond
                lopetettu? "kartta-kysely-violetti.svg" ;; TODO Lisää harmaat ikonit kun valmistuvat.
                aloitettu? "kartta-kysely-violetti.svg"
                :else "kartta-kysely-kesken-punainen.svg")]
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
                lopetettu? "kartta-toimenpidepyynto-violetti.svg" ;; TODO
                vastaanotettu? "kartta-toimenpidepyynto-violetti.svg"
                :else "kartta-toimenpidepyynto-kesken-punainen.svg")]
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
    :urakoitsija (str "kartta-" ikonityyppi "-urakoitsija-violetti.svg")
    :tilaaja (str "kartta-" ikonityyppi "-tilaaja-violetti.svg")
    :konsultti (str "kartta-" ikonityyppi "-konsultti-violetti.svg")
    (str "kartta-" ikonityyppi "-violetti.svg")))

(defn selvita-tarkastuksen-ikoni [tekija]
  (selvita-laadunseurannan-ikoni "tarkastus" tekija))

(defn selvita-laatupoikkeaman-ikoni [tekija]
  (selvita-laadunseurannan-ikoni "laatupoikkeama" tekija))

(defmethod asia-kartalle :laatupoikkeama [laatupoikkeama valittu?]
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

                           (get-in laatupoikkeama [:sijainti :coordinates]))})])

(defmethod asia-kartalle :tarkastus [tarkastus valittu?]
  [(assoc tarkastus
     :type :tarkastus
     :nimi (or (:nimi tarkastus)
               (str (tarkastukset/+tarkastustyyppi->nimi+ (:tyyppi tarkastus)) " (" (laatupoikkeamat/kuvaile-tekija (:tekija tarkastus)) ")"))
     :selite {:teksti (str "Tarkastus (" (laatupoikkeamat/kuvaile-tekija (:tekija tarkastus)) ")")
              :img    (selvita-tarkastuksen-ikoni (:tekija tarkastus))}
     :alue (if (= :line (get-in tarkastus [:sijainti :type]))
             {:type  :tack-icon-line
              :scale (if (valittu? tarkastus) 1.5 1)
              :img   (selvita-tarkastuksen-ikoni (:tekija tarkastus))
              :points (get-in tarkastus [:sijainti :points])}
             {:type  :tack-icon
              :scale (if (valittu? tarkastus) 1.5 1)
              :img   (selvita-tarkastuksen-ikoni (:tekija tarkastus))
              :coordinates (get-in tarkastus [:sijainti :coordinates])}))])

(defmethod asia-kartalle :varustetoteuma [varustetoteuma]
  [(assoc varustetoteuma
     :type :varustetoteuma
     :nimi (or (:selitys-kartalla varustetoteuma) "Varustetoteuma")
     :alue {:type        :tack-icon
            :img         "kartta-hairion-hallinta-sininen.svg"
            :coordinates (get-in (first (:reittipisteet varustetoteuma)) [:sijainti :coordinates])})])

(defmethod asia-kartalle :toteuma [toteuma valittu?]
  ;; Yhdellä reittipisteellä voidaan tehdä montaa asiaa, ja tämän takia yksi reittipiste voi tulla
  ;; monta kertaa fronttiin.
  (let [reittipisteet (keep
                        (fn [[_ arvo]] (first arvo))
                        (group-by :id (:reittipisteet toteuma)))]
    [(when-not (empty? reittipisteet)
       (assoc toteuma
         :type :toteuma
         :nimi (or (:nimi toteuma)
                   (get-in toteuma [:tehtava :nimi])
                   (get-in toteuma [:tpi :nimi])
                   (if (> 1 (count (:tehtavat toteuma)))
                     (str (:toimenpide (first (:tehtavat toteuma))) " & ...")
                     (str (:toimenpide (first (:tehtavat toteuma))))))
         :selite {:teksti "Toteuma"
                  :img    "fixme.png"}
         :alue {
                :type   :arrow-line
                :scale  (if (valittu? toteuma) 0.8 0.5)     ;; TODO: Vaihda tämä joksikin paremmaksi kun saadaan oikeat ikonit :)
                :points (mapv #(get-in % [:sijainti :coordinates]) (sort-by
                                                                     :aika
                                                                     pvm/ennen?
                                                                     reittipisteet))}))]))
(defn paattele-turpon-ikoni [turpo]
  (let [kt (:korjaavattoimenpiteet turpo)]
    (if (empty? kt)
      ["kartta-turvallisuuspoikkeama-avoin-oranssi.svg" "Turvallisuuspoikkeama, avoin"]

      (if (some (comp nil? :suoritettu) kt)
        ["kartta-turvallisuuspoikkeama-ei-toteutettu-punainen.svg" "Turvallisuuspoikkeama, ei korjauksia"]

        ["kartta-turvallisuuspoikkeama-toteutettu-vihrea.svg" "Turvallisuuspoikkeama, kaikki korjattu"]))))

(defmethod asia-kartalle :turvallisuuspoikkeama [tp valittu?]
  (let [[ikoni selite] (paattele-turpon-ikoni tp)]
    [(assoc tp
       :type :turvallisuuspoikkeama
       :nimi (or (:nimi tp) "Turvallisuuspoikkeama")
       :selite {:teksti selite
                :img    ikoni}
       :alue (if (= :line (get-in tp [:sijainti :type]))
               {:type   :tack-icon-line
                :color  "black"
                :scale  (if (valittu? tp) 1.5 1)
                :img    ikoni
                :points (get-in tp [:sijainti :points])}
               {:type        :tack-icon
                :scale       (if (valittu? tp) 1.5 1)
                :img         ikoni
                :coordinates (get-in tp [:sijainti :coordinates])}))]))

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
  (let [ikonikartta {"auraus ja sohjonpoisto"          ["talvihoito" "Talvihoito"]
                     "suolaus"                         ["talvihoito" "Talvihoito"]
                     "pistehiekoitus"                  ["talvihoito" "Talvihoito"]
                     "linjahiekoitus"                  ["talvihoito" "Talvihoito"]
                     "lumivallien madaltaminen"        ["talvihoito" "Talvihoito"]
                     "sulamisveden haittojen torjunta" ["talvihoito" "Talvihoito"]
                     "kelintarkastus"                  ["talvihoito" "Talvihoito"]

                     "tiestotarkastus"                 ["liikenneympariston-hoito" "Liikenneympäristön hoito"]
                     "koneellinen niitto"              ["liikenneympariston-hoito" "Liikenneympäristön hoito"]
                     "koneellinen vesakonraivaus"      ["liikenneympariston-hoito" "Liikenneympäristön hoito"]

                     "liikennemerkkien puhdistus"      ["varusteet-ja-laitteet" "Varusteet ja laitteet"]

                     "sorateiden muokkaushoylays"      ["sorateiden-hoito" "Sorateiden hoito"]
                     "sorateiden polynsidonta"         ["sorateiden-hoito" "Sorateiden hoito"]
                     "sorateiden tasaus"               ["sorateiden-hoito" "Sorateiden hoito"]
                     "sorastus"                        ["sorateiden-hoito" "Sorateiden hoito"]

                     "harjaus"                         ["paallysteiden-yllapito" "Päällysteiden ylläpito"]
                     "pinnan tasaus"                   ["paallysteiden-yllapito" "Päällysteiden ylläpito"]
                     "paallysteiden paikkaus"          ["paallysteiden-yllapito" "Päällysteiden ylläpito"]
                     "paallysteiden juotostyot"        ["paallysteiden-yllapito" "Päällysteiden ylläpito"]

                     "siltojen puhdistus"              ["sillat" "Sillat"]

                     "l- ja p-alueiden puhdistus"      ["hairion-hallinta" "Häiriön hallinta"] ;; En tiedä yhtään mikä tämä on
                     "muu"                             ["hairion-hallinta" "Häiriön hallinta"]}
        tila (cond
               valittu? "valittu"
               (and lahetetty (t/before? lahetetty (t/now)) (> 20 (t/in-minutes (t/interval lahetetty (t/now)))))
               "sininen"
               :else "harmaa")
        [ikoni selite] (or (get ikonikartta (first tehtavat)) ["hairion-hallinta" "Häiriön hallinta"])]
    [(str "kartta-" ikoni "-" tila ".svg") (str "kartta-" ikoni "-sininen.svg") selite]))

(defmethod asia-kartalle :tyokone [tyokone valittu?]
  (let [[img selite-img selite-teksti] (paattele-tyokoneen-ikoni
                                         (:tehtavat tyokone)
                                         (or (:lahetysaika tyokone) (:vastaanotettu tyokone))
                                         (valittu? tyokone))]
    [(assoc tyokone
       :type :tyokone
       :nimi (or (:nimi tyokone) (str/capitalize (name (:tyokonetyyppi tyokone))))
       :selite {:teksti selite-teksti
                :img    ["kartta-suuntanuoli-sininen.svg" selite-img]}
       :alue {:type        :sticker-icon
              :coordinates (:sijainti tyokone)
              :direction   (+ (- Math/PI) (* (/ Math/PI 180) (:suunta tyokone)))
              :img         img})]))

(defmethod asia-kartalle :default [_ _ _])

(defn- valittu? [valittu tunniste asia]
  (and
    (not (nil? valittu))
    (= (get-in asia tunniste) (get-in valittu tunniste))))

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
    ;; tarkastetaan että edes jollain on..
   (assert (or (nil? asiat) (empty? asiat) (some :tyyppi-kartalla asiat)) "Kartalla esitettävillä asioilla pitää olla avain :tyyppi-kartalla!")
   (remove nil? (mapcat #(kartalla-xf % valittu tunniste) asiat))))
