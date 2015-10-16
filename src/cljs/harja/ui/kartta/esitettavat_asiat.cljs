(ns harja.ui.kartta.esitettavat-asiat
  (:require [harja.pvm :as pvm]
            [clojure.string :as str]))

(defn oletusalue [asia on-valittu?]
  (merge
    (:sijainti asia)
    {:color  (if (on-valittu? asia) "blue" "green")
     :radius 300
     :stroke {:color "black" :width 10}}))

(defmulti
  ^{:private true}
  asia-kartalle :tyyppi-kartalla)

(defmethod asia-kartalle :tiedoitus [ilmoitus on-valittu?]
  [(assoc ilmoitus
     :type :ilmoitus
     :nimi (or (:nimi ilmoitus) "Tiedotus")
     :alue (oletusalue ilmoitus on-valittu?))])

(defmethod asia-kartalle :kysely [ilmoitus on-valittu?]
  [(assoc ilmoitus
     :type :ilmoitus
     :nimi (or (:nimi ilmoitus) "Kysely")
     :alue (oletusalue ilmoitus on-valittu?))])

(defmethod asia-kartalle :toimenpidepyynto [ilmoitus on-valittu?]
  [(assoc ilmoitus
     :type :ilmoitus
     :nimi (or (:nimi ilmoitus) "Toimenpidepyyntö")
     :alue (oletusalue ilmoitus on-valittu?))])

(defmethod asia-kartalle :havainto [havainto on-valittu?]
  [(assoc havainto
     :type :havainto
     :nimi (or (:nimi havainto) "Havainto")
     :alue (oletusalue havainto on-valittu?))])

(defmethod asia-kartalle :pistokoe [tarkastus on-valittu?]
  [(assoc tarkastus
     :type :tarkastus
     :nimi (or (:nimi tarkastus) "Pistokoe")
     :alue (oletusalue tarkastus on-valittu?))])

(defmethod asia-kartalle :laaduntarkastus [tarkastus on-valittu?]
  [(assoc tarkastus
     :type :tarkastus
     :nimi (or (:nimi tarkastus) "Laaduntarkastus")
     :alue (oletusalue tarkastus on-valittu?))])

(defmethod asia-kartalle :toteuma [toteuma on-valittu?]
  ;; Yhdellä reittipisteellä voidaan tehdä montaa asiaa, ja tämän takia yksi reittipiste voi tulla
  ;; monta kertaa fronttiin.
  (let [reittipisteet (map
                        (fn [[_ arvo]] (first arvo))
                        (group-by :id (:reittipisteet toteuma)))]
    [(assoc toteuma
       :type :toteuma
       :nimi (or (:nimi toteuma)
                 (get-in toteuma [:tehtava :nimi])
                 (if (> 1 (count (:tehtavat toteuma)))
                   (str (:toimenpide (first (:tehtavat toteuma))) " & ...")
                   (str (:toimenpide (first (:tehtavat toteuma))))))
       :alue {
              :type   :arrow-line
              :scale (if (on-valittu? toteuma) 0.8 0.5) ;; TODO: Vaihda tämä joksikin paremmaksi kun saadaan oikeat ikonit :)
              :points (mapv #(get-in % [:sijainti :coordinates]) (sort-by
                                                                   :aika
                                                                   pvm/ennen?
                                                                   reittipisteet))})]))

(defmethod asia-kartalle :turvallisuuspoikkeama [tp on-valittu?]
  [(assoc tp
     :type :turvallisuuspoikkeama
     :nimi (or (:nimi tp) "Turvallisuuspoikkeama")
     :alue (oletusalue tp on-valittu?))])

(defmethod asia-kartalle :paallystyskohde [pt on-valittu?]
  (mapv
    (fn [kohdeosa]
      (assoc kohdeosa
        :type :paallystyskohde
        :nimi (or (:nimi pt) "Päällystyskohde")
        :alue (:sijainti kohdeosa)))
    (:kohdeosat pt)))

(defmethod asia-kartalle :paikkaustoteuma [pt on-valittu?]
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

(defmethod asia-kartalle :tyokone [tyokone on-valittu?]
  (assoc tyokone
    :type :tyokone
    :nimi (or (:nimi tyokone) (str/capitalize (name (:tyokonetyyppi tyokone))))
    :alue {:type        :icon
           :coordinates (:sijainti tyokone)
           :direction   (- (suunta-radiaaneina tyokone))
           :img         "images/tyokone.png"}))

(defmethod asia-kartalle :default [_ _ _])

(defn on-valittu? [valittu tunniste asia]
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
  ([asia valittu tunniste] (asia-kartalle asia (partial on-valittu? valittu tunniste))))