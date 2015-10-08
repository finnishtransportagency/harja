(ns harja.ui.kartta.esitettavat-asiat
  (:require [harja.pvm :as pvm]
            [clojure.string :as str]))

(defn oletusalue [asia]
  (merge
    (:sijainti asia)
    {:color  "green"
     :radius 300
     :stroke {:color "black" :width 10}}))

(defmulti kartalla-xf :tyyppi-kartalla)

(defmethod kartalla-xf :tiedoitus [ilmoitus]
  [(assoc ilmoitus
     :type :ilmoitus
     :nimi (or (:nimi ilmoitus) "Tiedotus")
     :alue (oletusalue ilmoitus))])

(defmethod kartalla-xf :kysely [ilmoitus]
  [(assoc ilmoitus
     :type :ilmoitus
     :nimi (or (:nimi ilmoitus) "Kysely")
     :alue (oletusalue ilmoitus))])

(defmethod kartalla-xf :toimenpidepyynto [ilmoitus]
  [(assoc ilmoitus
     :type :ilmoitus
     :nimi (or (:nimi ilmoitus) "Toimenpidepyyntö")
     :alue (oletusalue ilmoitus))])

(defmethod kartalla-xf :havainto [havainto]
  [(assoc havainto
     :type :havainto
     :nimi (or (:nimi havainto) "Havainto")
     :alue (oletusalue havainto))])

(defmethod kartalla-xf :pistokoe [tarkastus]
  [(assoc tarkastus
     :type :tarkastus
     :nimi (or (:nimi tarkastus) "Pistokoe")
     :alue (oletusalue tarkastus))])

(defmethod kartalla-xf :laaduntarkastus [tarkastus]
  [(assoc tarkastus
     :type :tarkastus
     :nimi (or (:nimi tarkastus) "Laaduntarkastus")
     :alue (oletusalue tarkastus))])

(defmethod kartalla-xf :toteuma [toteuma]
  ;; Yhdellä reittipisteellä voidaan tehdä montaa asiaa, ja tämän takia yksi reittipiste voi tulla
  ;; monta kertaa fronttiin.
  (let [reittipisteet (map
                        (fn [[_ arvo]] (first arvo))
                        (group-by :id (:reittipisteet toteuma)))]
    [(assoc toteuma
       :type :toteuma
       :nimi (or (:nimi toteuma) (if (> 1 (count (:tehtavat toteuma)))
                                   (str (:toimenpide (first (:tehtavat toteuma))) " & ...")
                                   (str (:toimenpide (first (:tehtavat toteuma))))))
       :alue {
              :type   :arrow-line
              :points (mapv #(get-in % [:sijainti :coordinates]) (sort-by
                                                                   :aika
                                                                   pvm/ennen?
                                                                   reittipisteet))})]))

(defmethod kartalla-xf :turvallisuuspoikkeama [tp]
  [(assoc tp
     :type :turvallisuuspoikkeama
     :nimi (or (:nimi tp) "Turvallisuuspoikkeama")
     :alue (oletusalue tp))])

(defmethod kartalla-xf :paallystyskohde [pt]
  (mapv
    (fn [kohdeosa]
      (assoc kohdeosa
        :type :paallystyskohde
        :nimi (or (:nimi pt) "Päällystyskohde")
        :alue (:sijainti kohdeosa)))
    (:kohdeosat pt)))

(defmethod kartalla-xf :paikkaustoteuma [pt]
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

(defmethod kartalla-xf :tyokone [tyokone]
  (assoc tyokone
    :type :tyokone
    :nimi (or (:nimi tyokone) (str/capitalize (name (:tyokonetyyppi tyokone))))
    :alue {:type        :icon
           :coordinates (:sijainti tyokone)
           :direction   (- (suunta-radiaaneina tyokone))
           :img         "images/tyokone.png"}))

(defmethod kartalla-xf :default [_])