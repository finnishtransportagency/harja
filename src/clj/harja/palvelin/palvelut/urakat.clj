(ns harja.palvelin.palvelut.urakat
  (:require [com.stuartsierra.component :as component]
            [harja.domain.roolit :as roolit]
            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.asetukset :refer [ominaisuus-kaytossa?]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.urakat :as q]
            [harja.kyselyt.sopimukset :as sopimukset-q]
            [harja.domain.urakka :as u]
            [harja.domain.sopimus :as s]
            [harja.domain.hanke :as h]
            [harja.domain.organisaatio :as o]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.palvelut.hankkeet :as hankkeet-palvelu]
            [namespacefy.core :refer [namespacefy]]
            [harja.kyselyt.laskutusyhteenveto :as laskutusyhteenveto-q]
            [harja.id :refer [id-olemassa?]]
            [harja.geo :refer [muunna-pg-tulokset]]
            [clojure.string :as str]
            [harja.pvm :as pvm]
            [slingshot.slingshot :refer [throw+]]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [clj-time.coerce :as c])
  (:import (org.joda.time.format DateTimeFormat)))

(def ^{:const true} oletus-toleranssi 50)

(defn urakan-paivamaarat
  [db id]
  (first (q/urakan-paivamaarat db id)))

(defn urakoiden-alueet
  [db user oikeus-fn urakka-idt]
  (when-not (empty? urakka-idt)
    (into []
      (comp
        (filter (fn [{:keys [urakka_id]}]
                  (oikeus-fn urakka_id user)))
        (harja.geo/muunna-pg-tulokset :urakka_alue)
        (map konv/alaviiva->rakenne))
      (q/hae-urakoiden-geometriat db urakka-idt))))

(defn hae-urakka-id-sijainnilla
  [db urakkatyyppi x y]
  (loop [radius 50
         k 1]
    ;; Palautetaan nil, jos ei löydy urakkaa 800 metrin säteeltä.
    ;; Jos on useampia urakoita, palautetaan lähin tai uusin, jos urakat ovat yhtä lähellä.
    (when (and (< radius 801)
            (< k 10))
      (let [urakat (distinct (map #(dissoc % :etaisyys :urakkatyyppi)
                               (q/hae-urakka-sijainnilla db {:x x :y y
                                                             :threshold radius
                                                             :urakkatyyppi urakkatyyppi})))]
        (cond
          (empty? urakat) (recur (* 2 radius) (inc k))
          :else (:id (first urakat)))))))

(defn hae-lahin-urakka-id-sijainnilla
  "Hakee annetun tyyppisen urakan sijainnilla. Mikäli tyyppiä vastaavaa urakkaa ei löydy, haetaan alueella toimiva
  hoidon alueurakka. Mikäli alueelta ei löydy alueurakkaa, haetaan lähin hoidon alueurakka"
  [db urakkatyyppi {:keys [x y]}]
  ;; Oletuksena haetaan valaistusurakat & päällystyksen palvelusopimukset 1000 metrin thesholdilla
  (let [urakka-id (hae-urakka-id-sijainnilla db urakkatyyppi x y)
        loytynyt-hoidon-urakka-id (fn []
                                    (let [;; Jos ei löytynyt urakkaa annetulla tyypillä, haetaan alueella toimiva hoidon alueurakka
                                          hoidon-urakka-id (if (not= "hoito" urakkatyyppi)
                                                             (hae-urakka-id-sijainnilla db "hoito" x y)
                                                             urakka-id)]
                                      (if hoidon-urakka-id
                                        hoidon-urakka-id
                                        ; Jos hoidon alueurakkaa ei löytynyt suoraan alueelta, haetaan lähin hoidon alueurakka 10 kilometrin säteellä
                                        (:id (first (q/hae-lahin-hoidon-alueurakka db x y 10000))))))]
    (if urakka-id
      urakka-id
      (loytynyt-hoidon-urakka-id))))

(defn hae-urakka-idt-sijainnilla
  "Hakee annetun tyyppisen urakat sijainnilla. Mikäli tyyppiä vastaavia urakoita ei löydy, haetaan alueella toimiva
  hoidon alueurakka. Mikäli alueelta ei löydy alueurakkaa, haetaan lähin hoidon alueurakka"
  [db urakkatyyppi {:keys [x y]}]
  ;; Oletuksena haetaan valaistusurakat & päällystyksen palvelusopimukset 1000 metrin thesholdilla
  (let [urakka-idt (distinct (map #(:id (dissoc % :etaisyys :urakkatyyppi))
                               (q/hae-urakka-sijainnilla db {:x x :y y
                                                             :threshold 1000
                                                             :urakkatyyppi urakkatyyppi})))]
    (if (empty? urakka-idt)
      (if (#{"hoito" "teiden-hoito"} urakkatyyppi)
        ;; Jos hoidon alueurakkaa ei löytynyt suoraan alueelta, haetaan lähin hoidon alueurakka 10 kilometrin säteellä
        (map :id (q/hae-lahin-hoidon-alueurakka db x y 10000))

        ;; Jos ei löytynyt urakkaa annetulla tyypillä, haetaan alueella toimiva hoidon alueurakka
        (let [hoidon-urakkaidt (distinct (map #(:id (dissoc % :etaisyys :urakkatyyppi))
                                           (q/hae-urakka-sijainnilla db {:x x :y y
                                                                         :threshold 10
                                                                         :urakkatyyppi "hoito"})))]
          (if hoidon-urakkaidt
            hoidon-urakkaidt
            ;; Jos hoidon alueurakkaa ei löytynyt suoraan alueelta, haetaan lähin hoidon alueurakka 10 kilometrin säteellä
            (map :id (q/hae-lahin-hoidon-alueurakka db x y 10000)))))
      urakka-idt)))

(defn- pura-sopimukset [{jdbc-array :sopimukset :as urakka}]
  (loop [sopimukset {}
         paasopimus nil
         [s & ss] (when jdbc-array (seq (.getArray jdbc-array)))]
    (if-not s
      (assoc urakka
        :sopimukset sopimukset
        :paasopimus paasopimus)
      (let [[id sampoid] (str/split s #"=")
            paasopimus? (str/starts-with? id "*")
            id (Long/parseLong
                 (if paasopimus?
                   (subs id 1)
                   id))]
        (recur (assoc sopimukset
                 id sampoid)
          (if paasopimus?
            id
            paasopimus)
          ss)))))

(defn- pura-yhteystiedot
  [{jdbc-array :urakan_yhteystiedot :as urakka}]
  (if-not jdbc-array
    urakka
    (assoc urakka :urakan_yhteystiedot (mapv (fn [s]
                                               (let [[id matkapuhelin sahkoposti organisaatio]
                                                     ;; Katso: listaa-urakat-hallintayksikolle
                                                     (str/split s #"\|")]
                                                 {:id           (Long/parseLong id)
                                                  :matkapuhelin matkapuhelin
                                                  :sahkoposti   sahkoposti
                                                  :organisaatio (Long/parseLong organisaatio)}))
                                         (.getArray jdbc-array)))))

(def urakka-xf
  (comp (muunna-pg-tulokset :alue :alueurakan_alue)

        ;; Aseta alue, jos se löytyy
    (map #(if-let [alueurakka (:alueurakan_alue %)]
            (-> %
              (dissoc :alueurakan_alue)
              (assoc :alue alueurakka))
            (dissoc % :alueurakan_alue)))

    (map #(assoc % :urakoitsija {:id      (:urakoitsija_id %)
                                 :nimi    (:urakoitsija_nimi %)
                                 :ytunnus (:urakoitsija_ytunnus %)}))

    (map #(assoc % :loppupvm (pvm/aikana (:loppupvm %) 23 59 59 999))) ; Automaattikonversiolla aika on 00:00

    (map #(assoc % :takuu {:loppupvm (:takuu_loppupvm %)}))

        ;; Sopimukset kannasta vectorina, jossa 1. elementti on id ja
        ;; 2. elementti on sopimuksen tekstikuvaus (sampoid tai nimi):
        ;; ["2=8H05228/01" "*3=8H05228/10"]
        ;; Pääsopimus on se, joka alkaa '*' merkillä.

        ;; Tarjotaan ulos muodossa {:sopimukset {"2" "8H05228/01", "3" "8H05228/10"
        ;;                          :paasopimus 3}
    (map pura-sopimukset)

    (map pura-yhteystiedot)

    (map #(assoc % :hallintayksikko {:id      (:hallintayksikko_id %)
                                     :nimi    (:hallintayksikko_nimi %)
                                     :lyhenne (:hallintayksikko_lyhenne %)}))

    (map #(if-let [tyyppi (:tyyppi %)]
                ;; jos urakkatyypissä on välilyöntejä, korvataan ne väliviivalla, jotta muodostuu validi keyword
            (assoc % :tyyppi (keyword (str/replace (:tyyppi %) " " "-")))
            %))

    (map #(assoc % :sopimustyyppi (and (:sopimustyyppi %) (keyword (:sopimustyyppi %)))))

        ;; Käsitellään päällystysurakan tiedot

    (map #(konv/array->vec % :yha_elyt))
    (map #(konv/array->vec % :yha_vuodet))

    (map #(if (:yha_yhaid %)
            (assoc % :yhatiedot {:yhatunnus                         (:yha_yhatunnus %)
                                 :yhaid                             (:yha_yhaid %)
                                 :yhanimi                           (:yha_yhanimi %)
                                 :elyt                              (:yha_elyt %)
                                 :vuodet                            (:yha_vuodet %)
                                 :kohdeluettelo-paivitetty          (:yha_kohdeluettelo_paivitetty %)
                                 :kohdeluettelo-paivittaja          (:yha_kohdeluettelo_paivittaja %)
                                 :kohdeluettelo-paivittaja-etunimi  (:yha_kohdeluettelo_paivittaja_etunimi %)
                                 :kohdeluettelo-paivittaja-sukunimi (:yha_kohdeluettelo_paivittaja_sukunimi %)
                                 :sidonta-lukittu?                  (:yha_sidonta_lukittu %)})
            %))

        ;; Poista käsitellyt avaimet

    (map #(dissoc %
            :urakoitsija_id :urakoitsija_nimi :urakoitsija_ytunnus
            :hallintayksikko_id :hallintayksikko_nimi :hallintayksikko_lyhenne
            :yha_yhatunnus :yha_yhaid :yha_yhanimi :yha_elyt :yha_vuodet
            :yha_kohdeluettelo_paivitetty :yha_sidonta_lukittu :takuu_loppupvm))))

(defn hallintayksikon-urakat [db {organisaatio :organisaatio :as user} hallintayksikko-id]
  (log/debug "Haetaan hallintayksikön urakat: " hallintayksikko-id)
  (let [urakat (oikeudet/kayttajan-urakat user)]
    (if (and (nil? organisaatio) (empty? urakat))
      (do
        (oikeudet/ei-oikeustarkistusta!)
        [])
      (into []
        urakka-xf
        (q/listaa-urakat-hallintayksikolle db
          {:hallintayksikko      hallintayksikko-id
           :kayttajan_org_id     (:id organisaatio)
           :kayttajan_org_tyyppi (when (:tyyppi organisaatio) (name (:tyyppi organisaatio)))
           :sallitut_urakat      (if (empty? urakat)
                                                                        ;; Jos ei urakoita, annetaan
                                                                        ;; dummy, jotta IN toimii
                                   [-1]
                                   urakat)})))))

(defn hae-urakoita [db user teksti]
  (log/debug "Haetaan urakoita tekstihaulla: " teksti)
  (into []
    urakka-xf
    (q/hae-urakoita db (str "%" teksti "%"))))

(defn hae-organisaation-urakat [db user organisaatio-id]
  (log/debug "Haetaan urakat organisaatiolle: " organisaatio-id)
  []
  (into []
    urakka-xf
    (q/hae-organisaation-urakat db organisaatio-id)))

(defn hae-urakan-organisaatio [db user urakka-id]
  (log/debug "Haetaan organisaatio urakalle: " urakka-id)
  (oikeudet/ei-oikeustarkistusta!)
  (let [organisaatio (first (into []
                              (q/hae-urakan-organisaatio db urakka-id)))]
    (log/debug "Urakan organisaatio saatu: " (pr-str organisaatio))
    organisaatio))

(defn hae-urakan-sopimustyyppi [db user urakka-id]
  (keyword (:sopimustyyppi (first (q/hae-urakan-sopimustyyppi db urakka-id)))))

(defn hae-urakan-tyyppi [db user urakka-id]
  (keyword (:tyyppi (first (q/hae-urakan-tyyppi db urakka-id)))))

(defn tallenna-urakan-sopimustyyppi [db user {:keys [urakka-id sopimustyyppi]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-yleiset user urakka-id)
  (q/tallenna-urakan-sopimustyyppi! db (name sopimustyyppi) urakka-id)
  (hae-urakan-sopimustyyppi db user urakka-id))

(defn tallenna-urakan-tyyppi [db user {:keys [urakka-id urakkatyyppi]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-yleiset user urakka-id)
  (q/tallenna-urakan-tyyppi! db urakkatyyppi urakka-id)
  (hae-urakan-tyyppi db user urakka-id))

(defn hae-yksittainen-urakka [db user urakka-id]
  (log/debug "Hae yksittäinen urakka id:llä: " urakka-id)
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-yleiset user urakka-id)
  (first (into []
           urakka-xf
           (q/hae-yksittainen-urakka db urakka-id))))

(defn aseta-takuun-loppupvm [db user {:keys [urakka-id takuu]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-yleiset user urakka-id)
  (q/aseta-takuun-loppupvm! db {:urakka   urakka-id
                                :loppupvm (:loppupvm takuu)}))
(defn- pvm-str->pvm [pvm-str]
  (. (. (DateTimeFormat/forPattern "d.M.yyyy") parseDateTime pvm-str) toDate))

(defn- pvm->kesa-aika-pvm [pvm-str]
  (let [vuosi-kantaan "2000"
        pv (try (pvm-str->pvm
                  (if (str/ends-with? pvm-str ".") (str pvm-str vuosi-kantaan) (str pvm-str "." vuosi-kantaan)))
             (catch Exception e
               (log/debug "poikkeus " e)
               (throw (IllegalArgumentException. (str (format "Päivämäärä %s ei ole oikean muotoinen päivämäärä." pvm-str))))))]
    (when (and (= (pvm/kuukausi pv) 2)
            (= (pvm/paiva pv) 29))
      (throw (IllegalArgumentException. "Karkauspäivä ei ole sallittu alkamis- tai loppupäivä.")))
    pv))

(defn aseta-urakan-kesa-aika [db user {:keys [urakka-id tiedot]}]
  (let [_ (log/debug "Aseta urakan kesäaika, id " urakka-id ", alku: " (:alkupvm tiedot) ", loppu " (:loppupvm tiedot))
        alkupvm (pvm->kesa-aika-pvm (:alkupvm tiedot))
        loppupvm (pvm->kesa-aika-pvm (:loppupvm tiedot))]
    (when-not (roolit/tilaajan-kayttaja? user)
      (throw (SecurityException. "Vain tilaaja voi asettaa urakan kesäajan")))
    (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-yleiset user urakka-id)

    (if (pvm/ennen? alkupvm loppupvm)
      (do
        (q/aseta-urakan-kesa-aika! db {:urakka urakka-id
                                       :alkupvm alkupvm
                                       :loppupvm loppupvm})
        (q/hae-urakan-kesa-aika db urakka-id))
      (throw (IllegalArgumentException. "Kesäajan alku oltava ennen loppuaikaa.")))))

(defn poista-indeksi-kaytosta [db user {:keys [urakka-id]}]
  (when-not (roolit/tilaajan-kayttaja? user)
    (throw (SecurityException. "Vain tilaaja voi poistaa indeksin käytöstä")))
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-yleiset user urakka-id)
  (jdbc/with-db-transaction [db db]
    (q/aseta-urakan-indeksi! db {:urakka urakka-id :indeksi nil})
    (laskutusyhteenveto-q/poista-urakan-kaikki-muistetut-laskutusyhteenvedot! db
      {:urakka urakka-id})
    :ok))


(defrecord Urakat []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db
           :as this}]
    (julkaise-palvelu http
      :hallintayksikon-urakat
      (fn [user hallintayksikko]
        (hallintayksikon-urakat db user hallintayksikko)))

    (julkaise-palvelu http
      :hae-urakka
      (fn [user urakka-id]
        (hae-yksittainen-urakka db user urakka-id)))

    (julkaise-palvelu http
      :hae-urakoita
      (fn [user teksti]
        (hae-urakoita db user teksti)))

    (julkaise-palvelu http
      :hae-organisaation-urakat
      (fn [user organisaatio-id]
        (hae-organisaation-urakat db user organisaatio-id)))

    (julkaise-palvelu http
      :hae-urakan-organisaatio
      (fn [user urakka-id]
        (hae-urakan-organisaatio db user urakka-id)))

    (julkaise-palvelu http
      :tallenna-urakan-sopimustyyppi
      (fn [user tiedot]
        (tallenna-urakan-sopimustyyppi db user tiedot)))

    (julkaise-palvelu http
      :tallenna-urakan-tyyppi
      (fn [user tiedot]
        (tallenna-urakan-tyyppi db user tiedot)))

    (julkaise-palvelu http
      :aseta-takuun-loppupvm
      (fn [user tiedot]
        (aseta-takuun-loppupvm db user tiedot)))

    (julkaise-palvelu http
      :poista-indeksi-kaytosta
      (fn [user tiedot]
        (poista-indeksi-kaytosta db user tiedot)))

    (julkaise-palvelu http
      :paivita-kesa-aika
      (fn [user tiedot]
        (aseta-urakan-kesa-aika db user tiedot)))

    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http
      :hallintayksikon-urakat
      :hae-urakka
      :hae-urakoita
      :hae-organisaation-urakat
      :tallenna-urakan-sopimustyyppi
      :tallenna-urakan-tyyppi
      :aseta-takuun-loppupvm
      :paivita-kesa-aika)

    this))
