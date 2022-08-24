(ns harja.tyokalut.hashing
  "Apureita hashien laskemiseen.")

(declare hash2)
(def hash2
  "Korjaa ClojureScriptin alkuperäisen hash-funktion.
  CLJS hash-funktio ei tue numeron desimaaliosan hashaamista, vaan muuntaa numeron ensin integeriksi.
  Tämä johtaa siihen, että hashien avulla ei voida tunnistaa muutoksia numeroiden desimaaleissa.
  Tämä vaihtoehtoinen hash-toteutus käsittelee finitet numerot stringeinä, jolloin desimaalien muutokset
  on mahdollista tunnistaa laskettujen hashien avulla."
  (let [old-hash-fn hash]
    (fn [o]
      (with-redefs [hash hash2]
        (cond
          ;; Kasittele numerot stringeinä ja hashaa string.
          (number? o)
          (if (js/isFinite o)
            ;; Alkuperäinen cljs hash-funktio muuntaa numeron ensin integeriksi, joten
            ;; numeron desimaalit jäävät huomiotta.
            (m3-hash-int (hash-string (str o)))
            ;; Käsittele infinite-tapaukset alkuperäisellä hash-funktiolla.
            (old-hash-fn o))
          :else
          ;; Käsittele kaikki muut tapaukset alkuperäisellä hash-funktiolla.
          (old-hash-fn o))))))