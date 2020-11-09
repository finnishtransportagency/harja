(ns harja.palvelin.palvelut.kayttajatiedot
  "Palvelu, jolla voi hakea perustietoja Harjan käyttäjistä."
  (:require [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.kayttajat :as q]
            [harja.tyokalut.html :as html]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.palvelut.urakat :as urakat]
            [taoensso.timbre :as log]
            [harja.kyselyt.konversio :as konv]
            [harja.pvm :as pvm]
            [clj-time.coerce :as c]
            [clj-time.core :as t]
            [harja.domain.roolit :as roolit]
            [harja.palvelin.integraatiot.sahkoposti :as sahkoposti]
            [clojure.set :as set]))

(defn oletusurakkatyyppi
  [db user]
  (let [kayttajan-urakat (oikeudet/kayttajan-urakat user)]
    (log/debug "KÄYTTÄJÄN URAKAT: " kayttajan-urakat)
    (log/debug "YLEISIN URAKKATYYPPI" (if (empty? kayttajan-urakat)
                                        :hoito
                                        (keyword (q/hae-kayttajan-yleisin-urakkatyyppi db kayttajan-urakat))))
    (if (empty? kayttajan-urakat)
      :hoito
      (keyword (q/hae-kayttajan-yleisin-urakkatyyppi db kayttajan-urakat)))))

(defn hae-kayttaja
  "Hakee käyttäjän tiedot id:llä."
  [db kayttaja-id]
  (when-let [k (first (q/hae-kayttaja db kayttaja-id))]
    (konv/array->set (konv/organisaatio k) :roolit)))

(defn kayttajan-lahimmat-urakat
  [db user oikeustarkistus-fn sijainti]
  "Palauttaa yksinkertaisen vectorin urakoita, joihin käyttäjällä on annettu oikeus.
  Urakat ovat järjestyksessä, lähin ensimmäisenä.
  Oikeustarkistus on 2-arity funktio (urakka-id ja käyttäjä)."
  (into []
        (filter (fn [{:keys [id] :as urakka}]
                  (oikeustarkistus-fn id user)))
        (urakat-q/hae-lahimmat-urakat-aikavalilta
          db
          {:x (:lon sijainti)
           :y (:lat sijainti)})))

(defn kayttajan-urakat-aikavalilta
  "Palauttaa vektorin mäppejä.
  Vastaus on vector, sen mäpit ovat muotoa {:tyyppi x :hallintayksikko {:id .. :nimi ..} :urakat [{:nimi .. :id ..}]}
  Oikeustarkistus on 2-arity funktio (urakka-id ja käyttäjä),
  joka tarkistaa, että käyttäjä voi lukea urakkaa annetulla oikeudella."
  ([db user oikeustarkistus-fn]
   (kayttajan-urakat-aikavalilta db user oikeustarkistus-fn nil nil nil nil (pvm/nyt) (pvm/nyt)))
  ([db user oikeustarkistus-fn urakka-id urakoitsija urakkatyyppi hallintayksikot alku loppu]
   (oikeudet/merkitse-oikeustarkistus-tehdyksi!)
   (konv/sarakkeet-vektoriin
     (into []
           (comp
             (filter (fn [{:keys [urakka_id]}]
                       (oikeustarkistus-fn urakka_id user)))
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
                 (when urakkatyyppi (name urakkatyyppi))
                 (not (empty? hallintayksikot)) hallintayksikot))))
     {:urakka :urakat}
     (juxt :tyyppi (comp :id :hallintayksikko)))))

(defn kayttajan-urakka-idt-aikavalilta
  ([db user oikeustarkistus-fn]
   (kayttajan-urakka-idt-aikavalilta db user oikeustarkistus-fn nil nil nil nil (pvm/nyt) (pvm/nyt)))
  ([db user oikeustarkistus-fn urakka-id urakoitsija urakkatyyppi hallintayksikot alku loppu]
   (into #{}
         (comp (mapcat :urakat)
               (map :id))
         (kayttajan-urakat-aikavalilta db user oikeustarkistus-fn urakka-id urakoitsija urakkatyyppi
                                       hallintayksikot alku loppu))))

(defn kayttajan-urakat-aikavalilta-alueineen
  "Tekee saman kuin kayttajan-urakat-aikavalilta, mutta liittää urakoihin mukaan niiden geometriat."
  ([db user oikeustarkistus-fn]
   (kayttajan-urakat-aikavalilta-alueineen db user oikeustarkistus-fn nil nil nil nil (pvm/nyt) (pvm/nyt)))
  ([db user oikeustarkistus-fn urakka-id urakoitsija urakkatyyppi hallintayksikot alku loppu]
   (let [aluekokonaisuudet (kayttajan-urakat-aikavalilta db user oikeustarkistus-fn urakka-id
                                                         urakoitsija urakkatyyppi
                                                         hallintayksikot alku loppu)
         urakka-idt (mapcat
                      (fn [aluekokonaisuus]
                        (map :id (:urakat aluekokonaisuus)))
                      aluekokonaisuudet)
         urakat-alueineen (into {} (map
                                     (fn [ur]
                                       [(get-in ur [:urakka :id]) (get-in ur [:urakka :alue])])
                                     (urakat/urakoiden-alueet
                                       db
                                       user
                                       oikeustarkistus-fn
                                       urakka-idt)))]

     (mapv
       (fn [au]
         (assoc au :urakat (mapv
                             (fn [urakka]
                               (assoc urakka :alue (get urakat-alueineen (:id urakka))))
                             (:urakat au))))
       aluekokonaisuudet))))

(defn yhdista-kayttajan-urakat-alueittain
  "Yhdistää käyttäjän urakat alueittain niin, että sama tyyppi ja alue sekä sen kaikki
   urakat esiintyy vectorissa vain kerran."
  [kayttajan-urakat-alueittain-a kayttajan-urakat-alueittain-b]
  (let [kayttajan-urakat-alueittain-a (map #(update % :urakat set) kayttajan-urakat-alueittain-a)
        kayttajan-urakat-alueittain-b (map #(update % :urakat set) kayttajan-urakat-alueittain-b)
        kayttajan-kaikki-urakat-alueittain (concat kayttajan-urakat-alueittain-a kayttajan-urakat-alueittain-b)]
    (as-> kayttajan-kaikki-urakat-alueittain $
          (group-by (juxt :hallintayksikko :tyyppi) $)
          (reduce-kv
            (fn [vektori _ urakat-alueessa]
              (conj vektori
                    (assoc
                      (first urakat-alueessa)
                      :urakat
                      (->> urakat-alueessa (mapcat :urakat) set))))
            []
            $))))

(defn- hae-yhteydenpidon-vastaanottajat [db user]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-yhteydenpito user)
  (log/debug "Haetaan yhteydenpidon vastaanottajat")
  (let [vastaus (into [] (q/hae-yhteydenpidon-vastaanottajat db))]
    (log/debug "Vastaus: " vastaus)
    vastaus))

(defn- hae-kayttajan-urakat
  "Hakee kaikki urakat tyypin ja hallintayksikön mukaan ryhmiteltynä, joihin
  käyttäjällä on jokin lukuoikeus."
  [db user hallintayksikot]
  (oikeudet/ei-oikeustarkistusta!)
  (kayttajan-urakat-aikavalilta
   db user
   (partial oikeudet/voi-lukea? oikeudet/urakat)
   nil nil nil (if (empty? hallintayksikot)
                 nil
                 hallintayksikot)
   (pvm/nyt) (pvm/nyt)))

(defn laheta-sahkoposti-kaikille-kayttajille
  "Lähettää annetun viestin kaikille Harjan käyttäjille, joille löytyy sähköpostiosoite"
  [sahkoposti db user {:keys [otsikko sisalto]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-indeksit user)
  (let [vastaanottajat (hae-yhteydenpidon-vastaanottajat db user)
        sisalto-sanitoitu (html/sanitoi sisalto)
        sisalto-html (str "<html><body>" (.replace sisalto-sanitoitu "\n" "<br>") "</body>")]
    (doseq [{sahkopostiosoite :sahkoposti} vastaanottajat]
      (sahkoposti/laheta-viesti!
        sahkoposti "harja-ala-vastaa@vayla.fi" sahkopostiosoite otsikko sisalto-html)))
  true)

(defrecord Kayttajatiedot []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db
           sahkoposti :solita-sahkoposti
           :as this}]
    (julkaise-palvelu http
                      :kayttajatiedot
                      (fn [user alku]
                        (oikeudet/ei-oikeustarkistusta!)
                        (assoc user :urakkatyyppi
                               (oletusurakkatyyppi db user))))
    (julkaise-palvelu http
                      :yhteydenpito-vastaanottajat
                      (fn [user _]
                        (hae-yhteydenpidon-vastaanottajat db user)))
    (julkaise-palvelu http
                      :laheta-sahkoposti-kaikille-kayttajille
                      (fn [user yhteydenotto]
                        (laheta-sahkoposti-kaikille-kayttajille sahkoposti db user yhteydenotto)))
    (julkaise-palvelu http
                      :kayttajan-urakat
                      (fn [user hallintayksikot]
                        (#'hae-kayttajan-urakat db user hallintayksikot)))
    this)
  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :kayttajatiedot
                     :yhteydenpito-vastaanottajat)
    this))
