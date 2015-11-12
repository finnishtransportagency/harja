(ns harja.ui.kartta.esitettavat-asiat
  (:require [harja.pvm :as pvm]
            [clojure.string :as str]
            [harja.loki :refer [log]]
            [cljs-time.core :as t]))

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

(defmethod asia-kartalle :tiedoitus [ilmoitus valittu?]
  [(assoc ilmoitus
     :type :ilmoitus
     :nimi (or (:nimi ilmoitus) "Tiedotus")
     :selite {:teksti "Tiedotus"
              :img    "kartta-tiedotus-violetti.svg"}
     :alue {:type        :tack-icon
            :scale       (if (valittu? ilmoitus) 1.5 1)
            :img         "kartta-tiedotus-violetti.svg"
            :coordinates (get-in ilmoitus [:sijainti :coordinates])})])

(defmethod asia-kartalle :kysely [ilmoitus valittu?]
  [(assoc ilmoitus
     :type :ilmoitus
     :nimi (or (:nimi ilmoitus) "Kysely")
     :selite {:teksti "Kysely"
              :img    "kartta-kysely-violetti.svg"}
     :alue {:type        :tack-icon
            :scale       (if (valittu? ilmoitus) 1.5 1)
            :img         (if (sisaltaako-kuittauksen? ilmoitus :aloitus)
                           "kartta-kysely-violetti.svg"
                           "kartta-kysely-violetti.svg")
            :coordinates (get-in ilmoitus [:sijainti :coordinates])})])

(defmethod asia-kartalle :toimenpidepyynto [ilmoitus valittu?]
  [(assoc ilmoitus
     :type :ilmoitus
     :nimi (or (:nimi ilmoitus) "Toimenpidepyyntö")
     :selite {:teksti "Toimenpidepyyntö"
              :img    "kartta-toimenpidepyynto-violetti.svg"}
     :alue {:type        :tack-icon
            :scale       (if (valittu? ilmoitus) 1.5 1)
            :img         (if (sisaltaako-kuittauksen? ilmoitus :vastaanotto)
                           "kartta-toimenpidepyynto-violetti.svg"
                           "kartta-toimenpidepyynto-violetti.svg")
            :coordinates (get-in ilmoitus [:sijainti :coordinates])})])

(defmethod asia-kartalle :havainto [havainto valittu?]
  [(assoc havainto
     :type :havainto
     :nimi (or (:nimi havainto) "Havainto")
     :selite {:teksti "Havainto"
              :img    "kartta-havainto-violetti.svg"}
     :alue {:type        :tack-icon
            :scale       (if (valittu? havainto) 1.5 1)
            :img         "kartta-havainto-violetti.svg"
            :coordinates (get-in havainto [:sijainti :coordinates])})])

(defmethod asia-kartalle :pistokoe [tarkastus valittu?]
  [(assoc tarkastus
     :type :tarkastus
     :nimi (or (:nimi tarkastus) "Pistokoe")
     :selite {:teksti "Pistokoe"
              :img    "kartta-tarkastus-violetti.svg"}
     :alue {:type        :tack-icon
            :scale       (if (valittu? tarkastus) 1.5 1)
            :img         "kartta-tarkastus-violetti.svg"
            :coordinates (get-in tarkastus [:sijainti :coordinates])})])

(defmethod asia-kartalle :laaduntarkastus [tarkastus valittu?]
  [(assoc tarkastus
     :type :tarkastus
     :nimi (or (:nimi tarkastus) "Laaduntarkastus")
     :selite {:teksti "Laaduntarkastus"
              :img    "kartta-tarkastus-violetti.svg"}
     :alue {:type        :tack-icon
            :scale       (if (valittu? tarkastus) 1.5 1)
            :img         "kartta-tarkastus-violetti.svg"
            :coordinates (get-in tarkastus [:sijainti :coordinates])})])

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


(defmethod asia-kartalle :turvallisuuspoikkeama [tp valittu?]
  [(assoc tp
     :type :turvallisuuspoikkeama
     :nimi (or (:nimi tp) "Turvallisuuspoikkeama")
     :selite {:teksti "Turvallisuuspoikkeama"
              :img    "kartta-turvallisuuspoikkeama-avoin-oranssi.svg"}
     :alue {:type        :tack-icon
            :scale       (if (valittu? tp) 1.5 1)
            :img         "kartta-turvallisuuspoikkeama-avoin-oranssi.svg"
            :coordinates (get-in tp [:sijainti :coordinates])})])

(defmethod asia-kartalle :paallystyskohde [pt valittu?]
  (mapv
    (fn [kohdeosa]
      (assoc kohdeosa
        :type :paallystyskohde
        :nimi (or (:nimi pt) "Päällystyskohde")
        :alue (:sijainti kohdeosa)))
    (:kohdeosat pt)))

(defmethod asia-kartalle :paikkaustoteuma [pt valittu?]
  ;; Saattaa olla, että yhdelle kohdeosalle pitää antaa jokin viittaus paikkaustoteumaan.
  (mapv
    (fn [kohdeosa]
      (assoc kohdeosa
        :type :paikkaustoteuma
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
  (let [ikonikartta {"auraus ja sohjonpoisto" ["talvihoito" "Talvihoito"]
                     "suolaus" ["talvihoito" "Talvihoito"]
                     "pistehiekoitus" ["talvihoito" "Talvihoito"]
                     "linjahiekoitus" ["talvihoito" "Talvihoito"]
                     "lumivallien madaltaminen" ["talvihoito" "Talvihoito"]
                     "sulamisveden haittojen torjunta" ["talvihoito" "Talvihoito"]
                     "kelintarkastus" ["talvihoito" "Talvihoito"]

                     "tiestotarkastus" ["liikenneympariston-hoito" "Liikenneympäristön hoito"]
                     "koneellinen niitto" ["liikenneympariston-hoito" "Liikenneympäristön hoito"]
                     "koneellinen vesakonraivaus" ["liikenneympariston-hoito" "Liikenneympäristön hoito"]

                     "liikennemerkkien puhdistus" ["varusteet-ja-laitteet" "Varusteet ja laitteet"]

                     "sorateiden muokkaushoylays" ["sorateiden-hoito" "Sorateiden hoito"]
                     "sorateiden polynsidonta" ["sorateiden-hoito" "Sorateiden hoito"]
                     "sorateiden tasaus" ["sorateiden-hoito" "Sorateiden hoito"]
                     "sorastus" ["sorateiden-hoito" "Sorateiden hoito"]

                     "harjaus" ["paallysteiden-yllapito" "Päällysteiden ylläpito"]
                     "pinnan tasaus" ["paallysteiden-yllapito" "Päällysteiden ylläpito"]
                     "paallysteiden paikkaus" ["paallysteiden-yllapito" "Päällysteiden ylläpito"]
                     "paallysteiden juotostyot" ["paallysteiden-yllapito" "Päällysteiden ylläpito"]

                     "siltojen puhdistus" ["sillat" "Sillat"]

                     "l- ja p-alueiden puhdistus" ["hairion-hallinta" "Häiriön hallinta"] ;; En tiedä yhtään mikä tämä on
                     "muu" ["hairion-hallinta" "Häiriön hallinta"]}
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
                :img    selite-img}
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