(ns harja.ui.kartta.esitettavat-asiat
  (:require [harja.pvm :as pvm]
            [clojure.string :as str]
            [harja.loki :refer [log]]
            [cljs-time.core :as t]))

(defn- oletusalue [asia valittu?]
  (merge
    (:sijainti asia)
    {:color  (if (valittu? asia) "blue" "green")
     :radius 300
     :stroke {:color "black" :width 10}}))

(defmulti
  ^{:private true}
  asia-kartalle :tyyppi-kartalla)

(defmethod asia-kartalle :tiedoitus [ilmoitus valittu?]
  [(assoc ilmoitus
     :type :ilmoitus
     :nimi (or (:nimi ilmoitus) "Tiedotus")
     :alue (oletusalue ilmoitus valittu?))])

(defmethod asia-kartalle :kysely [ilmoitus valittu?]
  [(assoc ilmoitus
     :type :ilmoitus
     :nimi (or (:nimi ilmoitus) "Kysely")
     :alue (oletusalue ilmoitus valittu?))])

(defmethod asia-kartalle :toimenpidepyynto [ilmoitus valittu?]
  [(assoc ilmoitus
     :type :ilmoitus
     :nimi (or (:nimi ilmoitus) "Toimenpidepyyntö")
     :alue (oletusalue ilmoitus valittu?))])

(defmethod asia-kartalle :havainto [havainto valittu?]
  [(assoc havainto
     :type :havainto
     :nimi (or (:nimi havainto) "Havainto")
     :alue (oletusalue havainto valittu?))])

(defmethod asia-kartalle :pistokoe [tarkastus valittu?]
  [(assoc tarkastus
     :type :tarkastus
     :nimi (or (:nimi tarkastus) "Pistokoe")
     :alue (oletusalue tarkastus valittu?))])

(defmethod asia-kartalle :laaduntarkastus [tarkastus valittu?]
  [(assoc tarkastus
     :type :tarkastus
     :nimi (or (:nimi tarkastus) "Laaduntarkastus")
     :alue (oletusalue tarkastus valittu?))])

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
     :alue {:type        :tack-icon
            :img         "kartta-hairion-hallinta-sininen.svg"
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
  (let [tila (cond
               valittu? "valittu"
               (> 20 (t/in-minutes (t/interval lahetetty (t/now)))) "sininen"
               :else "harmaa")
        ;; TODO Miten päätellään järkevästi mikä ikoni työkoneelle näytetään?
        ;; Ensinnäkin, en ole yhtään varma osuuko nämä suoritettavat tehtävät edes oikeanlaisiin ikoneihin
        ;; Mutta tärkempää on, että työkoneella voi olla useampi tehtävä. Miten se hoidetaan?
        ;; Voisi kuvitella että jotkut tehtävät ovat luonnostaan kiinnostavampia,
        ;; Esim jos talvella aurataan paljon mutta suolataan vain vähän (ja yleensä aurataan kun suolataan),
        ;; niin silloin pitäisi näyttää suolauksen ikoni silloin harvoin kun sitä tehdään.
        ikoni (condp = (first tehtavat)
                "auraus ja sohjonpoisto" "talvihoito"
                "suolaus" "talvihoito"
                "pistehiekoitus" "talvihoito"
                "linjahiekoitus" "talvihoito"
                "lumivallien madaltaminen" "talvihoito"
                "sulamisveden haittojen torjunta" "talvihoito"
                "kelintarkastus" "talvihoito"

                "tiestotarkastus" "liikenneympariston-hoito"
                "koneellinen niitto" "liikenneympariston-hoito"
                "koneellinen vesakonraivaus" "liikenneympariston-hoito"

                "liikennemerkkien puhdistus" "varusteet-ja-laitteet"

                "sorateiden muokkaushoylays" "sorateiden-hoito"
                "sorateiden polynsidonta" "sorateiden-hoito"
                "sorateiden tasaus" "sorateiden-hoito"
                "sorastus" "sorateiden-hoito"

                "harjaus" "paallysteiden-yllapito"
                "pinnan tasaus" "paallysteiden-yllapito"
                "paallysteiden paikkaus" "paallysteiden-yllapito"
                "paallysteiden juotostyot" "paallysteiden-yllapito"

                "siltojen puhdistus" "sillat"

                "l- ja p-alueiden puhdistus" "hairion-hallinta" ;; En tiedä yhtään mikä tämä on
                "muu" "hairion-hallinta"
                "hairion-hallinta")]
    (log (str "kartta-" ikoni "-" tila ".svg"))
    (str "kartta-" ikoni "-" tila ".svg")))

(defmethod asia-kartalle :tyokone [tyokone valittu?]
  (log "Tehdäänpä työkone!")
  [(assoc tyokone
     :type :tyokone
     :nimi (or (:nimi tyokone) (str/capitalize (name (:tyokonetyyppi tyokone))))
     :alue {:type        :sticker-icon
            :coordinates (:sijainti tyokone)
            :direction   (- (/ Math/PI 2) (* (/ Math/PI 180) (:suunta tyokone))) ;; Onkohan oikein..
            :img         (paattele-tyokoneen-ikoni
                           (:tehtavat tyokone)
                           (or (:lahetysaika tyokone) (:vastaanotettu tyokone))
                           (valittu? tyokone))})])

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