(ns harja.kyselyt.sanktio-konfiguraatio
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [harja.domain.laadunseuranta.sanktion-laskenta :as sanktion-laskenta]
            [harja.kyselyt.konversio :as konv]
            [jeesql.core :refer [defqueries]]))

(declare hae-urakan-sanktio-profiilit hae-sanktio-profiilit-admin hae-sanktio-profiili-admin
  hae-sanktio-profiilin-rivit hae-sanktio-profiilin-rivit-admin)

(defn- muunna-urakkatyyppi
  [rivi avainpolku]
  (if (get-in rivi avainpolku)
    (update-in rivi avainpolku keyword)
    rivi))

(defn- muunna-soveltuvuuskontekstit
  [rivi]
  (cond-> rivi
    (:soveltuvuuskontekstit rivi)
    (->
      (konv/array->vec :soveltuvuuskontekstit)
      (update :soveltuvuuskontekstit #(mapv keyword %)))))

(defn- normalisoi-vektoriksi
  [arvo]
  (cond
    (nil? arvo) []
    (vector? arvo) arvo
    (instance? java.sql.Array arvo) (vec (.getArray ^java.sql.Array arvo))
    :else [arvo]))

(defn- normalisoi-euromaara
  [arvo]
  (cond
    (nil? arvo) nil
    (instance? java.math.BigDecimal arvo) arvo
    (number? arvo) (bigdec (str arvo))
    :else arvo))

(defn- normalisoi-maaritystapa
  [arvo]
  (when arvo
    (-> arvo
      name
      (str/replace "_" "-")
      keyword)))

(defn- tyyppi-tekstina
  [arvo]
  (if (nil? arvo) "nil" (.getName (class arvo))))

(defn- laskentatapa-avaimeksi
  [arvo]
  (when (string? arvo)
    (normalisoi-maaritystapa arvo)))

(defn- vaadi-laskettu-summamaaritys
  "Validoi tietokannasta luetun laskettu-määrityksen muodon ennen sen käyttöä. Heittää virheen kentän nimellä."
  [{:keys [laskentatapa laskentaversio laskentaparametrit]} raaka-laskentatapa]
  (when-not (contains? sanktion-laskenta/laskentatavat laskentatapa)
    (throw (IllegalStateException.
             (str "Kenttä laskentatapa on virheellinen: odotettu jokin arvoista "
               (sort sanktion-laskenta/laskentatavat) ", saatu " (pr-str raaka-laskentatapa)
               " (tyyppi " (tyyppi-tekstina raaka-laskentatapa) ")."))))
  (when-not (s/valid? (sanktion-laskenta/laskentatavan-parametrit-spec laskentatapa) laskentaparametrit)
    (throw (IllegalStateException.
             (str "Kenttä laskentaparametrit on virheellinen: odotettu laskentatavan " (name laskentatapa)
               " mukainen parametrimap, saatu " (pr-str laskentaparametrit)
               " (tyyppi " (tyyppi-tekstina laskentaparametrit) ")."))))
  (when-not (s/valid? ::sanktion-laskenta/laskentaversio laskentaversio)
    (throw (IllegalStateException.
             (str "Kenttä laskentaversio on virheellinen: odotettu positiivinen kokonaisluku, saatu "
               (pr-str laskentaversio) " (tyyppi " (tyyppi-tekstina laskentaversio) ").")))))

(defn- normalisoi-summamaaritys
  [{:keys [laskentaversio laskentaparametrit] :as summamaaritys}]
  (let [maaritystapa (normalisoi-maaritystapa (:maaritystapa summamaaritys))
        perusrivi {:maaritystapa maaritystapa
                   :summa-euroina (normalisoi-euromaara (:summa_euroina summamaaritys))
                   :ohjeteksti (:ohjeteksti summamaaritys)
                   :jarjestys (:jarjestys summamaaritys)}]
    (if (= :laskettu maaritystapa)
      (let [laskentatapa (laskentatapa-avaimeksi (:laskentatapa summamaaritys))
            laskettu {:laskentatapa laskentatapa
                      :laskentaversio laskentaversio
                      :laskentaparametrit laskentaparametrit}]
        (vaadi-laskettu-summamaaritys laskettu (:laskentatapa summamaaritys))
        (merge perusrivi laskettu))
      perusrivi)))

(defn- normalisoi-summamaaritykset
  [summamaaritykset]
  (some->> summamaaritykset
    konv/jsonb->clojuremap
    (mapv normalisoi-summamaaritys)))

(defn- lukitut-summat-summamaarityksista
  [summamaaritykset]
  (->> summamaaritykset
    (keep (fn [{:keys [maaritystapa summa-euroina]}]
            (when (= :automaattinen maaritystapa)
              summa-euroina)))
    vec))

;; Kaytossa jeesql:ssa row-fn-muuntimina.
(defn muunna-sanktio-profiili
  [{:as rivi}]
  (let [rivi (konv/alaviiva->rakenne rivi)]
    (muunna-urakkatyyppi rivi [:urakkatyyppi])))

(defn muunna-sanktio-profiili-admin-listarivi
  [{:as rivi}]
  (-> rivi
    konv/alaviiva->rakenne
    (muunna-urakkatyyppi [:urakkatyyppi])
    muunna-soveltuvuuskontekstit))

(defn- normalisoi-profiilirivin-metatiedot
  [rivi]
  (let [voi-puolittaa-omailmoituksella (or (get-in rivi [:profiilirivi :voi-puolittaa-omailmoituksella])
                                         (get-in rivi [:profiilirivi :voi :puolittaa :omailmoituksella]))
        summamaaritykset (normalisoi-summamaaritykset (get-in rivi [:profiilirivi :summamaaritykset]))
        lukitut-summat (or (get-in rivi [:profiilirivi :lukitut-summat])
                         (get-in rivi [:profiilirivi :lukitut :summat])
                         (when (seq summamaaritykset)
                           (lukitut-summat-summamaarityksista summamaaritykset)))]
    (cond-> rivi
      (some? voi-puolittaa-omailmoituksella)
      (assoc-in [:profiilirivi :voi-puolittaa-omailmoituksella] voi-puolittaa-omailmoituksella)

      (some? summamaaritykset)
      (assoc-in [:profiilirivi :summamaaritykset] summamaaritykset)

      (some? lukitut-summat)
      (assoc-in [:profiilirivi :lukitut-summat] lukitut-summat)

      (get-in rivi [:profiilirivi :voi])
      (update :profiilirivi dissoc :voi)

      (get-in rivi [:profiilirivi :lukitut])
      (update :profiilirivi dissoc :lukitut))))

(defn muunna-sanktio-konfiguraatiorivi
  [{:as rivi}]
  (let [rivi (-> rivi
               konv/alaviiva->rakenne
               normalisoi-profiilirivin-metatiedot)]
    (cond-> rivi
      (get-in rivi [:profiilirivi :summamaaritykset])
      (update-in [:profiilirivi :summamaaritykset]
        (comp vec (partial sort-by :jarjestys)))

      (get-in rivi [:profiilirivi :lukitut-summat])
      (update-in [:profiilirivi :lukitut-summat] normalisoi-vektoriksi)

      (get-in rivi [:profiili :urakkatyyppi])
      (muunna-urakkatyyppi [:profiili :urakkatyyppi])

      (:soveltuvuuskonteksti rivi)
      (update :soveltuvuuskonteksti keyword)

      (get-in rivi [:laji :koodi])
      (update-in [:laji :koodi] keyword))))

(defqueries "harja/kyselyt/sanktio_konfiguraatio.sql")

