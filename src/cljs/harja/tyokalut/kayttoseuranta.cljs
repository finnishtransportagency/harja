(ns harja.tyokalut.kayttoseuranta
  "Mahdollistaa datan keräämisen käyttöliittymän käytöstä.

  Lähettää palvelimelle timestampin, sivun, ja lisätiedon.
  Katso myös harja.ui.komponentti/kirjaa-kaytto!"
  (:require [harja.asiakas.kommunikaatio :as k]
            [cljs.core.async :refer [<!]]
            [reagent.core :as r])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn laheta-kaytto! [sivu lisatieto tila]
  (go (let [vastaus (<! (k/post!
                          :lokita-kaytto
                          {:sivu sivu
                           :lisatieto lisatieto
                           :tila tila}))])))

(defn seuraa-kayttoa!
  "Rekisteröi atomin seurattavaksi, ja lähettää palvelimelle tiedon,
  kun atomin arvo muuttuu. Lähetettävä tila on aina boolean."
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

(defn seuraa-polkua!
  "Luo kursorin atomista annetun polun perusteella, ja rekisteröi atomin
  seurattavaksi."
  ([atomi polku sivu]
    (seuraa-polkua! atomi polku sivu nil))
  ([atomi polku sivu lisatieto]
    (seuraa-kayttoa! (r/cursor atomi polku) sivu lisatieto)))