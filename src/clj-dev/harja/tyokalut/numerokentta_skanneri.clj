(ns harja.tyokalut.numerokentta-skanneri
  "Oneshot-työkalu numerokenttien skannaukseen."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

;; Aja esimerkiksi:
;;
;;   lein run -m harja.tyokalut.numerokentta-skanneri; echo ""
;;
;; Oletuksena skannaa src/cljs (ja jos olemassa, myös laadunseuranta/src).
;;
;; Huomio: tämä on heuristinen tekstiskanneri, ei AST-lukija. Se voi antaa
;; false positive/negative -osumia, mutta toimii hyvin "löydä epäilyttävät" -tasolla.

(def ^:private numero-tyyppi-re
  #":tyyppi\s+:numero\b")

(def ^:private eksplisiittiset-avainfragit
  "Jos jokin näistä löytyy samasta kenttämäärittelyn lähikontekstista,
  oletetaan että numeroformaatin/tarkkuuden semantiikka on eksplisiittinen."
  [":kokonaisluku?"
   ":desimaalien-maara"
   ":min-desimaalit"
   ":max-desimaalit"
   ":fmt"
   ":tyyppi :kokonaisluku"
   ":tyyppi :lkm"])

(defn- tiedosto?
  [^java.io.File f]
  (and (.isFile f)
       (let [n (.getName f)]
         (or (str/ends-with? n ".cljs")
             (str/ends-with? n ".cljc")))))

(defn- lue-juuret
  [^String projektijuuri]
  (let [oletus ["src/cljs" "laadunseuranta/src"]]
    (->> oletus
         (map #(io/file projektijuuri %))
         (filter #(.exists ^java.io.File %)))))

(defn- kaikki-tiedostot
  [juuret]
  (->> juuret
       (mapcat file-seq)
       (filter tiedosto?)))

(defn- konteksti
  "Palauttaa rivikontekstin rivin ympäriltä, jotta voidaan löytää avaimet jotka
  ovat eri riveillä samassa mapissa."
  [rivit idx]
  (let [alku (max 0 (- idx 8))
        loppu (min (dec (count rivit)) (+ idx 8))]
    (->> (subvec rivit alku (inc loppu))
         (str/join "\n"))))

(defn- poimi-kentta-id
  "Poimii rivikontekstista nimettyjä avaimia raportointia varten (best effort)."
  [ctx]
  (let [otsikko (some-> (re-find #":otsikko\s+\"([^\"]+)\"" ctx) second)
        nimi    (some-> (re-find #":nimi\s+([^\s\]\}]+)" ctx) second)]
    {:otsikko otsikko
     :nimi nimi}))

(defn- eksplisiittinen?
  [ctx]
  (some #(str/includes? ctx %) eksplisiittiset-avainfragit))

(defn- raportoi!
  [{:keys [polku rivi-nro id]}]
  (println (format "%s:%d  nimi=%s  otsikko=%s"
                   polku
                   rivi-nro
                   (or (:nimi id) "-")
                   (or (:otsikko id) "-"))))

(defn- etsi-tiedostosta
  [^java.io.File f]
  (let [polku (.getPath f)
        rivit (->> (slurp f)
                   (str/split-lines)
                   (vec))]
    (->> (map-indexed vector rivit)
         (keep (fn [[idx rivi]]
                 (when (re-find numero-tyyppi-re rivi)
                   (let [ctx (konteksti rivit idx)]
                     (when-not (eksplisiittinen? ctx)
                       {:polku polku
                        :rivi-nro (inc idx)
                        :id (poimi-kentta-id ctx)})))))
         (vec))))

(defn- yhteenveto!
  [osumat]
  (println)
  (println (str "Yhteensä epäilyttäviä `:tyyppi :numero` -kenttiä: " (count osumat)))
  (when (pos? (count osumat))
    (println "Vinkki: vaihda `:tyyppi :kokonaisluku`/`:lkm` tai määritä formatointi (esim. :desimaalien-maara).")))

(defn -main
  [& args]
  (let [projektijuuri (.getCanonicalPath (io/file "."))
        juuret (lue-juuret projektijuuri)
        tiedostot (kaikki-tiedostot juuret)
        osumat (->> tiedostot
                    ;; Toteutustiedosto sisältää tarkoituksella :tyyppi :numero -koodia.
                    (remove #(str/ends-with? (.getPath ^java.io.File %) "src/cljs/harja/ui/kentat.cljs"))
                    (mapcat etsi-tiedostosta)
                    (sort-by (juxt :polku :rivi-nro))
                    (vec))]
    (when (empty? juuret)
      (println "Ei löytynyt skannattavia juurihakemistoja."))
    (doseq [o osumat]
      (raportoi! o))
    (yhteenveto! osumat)
    (when (some #(= "--fail" %) args)
      (System/exit (if (zero? (count osumat)) 0 1)))))
