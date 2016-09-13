(ns harja.palvelin.palvelut.urakat
  (:require [com.stuartsierra.component :as component]
            [harja.domain.roolit :as roolit]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelut poista-palvelut]]
            [harja.kyselyt.urakat :as q]
            [harja.kyselyt.kayttajat :as kayttajat-q]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.laskutusyhteenveto :as laskutusyhteenveto-q]
            [harja.geo :refer [muunna-pg-tulokset]]
            [clojure.string :as str]
            [harja.pvm :as pvm]
            [taoensso.timbre :as log]
            [harja.domain.oikeudet :as oikeudet]
            [clojure.java.jdbc :as jdbc]))

(def ^{:const true} oletus-toleranssi 50)

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
              (q/hae-urakoiden-organisaatiotiedot db urakka-id)

              #_(roolit/lukuoikeus-kaikkiin-urakoihin? user)
              ;; Haetaan vaan kaikki urakat, näistä filtteröidään joka tapauksessa
              ;; pois sellaiset, johon käyttäjällä ei ole oikeutta
              :else
              (q/hae-kaikki-urakat-aikavalilla
                db (konv/sql-date alku) (konv/sql-date loppu)
                (when urakoitsija urakoitsija)
                (when urakkatyyppi (name urakkatyyppi)) hallintayksikot)

              ;:else
              #_(do
                (log/debug "Hae käyttäjän urakat aikaväliltä")
                (log/debug (:id user))
                (log/debug (konv/sql-date alku) (konv/sql-date loppu))
                (log/debug urakoitsija urakkatyyppi hallintayksikot)
                (kayttajat-q/hae-kayttajan-urakat-aikavalilta
                 db (:id user)
                 (konv/sql-date alku) (konv/sql-date loppu)
                 (when urakoitsija urakoitsija)
                 (when urakkatyyppi (name urakkatyyppi))
                 hallintayksikot)))))
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

(defn urakoiden-alueet
  [db user oikeus urakka-idt toleranssi]
  (when-not (empty? urakka-idt)
    (into []
         (comp
           (filter (fn [{:keys [urakka_id]}]
                     (oikeudet/voi-lukea? oikeus urakka_id user)))
           (harja.geo/muunna-pg-tulokset :urakka_alue)
           (harja.geo/muunna-pg-tulokset :alueurakka_alue)
           (map konv/alaviiva->rakenne))
         (q/hae-urakoiden-geometriat db (or toleranssi oletus-toleranssi) urakka-idt))))

(defn kayttajan-urakat-aikavalilta-alueineen
  "Tekee saman kuin kayttajan-urakat-aikavalilta, mutta liittää urakoihin mukaan niiden geometriat."
  ([db user oikeus]
   (kayttajan-urakat-aikavalilta-alueineen db user oikeus nil nil nil nil (pvm/nyt) (pvm/nyt)))
  ([db user oikeus urakka-id urakoitsija urakkatyyppi hallintayksikot alku loppu]
   (kayttajan-urakat-aikavalilta-alueineen db user oikeus urakka-id urakoitsija urakkatyyppi
                                           hallintayksikot alku loppu oletus-toleranssi))
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
                                    (urakoiden-alueet db user oikeus urakka-idt toleranssi)))]
     (mapv
       (fn [au]
         (assoc au :urakat (mapv
                             (fn [urakka]
                               (assoc urakka :alue (get urakat-alueineen (:id urakka))))
                             (:urakat au))))
       aluekokonaisuudet))))

(defn hae-urakka-idt-sijainnilla [db urakkatyyppi {:keys [x y]}]
  (let [urakka-idt (map :id (q/hae-urakka-sijainnilla db urakkatyyppi x y))]
    (if (and (empty? urakka-idt)
             (not= "hoito" urakkatyyppi))
        ;; Jos ei löytynyt urakoita eri tyypillä, kokeillaan hoido urakoita
      (map :id (q/hae-urakka-sijainnilla db "hoito" x y))
      urakka-idt)))

(def urakka-xf
  (comp (muunna-pg-tulokset :alue :alueurakan_alue)

        ;; Jos alueurakan alue on olemassa, käytetään sitä alueena
        (map #(if-let [alueurakka (:alueurakan_alue %)]
                (-> %
                    (dissoc :alueurakan_alue)
                    (assoc  :alue alueurakka))
                (dissoc % :alueurakan_alue)))

        (map #(assoc % :urakoitsija {:id (:urakoitsija_id %)
                                     :nimi (:urakoitsija_nimi %)
                                     :ytunnus (:urakoitsija_ytunnus %)}))

        (map #(assoc % :loppupvm (pvm/aikana (:loppupvm %) 23 59 59 999))) ; Automaattikonversiolla aika on 00:00

        (map #(assoc % :takuu {:loppupvm (:takuu_loppupvm %)}))

        ;; :sopimukset kannasta muodossa ["2=8H05228/01" "3=8H05228/10"] ja
        ;; tarjotaan ulos muodossa {:sopimukset {"2" "8H05228/01", "3" "8H05228/10"}
        (map #(update-in % [:sopimukset] (fn [jdbc-array]
                                           (if (nil? jdbc-array)
                                             {}
                                             (into {} (map (fn [s](let [[id sampoid] (str/split s #"=")]
                                                                    [(Long/parseLong id) sampoid]))
                                                           (.getArray jdbc-array)))))))
        (map #(assoc % :hallintayksikko {:id (:hallintayksikko_id %)
                                         :nimi (:hallintayksikko_nimi %)
                                         :lyhenne (:hallintayksikko_lyhenne %)}))
        (map #(assoc %
                :tyyppi (keyword (:tyyppi %))
                :sopimustyyppi (and (:sopimustyyppi %) (keyword (:sopimustyyppi %)))))

        ;; Käsitellään päällystysurakan tiedot

        (map #(konv/array->vec % :yha_elyt))
        (map #(konv/array->vec % :yha_vuodet))

        (map #(if (:yha_yhaid %)
               (assoc % :yhatiedot {:yhatunnus (:yha_yhatunnus %)
                                    :yhaid (:yha_yhaid %)
                                    :yhanimi (:yha_yhanimi %)
                                    :elyt (:yha_elyt %)
                                    :vuodet (:yha_vuodet %)
                                    :kohdeluettelo-paivitetty (:yha_kohdeluettelo_paivitetty %)
                                    :sidonta-lukittu? (:yha_sidonta_lukittu %)})
               %))

        ;; Poista käsitellyt avaimet

        (map #(dissoc %
                      :urakoitsija_id :urakoitsija_nimi :urakoitsija_ytunnus
                      :hallintayksikko_id :hallintayksikko_nimi :hallintayksikko_lyhenne
                      :yha_yhatunnus :yha_yhaid :yha_yhanimi :yha_elyt :yha_vuodet
                      :yha_kohdeluettelo_paivitetty :yha_sidonta_lukittu :takuu_loppupvm))))

(defn hallintayksikon-urakat [db {organisaatio :organisaatio :as user} hallintayksikko-id]
  (log/debug "Haetaan hallintayksikön urakat: " hallintayksikko-id)
  (if-not organisaatio
    []
    (let [urakat (oikeudet/kayttajan-urakat user)]
      (into []
            urakka-xf
            (q/listaa-urakat-hallintayksikolle db
                                               {:hallintayksikko hallintayksikko-id
                                                :kayttajan_org_id (:id organisaatio)
                                                :kayttajan_org_tyyppi (name (:tyyppi organisaatio))
                                                :sallitut_urakat (if (empty? urakat)
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
  (let [organisaatio (first (into []
        (q/hae-urakan-organisaatio db urakka-id)))]
    (log/debug "Urakan organisaatio saatu: " (pr-str organisaatio))
    organisaatio))

(defn hae-urakan-sopimustyyppi [db user urakka-id]
  (keyword (:sopimustyyppi (first (q/hae-urakan-sopimustyyppi db urakka-id)))))

(defn hae-urakan-tyyppi [db user urakka-id]
  (keyword (:tyyppi (first (q/hae-urakan-tyyppi db urakka-id)))))

(defn tallenna-urakan-sopimustyyppi [db user {:keys  [urakka-id sopimustyyppi]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-yleiset user urakka-id)
  (q/tallenna-urakan-sopimustyyppi! db (name sopimustyyppi) urakka-id)
  (hae-urakan-sopimustyyppi db user urakka-id))

(defn tallenna-urakan-tyyppi [db user {:keys  [urakka-id urakkatyyppi]}]
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
  (q/aseta-takuun-loppupvm! db {:urakka urakka-id
                                :loppupvm (:loppupvm takuu)}))

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
           db :db :as this}]
    (julkaise-palvelut
     http
     :hallintayksikon-urakat
     (fn [user hallintayksikko]
       (hallintayksikon-urakat db user hallintayksikko))

     :hae-urakka
     (fn [user urakka-id]
       (hae-yksittainen-urakka db user urakka-id))

     :hae-urakoita
     (fn [user teksti]
       (hae-urakoita db user teksti))

     :hae-organisaation-urakat
     (fn [user organisaatio-id]
       (hae-organisaation-urakat db user organisaatio-id))

     :hae-urakan-organisaatio
     (fn [user urakka-id]
       (hae-urakan-organisaatio db user urakka-id))

     :tallenna-urakan-sopimustyyppi
     (fn [user tiedot]
       (tallenna-urakan-sopimustyyppi db user tiedot))

     :tallenna-urakan-tyyppi
     (fn [user tiedot]
       (tallenna-urakan-tyyppi db user tiedot))

     :aseta-takuun-loppupvm
     (fn [user tiedot]
       (aseta-takuun-loppupvm db user tiedot))

     :poista-indeksi-kaytosta
     (fn [user tiedot]
       (poista-indeksi-kaytosta db user tiedot)))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http
                     :hallintayksikon-urakat
                     :hae-urakka
                     :hae-urakoita
                     :hae-organisaation-urakat
                     :tallenna-urakan-sopimustyyppi
                     :tallenna-urakan-tyyppi
                     :aseta-takuun-loppupvm)

    this))
