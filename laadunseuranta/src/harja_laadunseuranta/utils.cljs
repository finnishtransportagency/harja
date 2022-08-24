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

;; Nämä selaimet on käsin testattu toimivaksi.
;; On kuitenkin syytä huomata, että esim Androidin lukuisat omat selaimet saattavat käyttää
;; Chrome-nimeä user agentissa. Näitä ei tiedettävästi voi luotettavasti tunnistaa.
(def tuetut-selaimet [{:avain :chrome :nimi "Google Chrome" :versio 53}
                      {:avain :firefox :nimi "Mozilla Firefox" :versio 49} ;; mm. IndexedDB-tuki
                      {:avain :iphone-safari :nimi "iPhone Safari"}
                      {:avain :ipad-safari :nimi "iPad Safari"}])

(defn selain-avaimella [avain]
  (first (filter #(= (:avain %) avain) tuetut-selaimet)))

(defn tuetut-selaimet-tekstina []
  (str/join ", " (map #(str (:nimi %) (when-let [versio (:versio %)]
                                        (str " (" versio ")")))
                      tuetut-selaimet)))

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
          (:versio (selain-avaimella :chrome)))))

(defn firefox-vanhentunut? []
  (and (firefox?)
       (< (maarita-firefox-versio-user-agentista
            (clojure.string/lower-case js/window.navigator.userAgent))
          (:versio (selain-avaimella :firefox)))))

(defn tuettu-selain? []
  (boolean (or (ipad?)
               (iphone?)
               (and (chrome?)
                    (not (chrome-vanhentunut?)))
               (and (firefox?)
                    (not (firefox-vanhentunut?))))))

(defn vanhentunut-selain? []
  (boolean (or (and (chrome?)
                    (chrome-vanhentunut?))
               (and (firefox?)
                    (firefox-vanhentunut?)))))

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

(defn timestamp []
  (.getTime (js/Date.)))

(defn ilman-tavutusta [teksti]
  (str/replace teksti #"\u00AD" ""))

(defn kahdella-desimaalilla [arvo]
  (gstr/format "%.2f" arvo))

(defn keywordize-map [m]
  (into {}
        (map vector (map keyword (keys m)) (vals m))))

(defn- avg [mittaukset]
  (/ (reduce + 0 mittaukset) (count mittaukset)))

(defn tarkkaile!
  [nimi atomi]
  (add-watch atomi :tarkkailija (fn [_ _ vanha uusi]
                                  (println nimi ": " (pr-str vanha) " => " (pr-str uusi)))))

(defn stg-ymparistossa? []
  "Tarkistaa ollaanko stg-ympäristössä"
  (let [host (.-host js/location)]
    (#{"testiextranet.vayla.fi"} host)))

(defn kehitysymparistossa? []
  "Tarkistaa ollaanko kehitysympäristössä"
  (let [host (.-host js/location)]
    (or (gstr/startsWith host "10.10.")
        (#{"localhost" "localhost:3000" "localhost:8000" "192.168.43.22:8000"
           "harja-test.solitaservices.fi"
           "harja-dev1" "harja-dev2" "harja-dev3" "harja-dev4" "harja-dev5" "harja-dev6"} host))))