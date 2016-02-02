(ns harja.palvelin.tyokalut.lukot
  (:require [harja.kyselyt.lukot :as lukko]))

(defn aja-lukon-kanssa
  ([db tunniste toiminto-fn] (aja-lukon-kanssa db tunniste toiminto-fn nil))
  ([db tunniste toiminto-fn aikaraja]
   (if (lukko/aseta-lukko? db tunniste aikaraja)
     (do
       (try
         (toiminto-fn)
         (lukko/avaa-lukko? db tunniste)
         (catch Exception e
           (lukko/avaa-lukko? db tunniste)
           (throw e)))
       true)
     false)))

(defn aja-tiedoituslukon-kanssa [db tunniste toiminto-fn]
  (lukko/aseta-tiedoituslukko db tunniste)
  (try
    (let [tulos (toiminto-fn)]
      (lukko/avaa-tiedoituslukko db tunniste)
      tulos)
    (catch Exception e
      (lukko/avaa-tiedoituslukko db tunniste)
      (throw e))))

(def lukkoidt {:sampo 10001})



