(ns harja.tyokalut.kayttoseuranta
  (:require [harja.asiakas.kommunikaatio :as k]
            [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn- laheta-kaytto! [sivu lisatieto tila]
  (go (let [vastaus (<! (k/post!
                          :lokita-kaytto
                          {:sivu sivu
                           :lisatieto lisatieto
                           :tila tila}))])))

(defn seuraa-kayttoa!
  ([atomi sivu]
    (seuraa-kayttoa! atomi sivu nil))
  ([atomi sivu lisatieto]
    (add-watch atomi
               (keyword (str sivu lisatieto))
               (fn [_ _ vanha uusi]
                 (let [vanha (boolean vanha)
                       uusi (boolean uusi)]
                   (when-not (= vanha uusi)
                     (laheta-kaytto! sivu lisatieto uusi)))))))