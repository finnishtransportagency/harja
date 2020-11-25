(ns harja.tyokalut.edn-eval
  "Aja 'lein run -m harja.tyokalut.edn-eval --help' dokumentaatiota varten"
  (:require [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.pprint :as pprint])
  (:gen-class))

(s/def ::dir-polku (s/and string?
                          #(.isDirectory (io/file %))))
(s/def ::nimi string?)
(s/def ::edn-tiedosto (s/and string?
                             (fn [filu]
                               ((every-pred #(.exists %)
                                            #(.isFile %)
                                            #(re-find (re-pattern "\\.edn$") (.getName %)))
                                (io/file filu)))))
(s/def ::help nil?)

(def asetusten-lopetus-merkki "--")

(defonce ^{:private true
           :doc "Tietoja mahdollisista asetuksista"}
         mahdolliset-asetukset
         {:output {:max-args 1
                   :skeema [::dir-polku]
                   :nimi {:lyhyt "o"
                          :pitka "output"}
                   :doc (str "Polku kansioon, jonne generoit .edn tiedosto(t) luodaan.\n"
                             "Jos tätä ei anneta, luodaan tiedosto(t) siihen kansioon, jossa annettu .edn tiedosto sijaitsee")}
          :name {:max-args 1
                 :skeema [::nimi]
                 :nimi {:lyhyt "n"
                        :pitka "name"}
                 :doc (str "Luotavan tiedoston nimi. Jos tämä on annettu, käännettäviä .edn tiedostoja voi olla vain yksi\n"
                           "Jos tätä ei ole annettu, annetaan nimeksi sama kuin alkuperäisen .edn tiedoston nimi.\n"
                           "Jos tätä, eikä output ole annettu, annetaan nimeksi sama kuin alkuperäisen tiedoston nimi, \n"
                           "mutta päätteeksi tulee .evaled.edn")}
          :help {:max-args 0
                 :skeema [::help]
                 :nimi {:lyhyt "h"
                        :pitka "help"}
                 :doc "Näyätä tämä viesti"}})

(defn flag? [arg]
  {:pre [(string? arg)]
   :post [(boolean? %)]}
  (and (string? (re-find #"^-{1,2}[^-]+" arg))
       (not (s/valid? ::edn-tiedosto arg))))

(defn asetus->avain [asetus]
  {:pre [(flag? asetus)]
   :post [(keyword? %)]}
  (let [lyhennetty? (string? (re-find #"^-[^-]" asetus))
        asetuksen-nimi (last (re-find #"[-]+(.*)" asetus))]
    (reduce (fn [lopputulos [keyword-nimi {{:keys [lyhyt pitka]} :nimi}]]
              (cond
                lopputulos lopputulos
                (and lyhennetty?
                     (= asetuksen-nimi lyhyt)) keyword-nimi
                (and (not lyhennetty?)
                     (= asetuksen-nimi pitka)) keyword-nimi
                :else nil))
            nil
            mahdolliset-asetukset)))

(defn asetuksen-argumentit-oikein? [asetus-flag args]
  (let [asetusavain (asetus->avain asetus-flag)]
    (every? true?
            (map #(s/valid? %1 %2)
                 (get-in mahdolliset-asetukset [asetusavain :skeema])
                 args))))

(defn asetuksen-hyvaksytyt-argumentit [asetus-flag]
  (let [asetusavain (asetus->avain asetus-flag)]
    (get-in mahdolliset-asetukset [asetusavain :skeema])))

(defn asetuksen-argumenttien-maara [args]
  (let [input (take-while #(and (not (flag? %))
                                (not (= asetusten-lopetus-merkki %)))
                          args)]
    (if (= (count input) (count args))
      ;; Viimeinen asetus ennen edn-filuja
      (count (take-while #(not (s/valid? ::edn-tiedosto %)) args))
      ;; asetus asetusosiossa
      (count input))))

(defn tarkista-asetuksen-argumenttien-maara [asetus-flag args]
  (let [argumenttien-maara (asetuksen-argumenttien-maara args)
        max-maara (:max-args (mahdolliset-asetukset (asetus->avain asetus-flag)))]
    (when (< max-maara argumenttien-maara)
      (throw (Exception. (str "Liian monta argumenttia annettu asetukselle " (asetus->avain asetus-flag) ". Max määrä on " max-maara))))))

(defn jaottele-args
  ([args] (jaottele-args args {:asetukset {} :edn-tiedostot []}))
  ([args tulos]
   (if (empty? args)
     tulos
     (let [asetus? (flag? (str (first args)))]
       (cond
         (= asetusten-lopetus-merkki (str (first args))) (recur (rest args) tulos)
         asetus? (let [asetus (asetus->avain (first args))
                       asetuksen-argumenttien-maara (and asetus?
                                                         (asetuksen-argumenttien-maara (rest args)))
                       single? (= 1 (get-in mahdolliset-asetukset [asetus :max-args]))]
                   (recur (drop asetuksen-argumenttien-maara (rest args))
                          (assoc-in tulos [:asetukset asetus] (if single?
                                                                (second args)
                                                                (take asetuksen-argumenttien-maara (rest args))))))
         :else (recur (rest args)
                      (update tulos :edn-tiedostot conj (str (first args)))))))))

(defn tarkista-argumentit!
      ([args] (tarkista-argumentit! args (flag? (first args))))
      ([args asetukset-osio?]
       (when-not (empty? args)
         (let [asetus? (flag? (first args))
               validi-tiedosto? (and (or (not asetukset-osio?) (not asetus?))
                                     (s/valid? ::edn-tiedosto (str (first args))))
               asetusten-lopetus-merkki? (= (first args) asetusten-lopetus-merkki)
               asetus-vaarassa-kohdassa? (and asetukset-osio? asetus? (not asetukset-osio?))
               asetuksella-vaara-argumentti? (and asetukset-osio?
                                                  (if asetusten-lopetus-merkki?
                                                    false
                                                    (and asetus?
                                                         (not (asetuksen-argumentit-oikein? (first args) (rest args))))))
               asetuksen-jalkeen-ei-argseja? (and asetus? (empty? (drop (asetuksen-argumenttien-maara (rest args)) args)))]
           (when asetus?
             (tarkista-asetuksen-argumenttien-maara (first args) (rest args)))
           (cond
             asetusten-lopetus-merkki? (recur (rest args) false)
             (not (or asetus?
                      validi-tiedosto?)) (throw (Exception. (str (first args) " ei ole validi asetus eikä tiedosto")))
             asetus-vaarassa-kohdassa? (throw (Exception. (str "Asetus " (first args) " on väärässä kohdassa")))
             asetuksella-vaara-argumentti? (throw (Exception. (str "Annoit asetukselle " (str (first args))
                                                                   " arvon " (str (second args))
                                                                   ". Asetukselle voi antaa arvot "
                                                                   (asetuksen-hyvaksytyt-argumentit (first args)))))
             asetuksen-jalkeen-ei-argseja? (throw (Exception. (str "edn filut puuttuu")))

             (and asetukset-osio? asetus?) (recur (drop (inc (asetuksen-argumenttien-maara (rest args))) args) true)
             asetukset-osio? (recur (rest args) false))))))

(defn tarkista-asetuksein-ja-tiedostojen-yhteensopivuus! [asetukset edn-tiedostot]
  (when (and (:name asetukset)
             (not= (count edn-tiedostot) 1))
    (throw (Exception. "Jos 'name' asetus on annettu, ei .edn tiedostoja voi määritellä yhtä enempää"))))

(defmulti suorita-toiminto
          (fn [asetukset _]
            (if (contains? asetukset :help)
              :help
              :luo-tiedostot)))

(defmethod suorita-toiminto :help
  [_ _]
  (println (str "\nÄLÄ AJA TÄTÄ SELLAISTA .edn TIEDOSTOA VASTEN, JONKA SISÄLTÖÄ ET TIEDÄ!\n"
                "Tämä johtuu siitä, että annetujen .edn sisältämä koodi evaluoidaan.\n"
                "Jos ensimmäisen annetun .edn tiedoston nimi alkaa '-' merkillä, tulee\n"
                "optiot ja filut erottaa '" asetusten-lopetus-merkki "' merkillä\n\n"
                "----------------------------------------------------------------------\n"
                "Tätä työkalua voi käyttää .edn tiedoston kirjoittamisessa toiseen tiedostoon.\n"
                "Huom! .edn sisältämä koodi evaluoidaan.\n\n"
                "lein run -m harja.tyokalut.edn-eval [-h] [-o directory] [-n name] files\n"
                "lein run -m harja.tyokalut.edn-eval -h\n"
                "----------------------------------------------------------------------\n\n"
                "Options:\n"
                (reduce (fn [s [_ {{:keys [lyhyt pitka]} :nimi doc :doc}]]
                          (str s "-" lyhyt " (--" pitka ") " doc "\n\n"))
                        ""
                        mahdolliset-asetukset)
                "Files:\n"
                "tiedostopolkuja .edn tiedostoihin, jotka halutaan kirjoittaa uudestaan jonnekkin muualle\n\n"
                "Esimerkit:\n"
                "lein run -m harja.tyokalut.edn-eval -o figwheel_conf/luodut figwheel_conf/dev-container.figwheel.edn\n"
                "lein run -m harja.tyokalut.edn-eval -o figwheel_conf/luodut/ -- -foo.edn\n")))

(defmethod suorita-toiminto :luo-tiedostot
  [asetukset edn-tiedostot]
  (doseq [edn-tiedosto edn-tiedostot
          :let [file-obj (io/file edn-tiedosto)
                sama-kohde? (nil? (:output asetukset))
                nimi (or (:name asetukset)
                         (if sama-kohde?
                           (str (last (re-find #"(.*)\.edn$" (.getName file-obj))) ".evaled.edn")
                           (.getName file-obj)))
                polku (or (:output asetukset)
                          (apply str (drop-last (count (.getName file-obj))
                                                (.getAbsolutePath file-obj))))
                polku (if (re-find #"/$" polku)
                        polku
                        (str polku "/"))]]
    (println "polku: " polku)
    (println "nimi: " nimi)
    (with-open [w (io/writer (str polku nimi))]
      (let [input-data (read-string (slurp edn-tiedosto))]
        (when (meta input-data)
          (println "META LÖYTY")
          (.write w "^")
          (pprint/pprint (meta input-data) w))
        (pprint/pprint input-data w)))))

(defn -main [& args]
  (tarkista-argumentit! args)
  (let [{:keys [asetukset edn-tiedostot]} (jaottele-args args)]
    (tarkista-asetuksein-ja-tiedostojen-yhteensopivuus! asetukset edn-tiedostot)
    (suorita-toiminto asetukset edn-tiedostot)))