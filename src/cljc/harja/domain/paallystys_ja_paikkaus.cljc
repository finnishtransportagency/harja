(ns harja.domain.paallystys-ja-paikkaus
  "Päällystys- ja paikkausurakoiden yhteisiä apureita"
  (:require [schema.core :as s]))

(def +paallystetyypit+
  "Kaikki päällystetyypit POT-lomake Excelistä"
  [{:nimi "Betoni" :lyhenne "BET" :koodi 1}
   {:nimi "Kivi" :lyhenne "KIVI" :koodi 2}
   {:nimi "Avoin asfaltti" :lyhenne "AA" :koodi 11}
   {:nimi "Asfalttibetoni" :lyhenne "AB" :koodi 12}
   {:nimi "Epäjatkuva asfaltti" :lyhenne "EA" :koodi 13}
   {:nimi "Kivimastiksiasfaltti" :lyhenne "SMA" :koodi 14}
   {:nimi "Kantavan kerroksen AB" :lyhenne "ABK" :koodi 15}
   {:nimi "Bit.sidottu kantava ker." :lyhenne "ABS" :koodi 16}
   {:nimi "Valuasfaltti" :lyhenne "VA" :koodi 17}
   {:nimi "Pehmeä asfalttibetoni" :lyhenne "PAB-B" :koodi 21}
   {:nimi "Pehmeä asfalttibetoni" :lyhenne "PAB-V" :koodi 22}
   {:nimi "Pehmeä asfalttibetoni" :lyhenne "PAB-O" :koodi 23}
   {:nimi "Sirotepintaus" :lyhenne "SIP" :koodi 24}
   {:nimi "Soratien pintaus" :lyhenne "SOP" :koodi 31}
   {:nimi "Sora" :lyhenne "SORA" :koodi 41}])

(defn hae-paallyste-koodilla
  "Hakee päällysteen nimen koodilla"
  [koodi]
  (:nimi (first (filter #(= (:koodi %) koodi) +paallystetyypit+))))

(defn kuvaile-paatostyyppi [paatos]
  (case paatos
    :hyvaksytty "Hyväksytty"
    :hylatty "Hylätty"))

(defn nayta-tila [tila]
  (case tila
    :aloitettu "Aloitettu"
    :valmis "Valmis"
    :lukittu "Lukittu"
    "-"))

(defn nayta-paatos [tila]
  (case tila
    :hyvaksytty [:span.ilmoitus-hyvaksytty (kuvaile-paatostyyppi tila)]
    :hylatty [:span.ilmoitus-hylatty (kuvaile-paatostyyppi tila)]
    ""))

(def +paallystetyyppi+ "Päällystetyypin valinta koodilla"
  (apply s/enum (map :koodi +paallystetyypit+)))