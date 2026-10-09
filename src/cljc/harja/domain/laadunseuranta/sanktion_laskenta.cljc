(ns harja.domain.laadunseuranta.sanktion-laskenta
  "Profiilin rakenteisesti määrittämät laskettavat sanktiosummat.
  Palvelin tuntee vain alla olevat laskentatavat. Profiilin ohjetekstiä ei koskaan tulkita kaavana."
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str])
  #?(:clj (:import (java.math RoundingMode))))

(def laskentatavat #{:tiekm-yksikkohinta :prosenttiosuus-syotteesta})
(def nykyinen-laskentaversio 1)

(def palvelimen-tukema-desimaalimaara 2)

(s/def ::syoteavain (s/and string? (complement str/blank?)))
(s/def ::yksikko (s/and string? (complement str/blank?)))
(s/def ::desimaalit (s/int-in 0 (inc palvelimen-tukema-desimaalimaara)))
(s/def ::minimi number?)
(s/def ::maksimi number?)
(s/def ::yksikkohinta (s/and number? pos?))
(s/def ::prosentti (s/and number? pos? #(<= % 100)))
(s/def ::laskentaversio (s/and int? pos?))

(s/def ::tiekm-yksikkohinta-parametrit
  (s/keys :req-un [::syoteavain ::yksikko ::desimaalit ::yksikkohinta]
    :opt-un [::minimi ::maksimi]))

(s/def ::prosenttiosuus-syotteesta-parametrit
  (s/keys :req-un [::syoteavain ::yksikko ::desimaalit ::prosentti]
    :opt-un [::minimi ::maksimi]))

(def laskentatavan-parametrit-spec
  {:tiekm-yksikkohinta ::tiekm-yksikkohinta-parametrit
   :prosenttiosuus-syotteesta ::prosenttiosuus-syotteesta-parametrit})

(defn laskettu-summamaaritys
  "Palauttaa sanktiotyypin rakenteisen laskettu-määrityksen tai nil. Ohjetekstiä ei tulkita."
  [{:keys [summamaaritykset]}]
  (some #(when (= :laskettu (:maaritystapa %)) %) summamaaritykset))

(defn laske-summa
  "Laskee summan validoidusta syötteestä ja profiilin parametreista kahteen desimaaliin (HALF_UP).
  Ei validoi: palvelin validoi syötteen ennen kutsua. Selaimessa tulos on vain esikatselu."
  [{:keys [laskentatapa laskentaparametrit]} syote]
  (let [{:keys [yksikkohinta prosentti]} laskentaparametrit]
    #?(:clj (let [syote (bigdec (str syote))
                  tulos (case laskentatapa
                          :tiekm-yksikkohinta (.multiply syote (bigdec (str yksikkohinta)))
                          :prosenttiosuus-syotteesta (.movePointLeft (.multiply syote (bigdec (str prosentti))) 2))]
              (.setScale tulos palvelimen-tukema-desimaalimaara RoundingMode/HALF_UP))
       :cljs (let [tulos (case laskentatapa
                           :tiekm-yksikkohinta (* syote yksikkohinta)
                           :prosenttiosuus-syotteesta (/ (* syote prosentti) 100))]
               (/ (js/Math.round (* tulos 100)) 100)))))

;; Lomakkeen apufunktiot. Selaimen tulos on vain esikatselu; palvelin laskee tallennettavan summan.

(def ^:private syoteavainten-otsikot {"tiekm" "Tiekm"})

(defn syotteen-otsikko
  "Syötekentän otsikko profiilin laskentaparametreista muodossa 'Nimi (yksikkö)'."
  [{:keys [syoteavain yksikko]}]
  (let [teksti (str/replace syoteavain "_" " ")
        nimi (or (get syoteavainten-otsikot syoteavain)
               (str (str/upper-case (subs teksti 0 1)) (subs teksti 1)))]
    (str nimi
      (when-not (contains? syoteavainten-otsikot syoteavain)
        (str " (" yksikko ")")))))

(defn syotteen-virhe
  "Selaimen esitarkistus, joka peilaa palvelimen syötesääntöjä. Palauttaa virheviestin tai nil.
  Palvelin validoi syötteen aina uudelleen."
  [{:keys [minimi maksimi] :as laskentaparametrit} syote]
  (let [desimaalit (or (:desimaalit laskentaparametrit) 0)
        kerroin (Math/pow 10 desimaalit)]
    (cond
      (not (number? syote)) "Anna arvo"
      (not (and (== syote syote) (< ##-Inf syote ##Inf))) "Anna äärellinen luku"
      (not (pos? syote)) "Arvon pitää olla nollaa suurempi"
      (not (== syote (/ (Math/floor (+ 0.5 (* syote kerroin))) kerroin)))
      (if (zero? desimaalit)
        "Arvon pitää olla kokonaisluku"
        (str "Arvossa saa olla enintään " desimaalit " desimaalia"))
      (and minimi (< syote minimi)) (str "Arvon pitää olla vähintään " minimi)
      (and maksimi (> syote maksimi)) (str "Arvon pitää olla enintään " maksimi))))

(defn laskettava-syote
  "Lomakkeen laskettavan sanktion syöte: käyttäjän muokkaus tai palvelimelta luettu snapshot."
  [sanktio]
  (if (contains? sanktio :laskettava-syote)
    (:laskettava-syote sanktio)
    (get-in sanktio [:laskennan-syote :syote])))

(defn laskettu-tulos
  "Lomakkeella näytettävä laskettu summa. Muokatusta syötteestä esikatselu, muuten palvelimen tallentama summa."
  [{:keys [laskentaparametrit] :as maaritys} {:keys [laskennan-syote summa] :as sanktio}]
  (if (contains? sanktio :laskettava-syote)
    (let [syote (:laskettava-syote sanktio)]
      (when (nil? (syotteen-virhe laskentaparametrit syote))
        (laske-summa maaritys syote)))
    (when (and laskennan-syote (number? summa))
      (if (neg? summa) (- summa) summa))))

(defn manuaalinen-laskettavan-sisartyyppi?
  "Tosi, kun tyypillä ei ole profiilissa summamääritystä, mutta saman sanktiolajin jokin toinen tyyppi on laskettava."
  [sanktiotyyppi lajin-sanktiotyypit]
  (boolean
    (and sanktiotyyppi
      (empty? (:summamaaritykset sanktiotyyppi))
      (some laskettu-summamaaritys lajin-sanktiotyypit))))

#?(:clj
   (do
     (defn- tyyppi-tekstina [arvo]
       (if (nil? arvo) "nil" (.getName (class arvo))))

     (defn- heita-virhe!
       [kentta odotettu saatu]
       (throw (IllegalArgumentException.
                (str "Kenttä " kentta " on virheellinen: odotettu " odotettu
                  ", saatu " (pr-str saatu) " (tyyppi " (tyyppi-tekstina saatu) ")."))))

     (defn- ongelman-kentta [{:keys [in pred]}]
       (or (some-> (first in) name)
         (second (re-find #"contains\? % :([\w-]+)" (pr-str pred)))))

     (defn- vaadi-tunnettu-laskentatapa [laskentatapa]
       (when-not (contains? laskentatavat laskentatapa)
         (throw (IllegalArgumentException.
                  (str "Kenttä laskentatapa on virheellinen: odotettu jokin arvoista " (sort laskentatavat)
                    ", saatu " (pr-str laskentatapa) " (tyyppi " (tyyppi-tekstina laskentatapa) ").")))))

     (defn- vaadi-laskentaversio [laskentaversio]
       (when-not (s/valid? ::laskentaversio laskentaversio)
         (heita-virhe! "laskentaversio" "positiivinen kokonaisluku" laskentaversio)))

     (defn- vaadi-laskentaparametrit [laskentatapa laskentaparametrit]
       (let [spec (laskentatavan-parametrit-spec laskentatapa)]
         (when-not (s/valid? spec laskentaparametrit)
           (let [ongelmat (::s/problems (s/explain-data spec laskentaparametrit))
                 kentat (->> ongelmat (keep ongelman-kentta) distinct sort (str/join ", "))]
             (throw (IllegalArgumentException.
                      (str "Kenttä laskentaparametrit on virheellinen"
                        (when-not (str/blank? kentat) (str " (" kentat ")"))
                        ": odotettu laskentatavan " (name laskentatapa) " mukainen parametrimap, saatu "
                        (pr-str laskentaparametrit)
                        " (tyyppi " (tyyppi-tekstina laskentaparametrit) ").")))))))

     (defn- vaadi-syote
       "Palauttaa validoidun syötteen BigDecimalina."
       [{:keys [syoteavain desimaalit minimi maksimi]} syote]
       (when-not (and (number? syote) (not (ratio? syote)))
         (heita-virhe! syoteavain "skalaarinen numero" syote))
       (when-not (or (decimal? syote) (integer? syote) (Double/isFinite (double syote)))
         (heita-virhe! syoteavain "äärellinen numero" syote))
       (let [arvo (bigdec (str syote))]
         (when-not (pos? arvo)
           (heita-virhe! syoteavain "nollaa suurempi numero" syote))
         (when (> (.scale (.stripTrailingZeros arvo)) desimaalit)
           (heita-virhe! syoteavain (str "numero, jossa on enintään " desimaalit " desimaalia") syote))
         (when (and minimi (< arvo (bigdec (str minimi))))
           (heita-virhe! syoteavain (str "numero, joka on vähintään " minimi) syote))
         (when (and maksimi (> arvo (bigdec (str maksimi))))
           (heita-virhe! syoteavain (str "numero, joka on enintään " maksimi) syote))
         arvo))

     (defn syote-vastaa-snapshotia?
       "Tosi, kun pyynnön raakasyöte on numeerisesti sama kuin tallennetun snapshotin syöte.
       Muun kuin äärellisen skalaarisen numeron kohdalla epätosi, jolloin syöte validoidaan normaalisti."
       [snapshot syote]
       (let [vertailtava? (fn [x] (and (number? x) (not (ratio? x))
                                    (or (decimal? x) (integer? x) (Double/isFinite (double x)))))
             tallennettu (:syote snapshot)]
         (boolean
           (and (vertailtava? syote) (vertailtava? tallennettu)
             (zero? (compare (bigdec (str syote)) (bigdec (str tallennettu))))))))

     (defn snapshot-vastaa-maaritysta?
       "Tosi, kun tallennetun snapshotin laskentatapa, versio ja parametrit täsmäävät profiilin määritykseen."
       [snapshot {:keys [laskentatapa laskentaversio laskentaparametrit]}]
       (boolean
         (and (keyword? laskentatapa)
           (s/valid? ::laskentaversio laskentaversio)
           (s/valid? ::laskentaversio (:laskentaversio snapshot))
           (= laskentaversio (:laskentaversio snapshot))
           (= (str/replace (name laskentatapa) "-" "_") (:laskentatapa snapshot))
           (= laskentaparametrit (:laskentaparametrit snapshot)))))

     (defn laske-sanktion-summa
       "Validoi syötteen profiilin laskettu-määritystä vasten ja laskee summan.
       Palauttaa {:summa BigDecimal :laskennan-syote snapshot}. Heittää IllegalArgumentException virheellisestä syötteestä."
       [{:keys [laskentatapa laskentaversio laskentaparametrit]} syote]
       (vaadi-tunnettu-laskentatapa laskentatapa)
       (vaadi-laskentaversio laskentaversio)
       (vaadi-laskentaparametrit laskentatapa laskentaparametrit)
       (let [arvo (vaadi-syote laskentaparametrit syote)]
         {:summa (laske-summa {:laskentatapa laskentatapa :laskentaparametrit laskentaparametrit} arvo)
          :laskennan-syote {:syoteavain (:syoteavain laskentaparametrit)
                            :syote arvo
                            :yksikko (:yksikko laskentaparametrit)
                            :laskentaversio laskentaversio
                            :laskentatapa (str/replace (name laskentatapa) "-" "_")
                            :laskentaparametrit laskentaparametrit}}))))
