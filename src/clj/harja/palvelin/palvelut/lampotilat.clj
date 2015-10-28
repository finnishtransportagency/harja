(ns harja.palvelin.palvelut.lampotilat
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.lampotilat :as q]
            [harja.kyselyt.pohjavesialueet :as pohjavesialueet-q]
            [harja.kyselyt.urakat :as urakat]
            [harja.domain.roolit :as roolit]
            [harja.kyselyt.konversio :as konv]
            [clojure.java.jdbc :as jdbc]
            [taoensso.timbre :as log]
            [harja.geo :as geo]
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
  {:suolasakot (into []
                     (comp
                      (map #(konv/decimal->double % :maara))
                      (map #(konv/decimal->double % :keskilampotila))
                      (map #(konv/decimal->double % :pitkakeskilampotila)))
                     (q/hae-urakan-suokasakot-ja-lampotilat db urakka-id))
   :pohjavesialueet (into []
                          (geo/muunna-pg-tulokset :alue)
                          (pohjavesialueet-q/hae-urakan-pohjavesialueet db urakka-id))
   :pohjavesialue-talvisuola (q/hae-urakan-pohjavesialue-talvisuolarajat db urakka-id)})

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
  [db user urakka hoitokauden-alkuvuosi tiedot]
  (log/debug "tallenna suolasakko" tiedot)
  (let [suolasakon-id (:id (first (q/hae-suolasakko-id db urakka hoitokauden-alkuvuosi)))]
    (if suolasakon-id
      (do (log/info "PAIVITA SUOLASAKKO" suolasakon-id)
        (q/paivita-suolasakko! db (:maara tiedot) (:maksukuukausi tiedot)
                                 (:indeksi tiedot) (:id user)
                                 (:talvisuolaraja tiedot) suolasakon-id)
          suolasakon-id)
      
      (:id (q/luo-suolasakko<! db (:maara tiedot) hoitokauden-alkuvuosi (:maksukuukausi tiedot)
                               (:indeksi tiedot) urakka (:id user) (:talvisuolaraja tiedot))))))


;; TIEDOT:
;; {:suolasakko {:talvisuolaraja 444, :maksukuukausi 7, :indeksi "MAKU 2010", :maara 30}
;;  :pohjavesialue-talvisuola [{:pohjavesialue "43091941", :urakka 4, :hoitokauden_alkuvuosi 2015, :talvisuolaraja 4242}]
;;  :muokattu true
;;  :hoitokauden_alkuvuosi 2015}
(defn tallenna-suolasakko-ja-pohjavesialueet
  [db user {:keys [hoitokauden-alkuvuosi urakka suolasakko pohjavesialue-talvisuola] :as tiedot}]
  (log/info "TIEDOT: " (pr-str tiedot))
  (roolit/vaadi-rooli-urakassa user
                               #{roolit/urakanvalvoja}
                               urakka)
  (jdbc/with-db-transaction [db db]
    (let [_ (log/debug "tallenna-suolasakko-ja-lampotilat" tiedot)
          suolasakon-id (tallenna-suolasakko db user urakka hoitokauden-alkuvuosi suolasakko)]

      (log/info "TALLENNA POHJAVESIALUEIDEN suolarajat"))

    (hae-urakan-suolasakot-ja-lampotilat db user urakka)))        


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
      (julkaise-palvelu http :tallenna-suolasakko-ja-pohjavesialueet
                        (fn [user tiedot]
                          (tallenna-suolasakko-ja-pohjavesialueet (:db this) user tiedot)))
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
