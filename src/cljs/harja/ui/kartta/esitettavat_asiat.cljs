(ns harja.ui.kartta.esitettavat-asiat
  (:require [harja.pvm :as pvm]
            [clojure.string :as str]
            [harja.loki :refer [log]]))

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

(defmethod asia-kartalle :varustetoteuma [varustetoteuma valittu?]
  [(assoc varustetoteuma
     :type :varustetoteuma
     :nimi (or (:nimi varustetoteuma) "Varustetoteuma")
     :alue (oletusalue varustetoteuma valittu?))])

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
                :scale  (if (valittu? toteuma) 0.8 0.5)  ;; TODO: Vaihda tämä joksikin paremmaksi kun saadaan oikeat ikonit :)
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

(defn- suunta-radiaaneina [tyokone]
  (let [sijainti (:sijainti tyokone)
        edellinensijainti (or (:edellinensijainti tyokone) sijainti)
        lat1 (sijainti 0)
        lon1 (sijainti 1)
        lat2 (edellinensijainti 0)
        lon2 (edellinensijainti 1)]
    (mod (Math/atan2 (* (Math/sin (- lon2 lon1))
                        (Math/cos lat2))
                     (- (* (Math/cos lat1) (Math/sin lat2))
                        (* (Math/sin lat1) (Math/cos lat2) (Math/cos (- lon2 lon1)))))
         (* 2 Math/PI))))

(defmethod asia-kartalle :tyokone [tyokone valittu?]
  (assoc tyokone
    :type :tyokone
    :nimi (or (:nimi tyokone) (str/capitalize (name (:tyokonetyyppi tyokone))))
    :alue {:type        :icon
           :coordinates (:sijainti tyokone)
           :direction   (- (suunta-radiaaneina tyokone))
           :img         "images/tyokone.png"}))

(defmethod asia-kartalle :default [_ _ _])

(defn- valittu? [valittu tunniste asia]
  (and
    (not (nil? valittu))
    (= (get-in asia tunniste) (get-in valittu tunniste))))

;; Palauttaa joukon vektoreita, joten kutsu (mapcat kartalla-xf @jutut)
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