(ns harja.domain.tierekisteri
  (:require [clojure.spec.alpha :as s]
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

(defn normalisoi
  "Muuntaa ei-ns avaimet :harja.domain.tierekisteri avaimiksi."
  [osoite]
  (let [osoite (or osoite {})
        ks (fn [& avaimet]
             (some osoite avaimet))]
    {::tie (ks ::tie :numero :tr-numero :tie)
     ::aosa (ks ::aosa :alkuosa :tr-alkuosa :aosa)
     ::aet (ks ::aet :alkuetaisyys :tr-alkuetaisyys :aet)
     ::losa (ks ::losa :loppuosa :tr-loppuosa :losa)
     ::let (ks ::let :loppuetaisyys :tr-loppuetaisyys :let)}))

(defn samalla-tiella? [tie1 tie2]
  (= (::tie (normalisoi tie1)) (::tie (normalisoi tie2))))

(defn ennen?
  "Tarkistaa alkaako tie1 osa ennen tie2 osaa. Osien tulee olla samalla tienumerolla.
  Jos osat ovat eri teilla, palauttaa nil."
  [tie1 tie2]
  (let [tie1 (normalisoi tie1)
        tie2 (normalisoi tie2)])
  (when (samalla-tiella? tie1 tie2)
    (or (< (::aosa tie1) (::aosa tie2))
        (and (= (::aosa tie1) (::aosa tie2))
             (< (::aet tie1) (::aet tie2))))))

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
  (let [tie (normalisoi tie)]
    (and (integer? (::aosa tie))
         (integer? (::aet tie)))))

(defn on-loppu? [tie]
  (let [tie (normalisoi tie)]
    (and (integer? (::losa tie))
         (integer? (::let tie)))))

(defn on-alku-ja-loppu? [tie]
  (and (on-alku? tie)
       (on-loppu? tie)))

(defn laske-tien-pituus
  ([tie] (laske-tien-pituus {} tie))
  ([osien-pituudet {:keys [tr-alkuosa tr-alkuetaisyys tr-loppuosa tr-loppuetaisyys] :as tie}]
   (when (and (on-alku-ja-loppu? tie)
              (or (= tr-alkuosa tr-loppuosa) ;; Pituus voidaan laskean suoraan
                  (not (empty? osien-pituudet)))) ;; Tarvitaan osien pituudet laskuun
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

(defn validi-osoite? [osoite]
  (let [osoite (normalisoi osoite)]
    (and (some? (::tie osoite))
         (some? (::aosa osoite))
         (some? (::aet osoite)))))

(defn osa-olemassa-verkolla?
  "Tarkistaa, onko annettu osa olemassa Harjan tieverkolla (true / false)"
  [osa osien-pituudet]
  (number? (get osien-pituudet osa)))

(defn osan-pituus-sopiva-verkolla? [osa etaisyys osien-pituudet]
  "Tarkistaa, onko annettu osa sekä sen alku-/loppuetäisyys sopiva Harjan tieverkolla (true / false)"
  (if-let [osan-pituus (get osien-pituudet osa)]
    (and (<= etaisyys osan-pituus)
         (>= etaisyys 0))
    false))

(defn kohdeosa-kohteen-sisalla? [kohde kohdeosa]
  (and
    (number? (:tienumero kohde))
    (number? (:tienumero kohdeosa))
    (= (:tienumero kohdeosa) (:tienumero kohde))
       (>= (:aosa kohdeosa) (:aosa kohde))
       (>= (:aet kohdeosa) (:aet kohde))
       (<= (:losa kohdeosa) (:losa kohde))
       (<= (:let kohdeosa) (:let kohde))))

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

(defn tieosoitteen-jarjestys
  "Palauttaa vectorin TR-osoitteen tiedoista. Voidaan käyttää järjestämään tieosoitteet järjestykseen."
  [kohde]
  ((juxt :tie :tr-numero :tienumero
         :aosa :tr-alkuosa
         :aet :tr-alkuetaisyys) kohde))

(defn jarjesta-tiet
  "Järjestää kohteet tieosoitteiden mukaiseen järjestykseen"
  [tiet]
  (sort-by tieosoitteen-jarjestys tiet))

(defn jarjesta-kohteiden-kohdeosat
  "Palauttaa kohteet tieosoitteen mukaisessa järjestyksessä"
  [kohteet]
  (when kohteet
    (mapv
      (fn [kohde]
        (assoc kohde :kohdeosat (jarjesta-tiet (:kohdeosat kohde))))
      kohteet)))

(defn tie-rampilla?
  "Tarkistaa onko annettu tienumero ramppi. Rampit tunnistetaan tienumeron
  perusteella ja ne ovat välillä 20001-29999."
  [tie]
  (if (and tie (number? tie))
    (boolean (and (> tie 20000)
                  (< tie 30000)))
    false))
