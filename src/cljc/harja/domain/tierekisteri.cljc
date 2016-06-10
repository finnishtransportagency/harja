(ns harja.domain.tierekisteri
  (:require [schema.core :as s]
            [clojure.string :as str]
    #?@(:cljs [[harja.loki :refer [log]]])))

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
   Jos losa tai let puuttuu, ei näytetä niitä.

   Optiot on mappi, jossa voi olla arvot:
   nayta-teksti-ei-tr-osoitetta?        Näyttää tekstin jos TR-osoite puuttuu. Oletus true.
   nayta-teksti-tie?                    Näyttää sanan 'Tie' osoitteen edessä. Oletus true."
  ([tr] (tierekisteriosoite-tekstina tr {}))
  ([tr optiot]
   (let [tie-sana (let [sana "Tie "]
                    (if (nil? (:nayta-teksti-tie? optiot))
                      sana
                      (when (:nayta-teksti-tie? optiot) sana)))
         tie (or (:numero tr) (:tr-numero tr) (:tie tr))
         alkuosa (or (:alkuosa tr) (:tr-alkuosa tr) (:aosa tr))
         alkuetaisyys (or (:alkuetaisyys tr) (:tr-alkuetaisyys tr) (:aet tr))
         loppuosa (or (:loppuosa tr) (:tr-loppuosa tr) (:losa tr))
         loppuetaisyys (or (:loppuetaisyys tr) (:tr-loppuetaisyys tr) (:let tr))
         ei-tierekisteriosoitetta (let [lause "Ei tierekisteriosoitetta"]
                                    (if (nil? (:nayta-teksti-ei-tr-osoitetta? optiot))
                                      lause
                                      (when (:nayta-teksti-ei-tr-osoitetta? optiot) lause)))]
     ;; Muodosta teksti
     (str (if tie
            (str tie-sana
                 tie " / "
                 alkuosa " / "
                 alkuetaisyys
                 (when (and loppuetaisyys loppuosa) " / " loppuosa " / " loppuetaisyys))
            ei-tierekisteriosoitetta)))))


(defn yllapitokohde-tekstina
  "Näyttää ylläpitokohteen kohdenumeron ja nimen.

  Optiot on map, jossa voi olla arvot:
  nayta-osoite?           Näyttää osoitteen sulkeissa kohteen tietojen perässä sulkeissa, jos osoite löytyy."
  ([kohde] (yllapitokohde-tekstina kohde {}))
  ([kohde optiot]
   (let [kohdenumero (or (:kohdenumero kohde) (:yllapitokohdenumero kohde))
         nimi (or (:nimi kohde) (:yllapitokohdenimi kohde))
         osoite (when (:nayta-osoite? optiot)
                  (let [tr-osoite (tierekisteriosoite-tekstina kohde optiot)]
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
  (mapv
    (fn [kohde]
      (assoc kohde :kohdeosat (sort-by tiekohteiden-jarjestys (:kohdeosat kohde))))
    kohteet))
