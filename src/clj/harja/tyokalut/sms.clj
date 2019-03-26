(ns harja.tyokalut.sms
  "Apureita tekstiviestien lähettämiseen"
  (:require [clojure.xml :refer [parse]]
            [clojure.java.io :as io]
            [clojure.zip :refer [xml-zip]]
            [taoensso.timbre :as log]
            [hiccup.core :refer [html]]
            [clj-time.format :as f]
            [clj-time.coerce :as tc]
            [clojure.data.zip.xml :as z]
            [clojure.string :as str]))

(defn tietolista [& kentat]
  (let [kentta-arvo-parit (partition 2 kentat)]
    (str/join
      (map (fn [[kentta arvo]]
             (str kentta ": " arvo "\n"))
           kentta-arvo-parit))))
