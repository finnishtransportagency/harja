(ns harja-laadunseuranta.utils
  (:require [cljs.core.async :as async :refer [timeout <!]]
            [clojure.string :as str]
            [reagent.core :as reagent]
            [goog.string :as gstr]
            [goog.string.format]
            [reagent.ratom :as ratom])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja-laadunseuranta.macros :refer [after-delay]]))

(defn- tuettu-selain? []
  (some #(re-matches % (clojure.string/lower-case js/window.navigator.userAgent))
        [#".*android.*" #".*chrome.*" #".*ipad.*" #".*iphone.*"]))

(defn- flip [atomi]
  (swap! atomi not))

(defn timed-swap! [delay atom swap-fn]
  (after-delay delay
     (swap! atom swap-fn)))

(defn unreactive-deref [atom]
  (binding [ratom/*ratom-context* nil]
    @atom))

(defn parsi-kaynnistysparametrit [params]
  (let [params (clojure.string/split (subs params 1) "&")
        keys-values (map #(clojure.string/split % "=") params)]
    (into {} keys-values)))

(defn- timestamp []
  (.getTime (js/Date.)))

(defn ipad? []
  (re-matches #".*iPad.*" (.-platform js/navigator)))

(defn erota-mittaukset [havainnot]
  (select-keys havainnot [:lampotila :lumisuus :tasaisuus :kitkamittaus
                          :polyavyys :kiinteys :sivukaltevuus]))

(defn erota-havainnot [havainnot]
  (let [h (dissoc havainnot
                  :lampotila :lumisuus :tasaisuus :kitkamittaus
                  :polyavyys :kiinteys :sivukaltevuus)]
    (filterv h (keys h))))

(defn kahdella-desimaalilla [arvo]
  (gstr/format "%.2f" arvo))

(defn keywordize-map [m]
  (into {}
        (map vector (map keyword (keys m)) (vals m))))

(defn- avg [mittaukset]
  (/ (reduce + 0 mittaukset) (count mittaukset)))

(def urakan-nimen-oletuspituus 60)

(defn lyhenna-keskelta
  "Lyhentää tekstijonon haluttuun pituuteen siten, että
  pituutta otetaan pois keskeltä, ja korvataan kahdella pisteellä .."
  [haluttu-pituus teksti]
  (if (>= haluttu-pituus (count teksti))
    teksti

    (let [patkat (split-at (/ (count teksti) 2) teksti)
          eka (apply str (first patkat))
          ;; Ekan pituus pyöristetään ylöspäin, tokan alaspäin
          eka-haluttu-pituus (int (Math/ceil (/ haluttu-pituus 2)))
          toka (apply str (second patkat))
          toka-haluttu-pituus (int (Math/floor (/ haluttu-pituus 2)))]
      (str
        ;; Otetaan haluttu pituus -1, jotta pisteet mahtuu mukaan
        (apply str (take (dec eka-haluttu-pituus) eka))
        ".."
        (apply str (take-last (dec toka-haluttu-pituus) toka))))))

(defn lyhennetty-urakan-nimi
  "Lyhentää urakan nimen haluttuun pituuteen, lyhentämällä
  aluksi tiettyjä sanoja (esim urakka -> ur.), ja jos nämä eivät
  auta, leikkaamalla keskeltä kirjaimia pois ja korvaamalla leikatut
  kirjaimet kahdella pisteellä .."
  ([nimi] (lyhennetty-urakan-nimi urakan-nimen-oletuspituus nimi))
  ([pituus nimi]
   (loop [nimi nimi]
     (if (>= pituus (count nimi))
       nimi

       ;; Tänne voi lisätä lisää korvattavia asioita
       ;; Päällimmäiseksi yleisemmät korjaukset,
       ;; viimeiseksi "last resort" tyyppiset ratkaisut
       (recur
         (cond
           ;; Ylimääräiset välilyönnit pois
           (re-find #"\s\s+" nimi)
           (str/replace nimi #"\s\s+" " ")

           ;; "  - " -> "-"
           ;; Täytyy etsiä nämä kaksi erikseen, koska
           ;; \s*-\s* osuisi myös korjattuun "-" merkkijonoon,
           ;; ja "\s+-\s+" osuisi vain jos molemmilla puolilla on välilyönti.
           (or (re-find #"\s+-" nimi) (re-find #"-\s+" nimi))
           (str/replace nimi #"\s*-\s*" "-")

           ;; (?i) case insensitive ei toimi str/replacessa
           ;; cljs puolella. Olisi mahdollista käyttää vain
           ;; clj puolella käyttäen reader conditionaleja, mutta
           ;; samapa se on toistaa kaikki näin.
           (re-find #"alueurakka" nimi)
           (str/replace nimi #"alueurakka" "au")

           (re-find #"Alueurakka" nimi)
           (str/replace nimi #"Alueurakka" "au")

           (re-find #"ALUEURAKKA" nimi)
           (str/replace nimi #"ALUEURAKKA" "au")

           (re-find #"urakka" nimi)
           (str/replace nimi #"urakka" "ur.")

           (re-find #"Urakka" nimi)
           (str/replace nimi #"Urakka" "ur.")

           (re-find #"URAKKA" nimi)
           (str/replace nimi #"URAKKA" "ur.")

           (re-find #"kunnossapidon" nimi)
           (str/replace nimi #"kunnossapidon" "kunn.pid.")

           (re-find #"Kunnossapidon" nimi)
           (str/replace nimi #"Kunnossapidon" "kunn.pid.")

           (re-find #"KUNNOSSAPIDON" nimi)
           (str/replace nimi #"KUNNOSSAPIDON" "kunn.pid.")

           ;; ", " -> " "
           (re-find #"\s*,\s*" nimi)
           (str/replace nimi #"\s*,\s*" " ")

           ;; Jos vieläkin liian pitkä, niin lyhennetään kun.pid. entisestään
           (re-find #"kun.pid." nimi)
           (str/replace nimi #"kun.pid." "kp.")

           (re-find #"POP" nimi)
           (str/replace nimi #"POP" "")

           :else (lyhenna-keskelta pituus nimi)))))))

(defn tarkkaile!
  [nimi atomi]
  (add-watch atomi :tarkkailija (fn [_ _ vanha uusi]
                                  (println nimi ": " (pr-str vanha) " => " (pr-str uusi)))))