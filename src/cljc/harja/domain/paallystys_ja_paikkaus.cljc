(ns harja.domain.paallystys-ja-paikkaus
  "Päällystys- ja paikkausurakoiden yhteisiä apureita"
  (:require [schema.core :as s]))

(def +paallystetyypit+
  "Kaikki päällystetyypit POT-lomake Excelistä"
  [{:nimi "Betoni" :lyhenne "BET" :koodi 1 :api-arvo "betoni"}
   {:nimi "Kivi" :lyhenne "KIVI" :koodi 2 :api-arvo "kivi"}
   {:nimi "Avoin asfaltti" :lyhenne "AA" :koodi 11 :api-arvo "avoin asfaltti"}
   {:nimi "Asfalttibetoni" :lyhenne "AB" :koodi 12 :api-arvo "asfalttibetoni"}
   {:nimi "Epäjatkuva asfaltti" :lyhenne "EA" :koodi 13 :api-arvo "epäjatkuva asfaltti"}
   {:nimi "Kivimastiksiasfaltti" :lyhenne "SMA" :koodi 14 :api-arvo "kivimastiksiasfaltti"}
   {:nimi "Kantavan kerroksen AB" :lyhenne "ABK" :koodi 15 :api-arvo "kantavan kerroksen AB"}
   {:nimi "Bit.sidottu kantava ker." :lyhenne "ABS" :koodi 16 :api-arvo "bit.sidottu kantava ker."}
   {:nimi "Valuasfaltti" :lyhenne "VA" :koodi 17 :api-arvo "valuasfaltti"}
   {:nimi "Pehmeä asfalttibetoni" :lyhenne "PAB-B" :koodi 21 :api-arvo "pehmeä asfalttibetoni (b)"}
   {:nimi "Pehmeä asfalttibetoni" :lyhenne "PAB-V" :koodi 22 :api-arvo "pehmeä asfalttibetoni (v)"}
   {:nimi "Pehmeä asfalttibetoni" :lyhenne "PAB-O" :koodi 23 :api-arvo "pehmeä asfalttibetoni (o)"}
   {:nimi "Sirotepintaus" :lyhenne "SIP" :koodi 24 :api-arvo "sirotepintaus"}
   {:nimi "Soratien pintaus" :lyhenne "SOP" :koodi 31 :api-arvo "soratien pintaus"}
   {:nimi "Sora" :lyhenne "SORA" :koodi 41 :api-arvo "sora"}])

(def +paallystetyypit-ja-nil+
  (conj +paallystetyypit+ {:nimi "Ei päällystetyyppiä" :lyhenne "Ei päällystetyyppiä" :koodi nil :api-arvo nil}))

(def +paallystetyyppi-tai-nil+ "Päällystetyypin valinta koodilla"
  (apply s/enum (map :koodi +paallystetyypit-ja-nil+)))

(defn hae-paallyste-koodilla
  "Hakee päällysteen nimen koodilla"
  [koodi]
  (:nimi (first (filter #(= (:koodi %) koodi) +paallystetyypit-ja-nil+))))

(defn hae-apin-paallyste-koodilla [koodi]
  "Hakee API:n päällysteen arvon koodilla"
  (:api-arvo (first (filter #(= (:koodi %) koodi) +paallystetyypit-ja-nil+))))

(defn hae-koodi-apin-paallysteella [koodi]
  "Hakee koodin API:n päällysteellä"
  (:koodi (first (filter #(= (:api-arvo %) koodi) +paallystetyypit-ja-nil+))))

(defn kuvaile-paatostyyppi [paatos]
  (case paatos
    :hyvaksytty "Hyväksytty"
    :hylatty "Hylätty"))

(defn kuvaile-ilmoituksen-tila [tila]
  (case tila
    :aloitettu "Aloitettu"
    :valmis "Valmis käsiteltäväksi"
    :lukittu "Lukittu"
    "-"))

(defn nayta-paatos [tila]
  (case tila
    :hyvaksytty [:span.ilmoitus-hyvaksytty (kuvaile-paatostyyppi tila)]
    :hylatty [:span.ilmoitus-hylatty (kuvaile-paatostyyppi tila)]
    [:span ""]))

(defn maaramuutoksissa-ennustettuja-maaria? [tyot]
  (boolean (some #(and (:ennustettu-maara %)
                       (not (:toteutunut-maara %)))
                 tyot)))

(defn summaa-maaramuutokset
  "Laskee ilmoitettujen töiden toteutumien erotuksen tilattuun määrään ja summaa tulokset yhteen.
   Palauttaa mapin, jossa laskun tulos sekä tieto siitä, sisältääkö lasku ennustettuja määriä."
  [tyot]
  {:tulos (if tyot
            (reduce + 0
              (map
                (fn [tyo]

                  (let [tilattu-maara (:tilattu-maara tyo)
                        ennustettu-maara (:ennustettu-maara tyo)
                        toteutunut-maara (:toteutunut-maara tyo)
                        yksikkohinta (:yksikkohinta tyo)]
                    (* (- (or toteutunut-maara ennustettu-maara)
                          tilattu-maara)
                       yksikkohinta)))
                (filter #(not= true (:poistettu %)) tyot)))
            0)
   :ennustettu? (maaramuutoksissa-ennustettuja-maaria? tyot)})
