(ns harja.palvelin.palvelut.lampotilat
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.lampotilat :as q]
            [harja.kyselyt.urakat :as urakat]
            [harja.domain.roolit :as roolit]
            [harja.kyselyt.konversio :as konv]
            [clojure.java.jdbc :as jdbc]
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


(defn tallenna-lampotilat!
  [db user arvot]
  (let [{:keys [urakka id alku loppu keskilampo pitkalampo]} arvot]
    (log/debug "Tallennetaan lämpötilaa")
    (if (roolit/rooli-urakassa? user "urakanvalvoja" urakka)
      (if (:id arvot)
        (do
          (log/debug "Pävitetään olemassaolevaa riviä")
          (keskilampo-ja-pitkalampo-floatiksi (q/paivita-lampotila<!
                                                db
                                                urakka
                                                (konv/sql-timestamp alku)
                                                (konv/sql-timestamp loppu)
                                                keskilampo
                                                pitkalampo
                                                id)))

        (do
          (log/debug "lisätään uusi rivi lämpötilalle")
          (keskilampo-ja-pitkalampo-floatiksi (q/uusi-lampotila<!
                                                db
                                                urakka
                                                (konv/sql-timestamp alku)
                                                (konv/sql-timestamp loppu)
                                                keskilampo
                                                pitkalampo)))))))

(defn hae-lampotilat-ilmatieteenlaitokselta [db url urakka-id vuosi]
  ;; Haetaan urakan id:n perusteella alueurakan tunnus, jota ilmatieteenlaitos käyttää
  (let [alueurakkanro (:alueurakkanro (first (urakat/hae-urakan-alueurakkanumero db urakka-id)))]
    (when alueurakkanro
      (some #(when (= (:urakka-id %) alueurakkanro)
               %)
            (ilmatieteenlaitos/hae-talvikausi url vuosi)))))

(defn hae-urakan-suolasakot-ja-lampotilat
  [db user urakka-id]
  (log/debug "hae-urakan-suolasakot-ja-lampotilat")
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (into []
        (comp
          (map #(konv/decimal->double % :maara))
          (map #(konv/decimal->double % :keskilampotila))
          (map #(konv/decimal->double % :pitkakeskilampotila)))
          (q/hae-urakan-suokasakot-ja-lampotilat db urakka-id)))

(defn luo-suolasakko
  [params]
  (log/debug "luo suolasakko" params)
  (let [uusi (apply q/luo-suolasakko<! params)
        ]
    (:id uusi)))

(defn paivita-suolasakko
  [tiedot id]
  (log/debug "päivitä suolasakko" tiedot)
  (let [params (into [] (concat tiedot [id]))]
    (apply q/paivita-suolasakko! params))
  id)



(defn tallenna-suolasakko
  [db user tiedot]
  (log/debug "tallenna suolasakko" tiedot)
  (let [params [db (:maara tiedot) (:hoitokauden_alkuvuosi tiedot)
                (:maksukuukausi tiedot) (:indeksi tiedot) (:urakka tiedot)
                (:id user)]
  id (if (:id tiedot)
       (paivita-suolasakko params (:id tiedot))
       (luo-suolasakko params))]
    id))

(defn tallenna-suolasakko-ja-lampotilat
  [db user tiedot]
  (roolit/vaadi-rooli-urakassa user
                               #{roolit/urakanvalvoja}
                               (:urakka tiedot))
  (jdbc/with-db-transaction [db db]
                            (let [_ (log/debug "tallenna-suolasakko-ja-lampotilat" tiedot)
                                  suolasakon-id (tallenna-suolasakko db user tiedot)

                                  ; mäpätään kenttien nimet syömäkelpoiseksi aiemmin tehdylle funktiolle
                                  ; --> urakka id alku loppu keskilampo pitkalampo
                                  lampotila (assoc tiedot
                                              :id (:lt_id tiedot)
                                              :alku (:lt_alkupvm tiedot)
                                              :loppu (:lt_loppupvm tiedot)
                                              :keskilampo (:keskilampotila tiedot)
                                              :pitkalampo (:pitkakeskilampotila tiedot))]
                              (tallenna-lampotilat! db user lampotila))
                            (hae-urakan-suolasakot-ja-lampotilat db user (:urakka tiedot))))

(defn aseta-suolasakon-kaytto [db user {:keys [urakka-id kaytossa?]}]
  (roolit/vaadi-urakanvalvoja user urakka-id)
  (jdbc/with-db-transaction [db db]
    (q/aseta-suolasakon-kaytto! db kaytossa? urakka-id)
    (:kaytossa (first (q/onko-suolasakko-kaytossa? db urakka-id)))))

(defrecord Lampotilat [ilmatieteenlaitos-url]
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)]
      (julkaise-palvelu http :tallenna-lampotilat!
                        (fn [user arvot]
                          (tallenna-lampotilat! (:db this) user arvot)))
      (julkaise-palvelu http :hae-lampotilat-ilmatieteenlaitokselta
                        (fn [user {:keys [urakka-id vuosi]}]
                          (hae-lampotilat-ilmatieteenlaitokselta (:db this) ilmatieteenlaitos-url urakka-id vuosi)))
      (julkaise-palvelu http :hae-urakan-suolasakot-ja-lampotilat
                        (fn [user urakka-id]
                          (hae-urakan-suolasakot-ja-lampotilat (:db this) user urakka-id)))
      (julkaise-palvelu http :tallenna-suolasakko-ja-lampotilat
                        (fn [user tiedot]
                          (tallenna-suolasakko-ja-lampotilat (:db this) user tiedot)))
      (julkaise-palvelu http :aseta-suolasakon-kaytto
                        (fn [user tiedot]
                          (aseta-suolasakon-kaytto (:db this) user tiedot)))
      this))

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :tallenna-lampotilat!
                     :hae-lampotilat-ilmatieteenlaitokselta
                     :hae-urakan-suolasakot-ja-lampotilat
                     :tallenna-suolasakko-ja-lampotilat
                     :aseta-suolasakon-kaytto)
    this))
