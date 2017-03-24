(ns harja.domain.tierekisteri
  (:require [clojure.spec :as s]
            [harja.tyokalut.spec-apurit :as spec-apurit]
            [clojure.string :as str]
            #?@(:clj [[clojure.future :refer :all]])))

;; Osan tiedot
(s/def ::osa (s/and pos-int? #(< % 1000)))
(s/def ::etaisyys (s/and nat-int? #(< % 50000)))

;; Tien tiedot
(s/def ::numero (s/and pos-int? #(< % 100000)))
(s/def ::alkuosa  ::osa)
(s/def ::alkuetaisyys ::etaisyys)
(s/def ::loppuosa ::osa)
(s/def ::loppuetaisyys ::etaisyys)

;; Yleiset suureet
(s/def ::pituus (s/and int? #(s/int-in-range? 1 spec-apurit/postgres-int-max %)))

;; Halutaan tierekisteriosoite, joka voi olla pistemäinen tai sisältää myös
;; loppuosan ja loppuetäisyyden.
;; Tämä voitaisiin s/or tehdä ja tarkistaa että pistemäisessä EI ole loppuosa/-etäisyys
;; kenttiä, mutta se johtaa epäselviin validointiongelmiin.
(s/def ::tierekisteriosoite
  (s/keys :req-un [::numero
                   ::alkuosa ::alkuetaisyys]
          :opt-un [::loppuosa ::loppuetaisyys]))

(defn samalla-tiella? [tie1 tie2]
  (= (:tr-numero tie1) (:tr-numero tie2)))

(defn ennen?
  "Tarkistaa alkaako tie1 osa ennen tie2 osaa. Osien tulee olla samalla tienumerolla.
  Jos osat ovat eri teilla, palauttaa nil."
  [tie1 tie2]
  (when (samalla-tiella? tie1 tie2)
    (or (< (:tr-alkuosa tie1) (:tr-alkuosa tie2))
        (and (= (:tr-alkuosa tie1) (:tr-alkuosa tie2))
             (< (:tr-alkuetaisyys tie1) (:tr-alkuetaisyys tie2))))))

(defn alku
  "Palauttaa annetun tien alkuosan ja alkuetäisyyden vektorina"
  [{:keys [tr-alkuosa tr-alkuetaisyys]}]
  [tr-alkuosa tr-alkuetaisyys])

(defn loppu
  "Palauttaa annetun tien loppuosan ja loppuetäisyyden vektorina"
  [{:keys [tr-loppuosa tr-loppuetaisyys]}]
  [tr-loppuosa tr-loppuetaisyys])

(defn jalkeen?
  "Tarkistaa loppuuko tie1 osa ennen tie2 osaa. Osien tulee olla samalla tienumerolla.
  Jos osat ovat eri teillä, palauttaa nil."
  [tie1 tie2]
  (when (samalla-tiella? tie1 tie2)
    (or (> (:tr-loppuosa tie1) (:tr-loppuosa tie2))
        (and (= (:tr-loppuosa tie1) (:tr-loppuosa tie2))
             (> (:tr-loppuetaisyys tie1) (:tr-loppuetaisyys tie2))))))

(defn nouseva-jarjestys
  "Tarkistaa, että annettu osoite on nousevassa järjestyksessä (alku ennen loppua) ja
  kääntää alkuosan ja loppuosan, jos ei ole. Palauttaa mahdollisesti muokatun osoitteen."
  [{:keys [tr-alkuosa tr-alkuetaisyys tr-loppuosa tr-loppuetaisyys] :as osoite}]
  (cond
    (< tr-loppuosa tr-alkuosa)
    (assoc osoite
      :tr-alkuosa tr-loppuosa
      :tr-alkuetaisyys tr-loppuetaisyys
      :tr-loppuosa tr-alkuosa
      :tr-loppuetaisyys tr-alkuetaisyys)

    (and (= tr-loppuosa tr-alkuosa)
         (< tr-loppuetaisyys tr-alkuetaisyys))
    (assoc osoite
      :tr-loppuetaisyys tr-alkuetaisyys
      :tr-alkuetaisyys tr-loppuetaisyys)

    :default
    osoite))

(defn on-alku? [tie]
  (and (integer? (:tr-alkuosa tie))
       (integer? (:tr-alkuetaisyys tie))))

(defn on-loppu? [tie]
  (and (integer? (:tr-loppuosa tie))
       (integer? (:tr-loppuetaisyys tie))))

(defn on-alku-ja-loppu? [tie]
  (and (on-alku? tie)
       (on-loppu? tie)))

(defn laske-tien-pituus
  ([tie] (laske-tien-pituus {} tie))
  ([osien-pituudet tie]
   (when (on-alku-ja-loppu? tie)
     (let [{aosa :tr-alkuosa
            alkuet :tr-alkuetaisyys
            losa :tr-loppuosa
            loppuet :tr-loppuetaisyys} (nouseva-jarjestys tie)]
       (if (= aosa losa)
         (Math/abs (- loppuet alkuet))
         (let [max-osa (reduce max 0 (keys osien-pituudet))
               losa (min losa max-osa)]
           (loop [pituus (- (get osien-pituudet aosa 0) alkuet)
                  osa (inc aosa)]
             (let [osan-pituus (get osien-pituudet osa 0)]
               (if (>= osa losa)
                 (+ pituus (min loppuet osan-pituus))

                 (recur (+ pituus osan-pituus)
                        (inc osa)))))))))))

(defn tierekisteriosoite-tekstina
  "Näyttää tierekisteriosoitteen muodossa tie / aosa / aet / losa / let
   Vähintään tie, aosa ja aet tulee löytyä osoitteesta, jotta se näytetään

   Optiot on mappi, jossa voi olla arvot:
   teksti-ei-tr-osoitetta?        Näyttää tekstin jos TR-osoite puuttuu. Oletus true.
   teksti-tie?                    Näyttää sanan 'Tie' osoitteen edessä. Oletus true."
  ([tr] (tierekisteriosoite-tekstina tr {}))
  ([tr optiot]
   (let [tie-sana (let [sana "Tie "]
                    (if (nil? (:teksti-tie? optiot))
                      sana
                      (when (:teksti-tie? optiot) sana)))
         tie (or (:numero tr) (:tr-numero tr) (:tie tr) (::tie tr))
         alkuosa (or (:alkuosa tr) (:tr-alkuosa tr) (:aosa tr) (::aosa tr))
         alkuetaisyys (or (:alkuetaisyys tr) (:tr-alkuetaisyys tr) (:aet tr) (::aet tr))
         loppuosa (or (:loppuosa tr) (:tr-loppuosa tr) (:losa tr) (::losa tr))
         loppuetaisyys (or (:loppuetaisyys tr) (:tr-loppuetaisyys tr) (:let tr) (::let tr))
         ei-tierekisteriosoitetta (if (or (nil? (:teksti-ei-tr-osoitetta? optiot))
                                          (boolean (:teksti-ei-tr-osoitetta? optiot)))
                                    "Ei tierekisteriosoitetta"
                                    "")]
     ;; Muodosta teksti
     (str (if tie
            (str tie-sana
                 tie
                 (when (and alkuosa alkuetaisyys)
                   (str " / " alkuosa " / " alkuetaisyys))
                 (when (and alkuosa alkuetaisyys loppuosa loppuetaisyys)
                   (str " / " loppuosa " / " loppuetaisyys)))
            ei-tierekisteriosoitetta)))))


(defn yllapitokohde-tekstina
  "Näyttää ylläpitokohteen kohdenumeron ja nimen.

  Optiot on map, jossa voi olla arvot:
  osoite              Kohteen tierekisteriosoite.
                      Näytetään sulkeissa kohteen tietojen perässä sulkeissa, jos löytyy."
  ([kohde] (yllapitokohde-tekstina kohde {}))
  ([kohde optiot]
   (let [kohdenumero (or (:kohdenumero kohde) (:numero kohde) (:yllapitokohdenumero kohde))
         nimi (or (:nimi kohde) (:yllapitokohdenimi kohde))
         osoite (when-let [osoite (:osoite optiot)]
                  (let [tr-osoite (tierekisteriosoite-tekstina osoite {:teksti-ei-tr-osoitetta? false
                                                                       :teksti-tie? false})]
                    (when-not (empty? tr-osoite)
                      (str " (" tr-osoite ")"))))]
     (str kohdenumero " " nimi osoite))))

(defn tiekohteiden-jarjestys
  "Palauttaa vectorin TR-osoitteen tiedoista. Voidaan käyttää järjestämään tieosoitteet järjestykseen."
  [kohde]
  ((juxt :tie :tr-numero :tienumero
         :aosa :tr-alkuosa
         :aet :tr-alkuetaisyys) kohde))

(defn jarjesta-kohteiden-kohdeosat
  "Palauttaa kohteet tieosoitteen mukaisessa järjestyksessä"
  [kohteet]
  (when kohteet
    (mapv
      (fn [kohde]
        (assoc kohde :kohdeosat (sort-by tiekohteiden-jarjestys (:kohdeosat kohde))))
      kohteet)))

(defn tie-rampilla?
  "Tarkistaa onko annettu tienumero ramppi. Rampit tunnistetaan tienumeron
  perusteella ja ne ovat välillä 20001-29999."
  [tie]
  (if (and tie (number? tie))
    (boolean (and (> tie 20000)
                  (< tie 30000)))
    false))
