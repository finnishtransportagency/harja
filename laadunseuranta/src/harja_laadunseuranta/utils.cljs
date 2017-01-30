(ns harja-laadunseuranta.utils
  (:require [cljs.core.async :as async :refer [timeout <!]]
            [clojure.string :as str]
            [reagent.core :as reagent]
            [goog.string :as gstr]
            [goog.string.format]
            [reagent.ratom :as ratom])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja-laadunseuranta.macros :refer [after-delay]]))

;; Lähtökohtaisesti pitäisi tutkia selainten ominaisuuksia eikä selaimia.
;; Kyseessä on kuitenkin tiettyyn tarkoitukseen toteutettu mobiilisovellus, joka on käsin
;; testattu toimivaksi eri selaimilla.

(defn ipad? []
  (boolean (some #(re-matches % (clojure.string/lower-case js/window.navigator.userAgent))
                 [#".*ipad.*"])))

(defn iphone? []
  (boolean (some #(re-matches % (clojure.string/lower-case js/window.navigator.userAgent))
                   [#".*iphone.*"])))

(defn chrome? []
  (boolean (some #(re-matches % (clojure.string/lower-case js/window.navigator.userAgent))
                   [#".*chrome.*"])))

(defn firefox? []
  (boolean (some #(re-matches % (clojure.string/lower-case js/window.navigator.userAgent))
                   [#".*firefox.*"])))

(def +tuettu-chrome-versio+ 53) ;; Testattu toimivaksi ja toivottavasti estää useimmat Android Browserit,
                                ;; joissa on usein vanha Chrome-versio user agentissa.
                                ;; Android Browsereille ei voi tarjota luotettavaa tukea
(def +tuettu-firefox-versio+ 49) ;; Testattu toimivaksi, tässä mm. Flexbox & IndexedDB mukana

(defn maarita-selainversio-user-agentista [user-agent-text-lowercase selain-nimi]
  (let [selain-alku-index (.indexOf user-agent-text-lowercase (str selain-nimi "/"))
        selain-versio-teksti (subs user-agent-text-lowercase selain-alku-index (+ selain-alku-index
                                                                                  (count selain-nimi)
                                                                                  5))
        selain-versonumero (re-find (re-pattern "\\d+") selain-versio-teksti)]
    (js/parseInt selain-versonumero)))

(defn maarita-chrome-versio-user-agentista [user-agent-text-lowercase]
  (maarita-selainversio-user-agentista user-agent-text-lowercase "chrome"))

(defn maarita-firefox-versio-user-agentista [user-agent-text-lowercase]
  (maarita-selainversio-user-agentista user-agent-text-lowercase "firefox"))

(defn chrome-vanhentunut? []
  (and (chrome?)
       (< (maarita-chrome-versio-user-agentista
             (clojure.string/lower-case js/window.navigator.userAgent))
           +tuettu-chrome-versio+)))

(defn firefox-vanhentunut? []
  (and (firefox?)
       (< (maarita-firefox-versio-user-agentista
             (clojure.string/lower-case js/window.navigator.userAgent))
           +tuettu-firefox-versio+)))

(defn tuettu-selain? []
  (boolean (or (ipad?)
               (iphone?)
               (and (chrome?)
                    (not (chrome-vanhentunut?)))
               (and (firefox?)
                    (not (firefox-vanhentunut?))))))

(defn vanhentunut-selain? []
  (boolean (or (and (chrome?)
                    (chrome-vanhentunut?)
               (and (firefox?)
                    (firefox-vanhentunut?))))))

(defn- flip [atomi]
  (swap! atomi not))

(defn timed-swap! [delay atom swap-fn]
  (after-delay delay
               (swap! atom swap-fn)))

(defn parsi-kaynnistysparametrit [params]
  (let [params (if (str/starts-with? params "?")
                 (subs params 1)
                 params)
        params (str/split params "&")
        keys-values (keep #(let [[nimi arvo] (str/split % "=")]
                             (when-not (str/blank? nimi)
                               [nimi arvo]))
                          params)]
    (into {} keys-values)))

(defn- timestamp []
  (.getTime (js/Date.)))

(defn ilman-tavutusta [teksti]
  (str/replace teksti #"\u00AD" ""))

(defn erota-mittaukset [havainnot]
  (select-keys havainnot [:lampotila :lumisuus :talvihoito-tasaisuus :soratie-tasaisuus :kitkamittaus
                          :polyavyys :kiinteys :sivukaltevuus]))

(defn erota-havainnot [havainnot]
  (let [h (dissoc havainnot
                  :lampotila :lumisuus :talvihoito-tasaisuus :soratie-tasaisuus :kitkamittaus
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

(defn tarkkaile!
  [nimi atomi]
  (add-watch atomi :tarkkailija (fn [_ _ vanha uusi]
                                  (println nimi ": " (pr-str vanha) " => " (pr-str uusi)))))

(defn stg-ymparistossa? []
  "Tarkistaa ollaanko stg-ympäristössä"
  (let [host (.-host js/location)]
    (#{"testiextranet.liikennevirasto.fi"} host)))

(defn- ms->sec
  "Muuntaa millisekunnit sekunneiksi"
  [s]
  (/ s 1000))

(defn kehitysymparistossa? []
  "Tarkistaa ollaanko kehitysympäristössä"
  (let [host (.-host js/location)]
    (or (gstr/startsWith host "10.10.")
        (#{"localhost" "localhost:3000" "localhost:8000" "192.168.43.22:8000"
           "harja-test.solitaservices.fi"
           "harja-dev1" "harja-dev2" "harja-dev3" "harja-dev4" "harja-dev5" "harja-dev6"} host))))