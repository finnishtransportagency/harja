(ns harja.palvelin.palvelut.lampotilat
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.lampotilat :as q]
            [harja.palvelin.oikeudet :as oik]
            [harja.kyselyt.konversio :as konv]
            [clojure.string :as str]
            [harja.palvelin.oikeudet :as oikeudet]
            [taoensso.timbre :as log]))

(defn keskilampo-ja-pitkalampo-floatiksi
  [kartta]
  (let [kl (:keskilampotila kartta)
        pl (:pitka_keskilampotila kartta)]
    (dissoc
      (assoc
        (assoc kartta :keskilampo (if (nil? kl) kl (float kl)))
        :pitkalampo (if (nil? pl) pl (float pl)))
      :pitka_keskilampotila :keskilampotila)))

(defn urakan-lampotilat [db user urakka-id]
  (log/debug "Haetaan urakan lämpotilat urakalle: " urakka-id)
  ;(oik/vaadi-lukuoikeus-urakkaan user urakka-id)
  (map keskilampo-ja-pitkalampo-floatiksi (q/hae-lampotilat db urakka-id)))

(defn tallenna-lampotilat!
  [db user arvot]
  (let [{:keys [urakka id alku loppu keskilampo pitkalampo]} arvot
        kl (if (empty? keskilampo) nil (java.lang.Double/parseDouble keskilampo))
        pl (if (empty? pitkalampo) nil (java.lang.Double/parseDouble pitkalampo))]
    (log/debug "Tallennetaan lämpötilaa")
    ;(log/debug (java.lang.Float/parseFloat keskilampo) (type (java.lang.Float/parseFloat keskilampo)))
    ;(log/debug (java.lang.Float/parseFloat pitkalampo))
    (if (oikeudet/rooli-urakassa? user "urakanvalvoja" urakka)
      (if (:id arvot)
        (do
          (log/debug "Pävitetään olemassaolevaa riviä")
          (keskilampo-ja-pitkalampo-floatiksi (q/paivita-lampotila<!
                                                db
                                                urakka
                                                (java.sql.Date. (.getTime alku))
                                                (java.sql.Date. (.getTime loppu))
                                                kl
                                                pl
                                                id)))

        (do
          (log/debug "lisätään uusi rivi lämpötilalle")
          (keskilampo-ja-pitkalampo-floatiksi (q/uusi-lampotila<!
                                                db
                                                urakka
                                                (java.sql.Date. (.getTime alku))
                                                (java.sql.Date. (.getTime loppu))
                                                kl
                                                pl)))))))

(defrecord Lampotilat []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)]
      (julkaise-palvelu http :urakan-lampotilat
                        (fn [user urakka-id]
                          (urakan-lampotilat (:db this) user urakka-id)))

      (julkaise-palvelu http :tallenna-lampotilat!
                        (fn [user arvot]
                          (tallenna-lampotilat! (:db this) user arvot)))
      this))

  (stop [this]
    (poista-palvelut (:http-palvelin this) :urakan-lampotilat)
    (poista-palvelut (:http-palvelin this) :tallenna-lampotilat!)
    this))
