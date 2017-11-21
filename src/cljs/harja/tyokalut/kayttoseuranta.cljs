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
  kun atomin arvo muuttuu. Katso myös harja.ui.komponentti/kirjaa-kaytto!

  Parametrit:
  * Atomi: Seurattava atomi
  * Sivu: Kantaan tallennettava otsikkotieto, pakollinen. Merkkijono, tai funktio joka palauttaa merkkijonon.
  * Lisätieto: Kantaan tallennettava lisätieto, esim 'Lomake' tai 'Grid'. Merkkijono, tai funktio joka palauttaa merkkijonon.
  * Tila-fn: Funktio, joka muuntaa atomin vanhan ja uuden tilan booleaniksi. Oletuksena 'boolean'"
  ([atomi sivu]
    (seuraa-kayttoa! atomi sivu nil))
  ([atomi sivu lisatieto]
    (seuraa-kayttoa! atomi sivu lisatieto boolean))
  ([atomi sivu lisatieto tila-fn]
   (add-watch atomi
              (keyword (str sivu lisatieto))
              (fn [_ _ vanha uusi]
                (let [vanha (tila-fn vanha)
                      uusi (tila-fn uusi)]
                  (when-not (= vanha uusi)
                    (laheta-kaytto! (if (fn? sivu)
                                      (sivu)
                                      sivu)
                                    (if (fn? lisatieto)
                                      (lisatieto)
                                      lisatieto)
                                    uusi)))))))

(defn seuraa-polkua!
  "Luo kursorin atomista annetun polun perusteella, ja rekisteröi atomin
  seurattavaksi."
  ([atomi polku sivu]
    (seuraa-polkua! atomi polku sivu nil))
  ([atomi polku sivu lisatieto]
    (seuraa-kayttoa! (r/cursor atomi polku) sivu lisatieto)))