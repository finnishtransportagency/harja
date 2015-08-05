(ns harja.palvelin.palvelut.lampotilat
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.lampotilat :as q]
            [harja.kyselyt.urakat :as urakat]
            [harja.domain.roolit :as roolit]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.ilmatieteenlaitos :as ilmatieteenlaitos]))

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
  ;(roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (map keskilampo-ja-pitkalampo-floatiksi (q/hae-lampotilat db urakka-id)))

(defn tallenna-lampotilat!
  [db user arvot]
  (let [{:keys [urakka id alku loppu keskilampo pitkalampo]} arvot
        kl (if (empty? keskilampo) nil (java.lang.Double/parseDouble keskilampo))
        pl (if (empty? pitkalampo) nil (java.lang.Double/parseDouble pitkalampo))]
    (log/debug "Tallennetaan lämpötilaa")
    ;(log/debug (java.lang.Float/parseFloat keskilampo) (type (java.lang.Float/parseFloat keskilampo)))
    ;(log/debug (java.lang.Float/parseFloat pitkalampo))
    (if (roolit/rooli-urakassa? user "urakanvalvoja" urakka)
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

(defn hae-lampotilat-ilmatieteenlaitokselta [db url urakka-id vuosi]
  ;; Haetaan urakan id:n perusteella alueurakan tunnus, jota ilmatieteenlaitos käyttää
  (let [alueurakkanro (:alueurakkanro (first (urakat/hae-urakan-alueurakkanumero db urakka-id)))]
    (when alueurakkanro
      (some #(when (= (:urakka-id %) alueurakkanro)
               %)
            (ilmatieteenlaitos/hae-talvikausi url vuosi)))))

    
(defrecord Lampotilat [ilmatieteenlaitos-url]
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)]
      (julkaise-palvelu http :urakan-lampotilat
                        (fn [user urakka-id]
                          (urakan-lampotilat (:db this) user urakka-id)))

      (julkaise-palvelu http :tallenna-lampotilat!
                        (fn [user arvot]
                          (tallenna-lampotilat! (:db this) user arvot)))
      (julkaise-palvelu http :hae-lampotilat-ilmatieteenlaitokselta
                        (fn [user {:keys [urakka-id vuosi]}]
                          (hae-lampotilat-ilmatieteenlaitokselta (:db this) ilmatieteenlaitos-url urakka-id vuosi)))
      this))

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :urakan-lampotilat :tallenna-lampotilat!
                     :hae-lampotilat-ilmatieteenlaitokselta)
    this))
