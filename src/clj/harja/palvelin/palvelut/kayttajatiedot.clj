(ns harja.palvelin.palvelut.kayttajatiedot
  "Palvelu, jolla voi hakea perustietoja nykyisestä käyttäjästä"
  (:require [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.kayttajat :as q]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.palvelut.urakat :as urakat]
            [harja.kyselyt.konversio :as konv]
            [harja.pvm :as pvm]))

(defn oletusurakkatyyppi
  [db user]
  (let [kayttajan-urakat (oikeudet/kayttajan-urakat user)]
    (if (empty? kayttajan-urakat)
      :hoito
      (keyword (q/hae-kayttajan-yleisin-urakkatyyppi db kayttajan-urakat)))))

(defn kayttajan-urakat-aikavalilta
  "Palauttaa vektorin mäppejä.
  Mäpit ovat muotoa {:tyyppi x :hallintayksikko {:id .. :nimi ..} :urakat [{:nimi .. :id ..}]}
  Tarkastaa, että käyttäjä voi lukea urakkaa annetulla oikeudella."
  ([db user oikeus]
   (kayttajan-urakat-aikavalilta db user oikeus nil nil nil nil (pvm/nyt) (pvm/nyt)))
  ([db user oikeus urakka-id urakoitsija urakkatyyppi hallintayksikot alku loppu]
   (konv/sarakkeet-vektoriin
     (into []
           (comp
             (filter (fn [{:keys [urakka_id]}]
                       (oikeudet/voi-lukea? oikeus urakka_id user)))
             (map #(assoc % :tyyppi (keyword (:tyyppi %))))
             (map konv/alaviiva->rakenne))

           (let [alku (or alku (pvm/nyt))
                 loppu (or loppu (pvm/nyt))
                 hallintayksikot (cond
                                   (nil? hallintayksikot) nil
                                   (vector? hallintayksikot) hallintayksikot
                                   :else [hallintayksikot])]
             (cond
               (not (nil? urakka-id))
               (urakat-q/hae-urakoiden-organisaatiotiedot db urakka-id)

               :else
               (urakat-q/hae-kaikki-urakat-aikavalilla
                 db (konv/sql-date alku) (konv/sql-date loppu)
                 (when urakoitsija urakoitsija)
                 (when urakkatyyppi (name urakkatyyppi)) hallintayksikot))))
     {:urakka :urakat}
     (juxt :tyyppi (comp :id :hallintayksikko)))))

(defn kayttajan-urakka-idt-aikavalilta
  ([db user oikeus]
   (kayttajan-urakka-idt-aikavalilta db user oikeus nil nil nil nil (pvm/nyt) (pvm/nyt)))
  ([db user oikeus urakka-id urakoitsija urakkatyyppi hallintayksikot alku loppu]
   (into #{}
         (comp (mapcat :urakat)
               (map :id))
         (kayttajan-urakat-aikavalilta db user oikeus urakka-id urakoitsija urakkatyyppi
                                       hallintayksikot alku loppu))))

(defn kayttajan-urakat-aikavalilta-alueineen
  "Tekee saman kuin kayttajan-urakat-aikavalilta, mutta liittää urakoihin mukaan niiden geometriat."
  ([db user oikeus]
   (kayttajan-urakat-aikavalilta-alueineen db user oikeus nil nil nil nil (pvm/nyt) (pvm/nyt)))
  ([db user oikeus urakka-id urakoitsija urakkatyyppi hallintayksikot alku loppu]
   (kayttajan-urakat-aikavalilta-alueineen db user oikeus urakka-id urakoitsija urakkatyyppi
                                           hallintayksikot alku loppu urakat/oletus-toleranssi))
  ([db user oikeus urakka-id urakoitsija urakkatyyppi hallintayksikot alku loppu toleranssi]
   (let [aluekokonaisuudet (kayttajan-urakat-aikavalilta db user oikeus urakka-id
                                                         urakoitsija urakkatyyppi
                                                         hallintayksikot alku loppu)
         urakka-idt (mapcat
                      (fn [aluekokonaisuus]
                        (map :id (:urakat aluekokonaisuus)))
                      aluekokonaisuudet)
         urakat-alueineen (into {} (map
                                     (fn [ur]
                                       [(get-in ur [:urakka :id]) (or (get-in ur [:urakka :alue])
                                                                      (get-in ur [:alueurakka :alue]))])
                                     (urakat/urakoiden-alueet db user oikeus urakka-idt toleranssi)))]
     (mapv
       (fn [au]
         (assoc au :urakat (mapv
                             (fn [urakka]
                               (assoc urakka :alue (get urakat-alueineen (:id urakka))))
                             (:urakat au))))
       aluekokonaisuudet))))

(defrecord Kayttajatiedot []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
                      :kayttajatiedot
                      (fn [user alku]
                        (assoc user :urakkatyyppi
                                    (oletusurakkatyyppi (:db this) user))))
    this)
  (stop [this]
    (poista-palvelu (:http-palvelin this) :kayttajatiedot)
    this))
