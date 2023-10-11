(ns harja.palvelin.integraatiot.api.tyomaapaivakirja
  "Työmaapäiväkirjan hallinta API:n kautta. Alkuun kirjaus ja päivitys mahdollisuudet"
  (:require [clojure.string :as str]
            [com.stuartsierra.component :as component]
            [clojure.spec.alpha :as s]
            [taoensso.timbre :as log]
            [compojure.core :refer [POST PUT]]
            [harja.pvm :as pvm]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.kyselyt.tyomaapaivakirja :as tyomaapaivakirja-kyselyt]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.integraatiot.api.tyokalut.json :as tyokalut-json]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.validointi.parametrit :as parametrivalidointi]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [clojure.java.jdbc :as jdbc])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(def virheet (atom []))
(s/def ::urakka-id #(and (integer? %) (pos? %)))

(defn- tarkista-parametrit [parametrit]
  (parametrivalidointi/tarkista-parametrit
    parametrit
    {:id "Urakka-id puuttuu"})
  (when (not (s/valid? ::urakka-id (konv/konvertoi->int (:id parametrit))))
    (virheet/heita-viallinen-apikutsu-poikkeus
      {:koodi virheet/+puutteelliset-parametrit+
       :viesti (format "Urakka-id muodossa: %s. Anna muodossa: 1" (:id parametrit))})))

(defn validoi-saa [saatiedot virheet]
  (reduce
    (fn [virheet s]
      (let [saa (:saatieto s)
            virheet (if (or (< (:ilman-lampotila saa) -80) (> (:ilman-lampotila saa) 80))
                      (conj virheet (format "Ilman lämpötila täytyy olla väliltä -80 - 80. Oli nyt %s." (:ilman-lampotila saa)))
                      virheet)
            virheet (if (and (not (nil? (:tien-lampotila saa)))
                          (or (< (:tien-lampotila saa) -80) (> (:tien-lampotila saa) 80)))
                      (conj virheet (format "Tien lämpötila täytyy olla väliltä -80 - 80. Oli nyt %s." (:tien-lampotila saa)))
                      virheet)
            virheet (if (and (not (nil? (:keskituuli saa)))
                          (or (< (:keskituuli saa) 0) (> (:keskituuli saa) 150)))
                      (conj virheet (format "Keskituuli täytyy olla väliltä 0 - 150. Oli nyt %s." (:keskituuli saa)))
                      virheet)
            virheet (if (and (not (nil? (:sateen-olomuoto saa)))
                          (or (< (:sateen-olomuoto saa) 0) (> (:sateen-olomuoto saa) 150)))
                      (conj virheet (format "Sateen olomuoto täytyy olla väliltä 0 - 150. Oli nyt %s." (:sateen-olomuoto saa)))
                      virheet)
            virheet (if (and (not (nil? (:sadesumma saa)))
                          (or (< (:sadesumma saa) 0) (> (:sadesumma saa) 10000)))
                      (conj virheet (format "Sadesumma täytyy olla väliltä 0 - 10000. Oli nyt %s." (:sadesumma saa)))
                      virheet)]
        virheet))
    virheet saatiedot))

(defn validoi-kalusto [kalustot virheet]
  (reduce (fn [virheet k]
            (let [kalusto (:kalusto k)
                  aloitus (tyokalut-json/pvm-string->joda-date (:aloitus kalusto))
                  lopetus (tyokalut-json/pvm-string->joda-date (:lopetus kalusto))
                  virheet (if (pvm/ennen? lopetus aloitus)
                            (conj virheet (str "Kaluston lopetusaika täytyy olla aloitusajan jälkeen."))
                            virheet)
                  virheet (if (or (nil? (:tyokoneiden-lkm kalusto)) (< (:tyokoneiden-lkm kalusto) 0) (> (:tyokoneiden-lkm kalusto) 2000))
                            (conj virheet (format "Työkoneiden lukumäärä täytyy olla väliltä 0 - 2000. Oli nyt %s." (:tyokoneiden-lkm kalusto)))
                            virheet)
                  virheet (if (or (nil? (:lisakaluston-lkm kalusto)) (< (:lisakaluston-lkm kalusto) 0) (> (:lisakaluston-lkm kalusto) 2000))
                            (conj virheet (format "Lisäkaluston lukumäärä täytyy olla väliltä 0 - 2000. Oli nyt %s." (:lisakaluston-lkm kalusto)))
                            virheet)]
              virheet))
    virheet kalustot))

(defn validoi-paivystajat-ja-tyonjohtajat [tiedot omistaja-avain kuvaava-nimi virheet]
  (reduce (fn [t t]
            (let [tieto (omistaja-avain t)
                  aloitus (tyokalut-json/pvm-string->joda-date (:aloitus tieto))
                  lopetus (tyokalut-json/pvm-string->joda-date (:lopetus tieto))
                  virheet (if (pvm/ennen? lopetus aloitus)
                            (conj virheet (format "%s lopetusaika täytyy olla aloitusajan jälkeen." kuvaava-nimi))
                            virheet)
                  virheet (if (> 4 (count (:nimi tieto)))
                            (conj virheet (format "%s nimi liian lyhyt. Oli nyt %s." kuvaava-nimi (:nimi tieto)))
                            virheet)]
              virheet))
    virheet tiedot))

(defn validoi-tieston-toimenpiteet [db toimenpiteet virheet]
  (reduce (fn [virheet t]
            (let [toimenpide (:tieston-toimenpide t)
                  aloitus (tyokalut-json/pvm-string->joda-date (:aloitus toimenpide))
                  lopetus (tyokalut-json/pvm-string->joda-date (:lopetus toimenpide))
                  virheet (if (pvm/ennen? lopetus aloitus)
                            (conj virheet (format "Toimenpiteen lopetusaika täytyy olla aloitusajan jälkeen."))
                            virheet)
                  ;; Varmista, että annetut tehtävät on tietokannassa
                  virheet (reduce (fn [virheet tehtava]
                                    (if (false? (tyomaapaivakirja-kyselyt/onko-tehtava-olemassa? db {:id (get-in tehtava [:tehtava :id])}))
                                      (conj virheet (format "Toimenpiteeseen liitettyä tehtävää ei löydy. Tarkista tehtävä id: %s." (get-in tehtava [:tehtava :id])))
                                      virheet))
                            virheet (:tehtavat toimenpide))]
              virheet))
    virheet toimenpiteet))

(defn validoi-tieston-muut-toimenpiteet [toimenpiteet virheet]
  (reduce (fn [virheet t]
            (let [toimenpide (:tieston-muu-toimenpide t)
                  aloitus (tyokalut-json/pvm-string->joda-date (:aloitus toimenpide))
                  lopetus (tyokalut-json/pvm-string->joda-date (:lopetus toimenpide))
                  virheet (if (pvm/ennen? lopetus aloitus)
                            (conj virheet (format "Tiestön muun toimenpiteen lopetusaika täytyy olla aloitusajan jälkeen."))
                            virheet)
                  ;; Varmista, että annetut tehtävät on kuvattu tarvittavan pitkällä tekstillä
                  virheet (reduce (fn [virheet tehtava]
                                    (if (> 4 (count (get-in tehtava [:tehtava :kuvaus])))
                                      (conj virheet (format "Tiestön muun toimenpiteen kuvaus on liian lyhyt. Tarkenna kuvasta. Oli nyt: %s." (get-in tehtava [:tehtava :kuvaus])))
                                      virheet))
                            virheet (:tehtavat toimenpide))]
              virheet))
    virheet toimenpiteet))

(defn validoi-viranomaisen-avustamiset [avustukset virheet]
  (reduce (fn [virheet a]
            (let [avustus (:viranomaisen-avustus a)
                  ;; Varmista, että annetut tunnit on järkevissä raameissa
                  virheet (if (and (not (nil? (:tunnit avustus)))
                                (or (< (:tunnit avustus) 0) (> (:tunnit avustus) 1000)))
                            (conj virheet (format "Viranomaisen avustamiseen käytetyt tunnit pitää olla väliltä 0 - 1000. Oli nyt: %s." (:tunnit avustus)))
                            virheet)
                  ;; Avustamisen kuvaus pitää olla järkevän mittainen
                  virheet (if (or (nil? (:kuvaus avustus))
                                (> 4 (count (:kuvaus avustus))))
                            (conj virheet (format "Viranomaisen avustamisen kuvausteksti pitää olla asiallisen mittainen. Oli nyt: %s." (:kuvaus avustus)))
                            virheet)]
              virheet))
    virheet avustukset))

(defn validoi-muut-kuvaustekstit [data virheet]
  (let [;; liikenteenohjaus-muutokset
        virheet (reduce (fn [virheet a]
                          (let [ohjaus (:liikenteenohjaus-muutos a)
                                ;; Kuvaus pitää olla järkevän mittainen
                                virheet (if (> 4 (count (:kuvaus ohjaus)))
                                          (conj virheet (format "Liikenteenohjausmuustosten kuvausteksti pitää olla asiallisen mittainen. Oli nyt: %s." (:kuvaus ohjaus)))
                                          virheet)]
                            virheet))
                  virheet (:liikenteenohjaus-muutokset data))

        ;; onnettomuudet
        virheet (reduce (fn [virheet a]
                          (let [onnettomuus (:onnettomuus a)
                                ;; Kuvaus pitää olla järkevän mittainen
                                virheet (if (> 4 (count (:kuvaus onnettomuus)))
                                          (conj virheet (format "Onnettomuuden kuvausteksti pitää olla asiallisen mittainen. Oli nyt: %s." (:kuvaus onnettomuus)))
                                          virheet)]
                            virheet))
                  virheet (:onnettomuudet data))
        ;; palautteet
        virheet (reduce (fn [virheet a]
                          (let [palaute (:palaute a)
                                ;; Kuvaus pitää olla järkevän mittainen
                                virheet (if (> 4 (count (:kuvaus palaute)))
                                          (conj virheet (format "Palautteiden kuvausteksti pitää olla asiallisen mittainen. Oli nyt: %s." (:kuvaus palaute)))
                                          virheet)]
                            virheet))
                  virheet (:palautteet data))

        ;; tilaajan-yhteydenotot
        virheet (reduce (fn [virheet a]
                          (let [yhteydenotto (:tilaajan-yhteydenotto a)
                                ;; Kuvaus pitää olla järkevän mittainen
                                virheet (if (> 4 (count (:kuvaus yhteydenotto)))
                                          (conj virheet (format "Yhteydenoton kuvausteksti pitää olla asiallisen mittainen. Oli nyt: %s." (:kuvaus yhteydenotto)))
                                          virheet)]
                            virheet))
                  virheet (:tilaajan-yhteydenotot data))

        ;; muut-kirjaukset
        ;; Kuvaus pitää olla järkevän mittainen
        virheet (if (> 4 (count (:kuvaus (:muut-kirjaukset data))))
                  (conj virheet (format "Muiden kirjausten kuvausteksti pitää olla asiallisen mittainen. Oli nyt: %s." (:kuvaus (:muut-kirjaukset data))))
                  virheet)]
    virheet))


(defn validoi-tyomaapaivakirja [db data]
  (let [virheet (->> []
                  (validoi-saa (get-in data [:tyomaapaivakirja :saatiedot]))
                  (validoi-kalusto (get-in data [:tyomaapaivakirja :kaluston-kaytto]))
                  (validoi-paivystajat-ja-tyonjohtajat (get-in data [:tyomaapaivakirja :paivystajan-tiedot]) :paivystaja "Päivystäjän")
                  (validoi-paivystajat-ja-tyonjohtajat (get-in data [:tyomaapaivakirja :tyonjohtajan-tiedot]) :tyonjohtaja "Työnjohtajan")
                  (validoi-tieston-toimenpiteet db (get-in data [:tyomaapaivakirja :tieston-toimenpiteet]))
                  (validoi-tieston-muut-toimenpiteet (get-in data [:tyomaapaivakirja :tieston-muut-toimenpiteet]))
                  (validoi-viranomaisen-avustamiset (get-in data [:tyomaapaivakirja :viranomaisen-avustaminen]))
                  (validoi-muut-kuvaustekstit (:tyomaapaivakirja data)))]

    (when-not (empty? virheet)
      (throw+
        {:type virheet/+invalidi-json+
         :virheet [{:koodi virheet/+invalidi-json+
                    :viesti (str/join \space virheet)}]}))))

(defn- hae-tyomaapaivakirjan-versiotiedot [db kayttaja tiedot]
  (validointi/tarkista-urakka-ja-kayttaja db (:urakka_id tiedot) kayttaja)
  (let [hakuparametrit {:urakka_id (:urakka_id tiedot)
                        :paivamaara (tyokalut-json/pvm-string->java-sql-date (:paivamaara tiedot))}
        _ (log/debug "hae-tyomaapaivakirjan-versiotiedot :: hakuparametrit" (pr-str hakuparametrit))
        versiotiedot (first (tyomaapaivakirja-kyselyt/hae-tyomaapaivakirjan-versiotiedot db hakuparametrit))
        versionro (if (or (nil? versiotiedot)) {:versio nil
                                                :tyomaapaivakirja_id nil} versiotiedot)]
    versionro))

(defn- tallenna-kalusto [db data versio tyomaapaivakirja-id urakka-id]
  (doseq [k (get-in data [:kaluston-kaytto])
          :let [kalusto (:kalusto k)
                kalusto (-> kalusto
                          (assoc :aloitus (tyokalut-json/aika-string->java-sql-date (:aloitus kalusto)))
                          (assoc :lopetus (tyokalut-json/aika-string->java-sql-date (:lopetus kalusto)))
                          (merge {:versio versio
                                  :tyomaapaivakirja_id tyomaapaivakirja-id
                                  :urakka_id urakka-id}))]]
    (tyomaapaivakirja-kyselyt/lisaa-kalusto<! db kalusto)))

(defn- tallenna-paivystajat [db data versio tyomaapaivakirja-id urakka-id]
  (doseq [p (get-in data [:paivystajan-tiedot])
          :let [paivystaja (:paivystaja p)
                paivystaja (-> paivystaja
                             (assoc :aloitus (tyokalut-json/aika-string->java-sql-date (:aloitus paivystaja)))
                             (assoc :lopetus (tyokalut-json/aika-string->java-sql-date (:lopetus paivystaja))))]]
    (tyomaapaivakirja-kyselyt/lisaa-paivystaja<! db (merge
                                                      paivystaja
                                                      {:versio versio
                                                       :tyomaapaivakirja_id tyomaapaivakirja-id
                                                       :urakka_id urakka-id}))))

(defn- tallenna-tyonjohtajat [db data versio tyomaapaivakirja-id urakka-id]
  (doseq [j (get-in data [:tyonjohtajan-tiedot])
          :let [johtaja (:tyonjohtaja j)
                johtaja (-> johtaja
                          (assoc :aloitus (tyokalut-json/aika-string->java-sql-date (:aloitus johtaja)))
                          (assoc :lopetus (tyokalut-json/aika-string->java-sql-date (:lopetus johtaja))))]]
    (tyomaapaivakirja-kyselyt/lisaa-tyonjohtaja<! db (merge
                                                       johtaja
                                                       {:versio versio
                                                        :tyomaapaivakirja_id tyomaapaivakirja-id
                                                        :urakka_id urakka-id}))))

(defn- tallenna-saatiedot [db data versio tyomaapaivakirja-id urakka-id]
  (doseq [s (get-in data [:saatiedot])
          :let [saa (:saatieto s)
                saa (-> saa
                      (assoc :havaintoaika (tyokalut-json/aika-string->java-sql-date (:havaintoaika saa)))
                      (assoc :aseman-tietojen-paivityshetki (tyokalut-json/aika-string->java-sql-date (:aseman-tietojen-paivityshetki saa))))]]
    (tyomaapaivakirja-kyselyt/lisaa-saatiedot<! db (merge
                                                     saa
                                                     {:versio versio
                                                      :tyomaapaivakirja_id tyomaapaivakirja-id
                                                      :urakka_id urakka-id}))))

(defn- tallenna-poikkeussaa [db data versio tyomaapaivakirja-id urakka-id]
  (doseq [p (get-in data [:poikkeukselliset-saahavainnot])
          :let [poikkeus (:poikkeuksellinen-saahavainto p)
                poikkeus (-> poikkeus
                           (assoc :havaintoaika (tyokalut-json/aika-string->java-sql-date (:havaintoaika poikkeus))))]]
    (tyomaapaivakirja-kyselyt/lisaa-poikkeussaa<! db (merge
                                                       poikkeus
                                                       {:versio versio
                                                        :tyomaapaivakirja_id tyomaapaivakirja-id
                                                        :urakka_id urakka-id}))))

(defn- tallenna-toimenpiteet [db data versio tyomaapaivakirja-id urakka-id]
  (doseq [t (get-in data [:tieston-toimenpiteet])
          :let [toimenpide (:tieston-toimenpide t)
                toimenpide (-> toimenpide
                             (assoc :aloitus (tyokalut-json/aika-string->java-sql-date (:aloitus toimenpide)))
                             (assoc :lopetus (tyokalut-json/aika-string->java-sql-date (:lopetus toimenpide)))
                             (assoc :tyyppi "yleinen")
                             (assoc :toimenpiteet nil) ;; Ei voida lisätä toimenpiteitä.
                             (assoc :tehtavat (->
                                                (map (comp :id :tehtava) (:tehtavat toimenpide))
                                                (konv/seq->array))))]]
    (tyomaapaivakirja-kyselyt/lisaa-tie-toimenpide<! db (merge
                                                          toimenpide
                                                          {:versio versio
                                                           :tyomaapaivakirja_id tyomaapaivakirja-id
                                                           :urakka_id urakka-id}))))

(defn- tallenna-muut-toimenpiteet [db data versio tyomaapaivakirja-id urakka-id]
  (doseq [t (get-in data [:tieston-muut-toimenpiteet])
          :let [toimenpide (:tieston-muu-toimenpide t)
                toimenpide (-> toimenpide
                             (assoc :aloitus (tyokalut-json/aika-string->java-sql-date (:aloitus toimenpide)))
                             (assoc :lopetus (tyokalut-json/aika-string->java-sql-date (:lopetus toimenpide)))
                             (assoc :tyyppi "muu")
                             (assoc :tehtavat nil) ;; Ei voida lisätä tehtäviä
                             (assoc :toimenpiteet (->
                                                    (map (comp :kuvaus :tehtava) (:tehtavat toimenpide))
                                                    (konv/seq->array))))]]
    (tyomaapaivakirja-kyselyt/lisaa-tie-toimenpide<! db (merge
                                                          toimenpide
                                                          {:versio versio
                                                           :tyomaapaivakirja_id tyomaapaivakirja-id
                                                           :urakka_id urakka-id}))))

(defn- tallenna-onnettomuudet [db data versio tyomaapaivakirja-id urakka-id]
  (doseq [o (get-in data [:onnettomuudet])]
    (tyomaapaivakirja-kyselyt/lisaa-tapahtuma<! db (merge
                                                     (:onnettomuus o)
                                                     {:versio versio
                                                      :tyomaapaivakirja_id tyomaapaivakirja-id
                                                      :urakka_id urakka-id
                                                      :tyyppi "onnettomuus"}))))

(defn- tallenna-liikenteenohjaus-muutokset [db data versio tyomaapaivakirja-id urakka-id]
  (doseq [l (get-in data [:liikenteenohjaus-muutokset])]
    (tyomaapaivakirja-kyselyt/lisaa-tapahtuma<! db (merge
                                                     (:liikenteenohjaus-muutos l)
                                                     {:versio versio
                                                      :tyomaapaivakirja_id tyomaapaivakirja-id
                                                      :urakka_id urakka-id
                                                      :tyyppi "liikenteenohjausmuutos"}))))

(defn- tallenna-palautteet [db data versio tyomaapaivakirja-id urakka-id]
  (doseq [p (get-in data [:palautteet])]
    (tyomaapaivakirja-kyselyt/lisaa-tapahtuma<! db (merge
                                                     (:palaute p)
                                                     {:versio versio
                                                      :tyomaapaivakirja_id tyomaapaivakirja-id
                                                      :urakka_id urakka-id
                                                      :tyyppi "palaute"}))))

(defn- tallenna-tapahtuma [db data versio tyomaapaivakirja-id urakka-id paa-avain toissijainen-avain tyyppi]
  (doseq [v (paa-avain data)]
    (tyomaapaivakirja-kyselyt/lisaa-tapahtuma<! db (merge
                                                     (toissijainen-avain v)
                                                     {:versio versio
                                                      :tyomaapaivakirja_id tyomaapaivakirja-id
                                                      :urakka_id urakka-id
                                                      :tyyppi tyyppi}))))

(defn- tallenna-muut-kirjaukset [db data versio tyomaapaivakirja-id urakka-id]
  (tyomaapaivakirja-kyselyt/lisaa-tapahtuma<! db {:kuvaus (get-in data [:muut-kirjaukset :kuvaus])
                                                  :versio versio
                                                  :tyomaapaivakirja_id tyomaapaivakirja-id
                                                  :urakka_id urakka-id
                                                  :tyyppi "muut_kirjaukset"}))

(defn- tallenna-toimeksiannot [db data versio tyomaapaivakirja-id urakka-id]
  (doseq [v (get-in data [:viranomaisen-avustaminen])]
    (tyomaapaivakirja-kyselyt/lisaa-toimeksianto<! db (merge
                                                        (:viranomaisen-avustus v)
                                                        {:versio versio
                                                         :tyomaapaivakirja_id tyomaapaivakirja-id
                                                         :urakka_id urakka-id
                                                         :kuvaus (:kuvaus (:viranomaisen-avustus v))
                                                         :aika (:tunnit (:viranomaisen-avustus v))}))))

(defn tallenna-tyomaapaivakirja [db urakka-id data kayttaja tyomaapaivakirja-id]
  (let [_ (log/debug "tallenna-tyomaapaivakirja :: data" (pr-str data))
        tyomaapaivakirja-id (konv/konvertoi->int tyomaapaivakirja-id)
        versiotiedot (hae-tyomaapaivakirjan-versiotiedot db kayttaja {:urakka_id urakka-id
                                                                      :paivamaara (get-in data [:tunniste :paivamaara])})
        versio (or
                 (get-in data [:tunniste :versio])
                 (inc (or (:versio versiotiedot) 0)))
        tyomaapaivakirja {:urakka_id urakka-id
                          :kayttaja (:id kayttaja)
                          :paivamaara (tyokalut-json/pvm-string->java-sql-date (get-in data [:tunniste :paivamaara]))
                          :ulkoinen-id (get-in data [:tunniste :id])
                          :versio versio
                          :id tyomaapaivakirja-id}
        ;; Varmista, että annettu versio on suurempi, kuin kannassa oleva
        _ (when tyomaapaivakirja-id
            (if (not= versio
                  (inc (:versio versiotiedot)))
              (throw+ {:type virheet/+vaara-versio-tyomaapaivakirja+ :virheet [{:koodi virheet/+vaara-versio-tyomaapaivakirja-virhe-koodi+
                                                                                :viesti "Työmaapäiväkirjan versio ei täsmää"}]})))
        tyomaapaivakirja-id (if tyomaapaivakirja-id
                              ;; Päivitä vanhaa
                              (do
                                (tyomaapaivakirja-kyselyt/paivita-tyomaapaivakirja<! db tyomaapaivakirja)
                                (:id tyomaapaivakirja))
                              ;; ELSE: Lisää uusi
                              (try
                                (:id (tyomaapaivakirja-kyselyt/lisaa-tyomaapaivakirja<! db tyomaapaivakirja))
                                (catch Exception e
                                  (throw+ {:type virheet/+duplikaatti-tyomaapaivakirja+ :virheet [{:koodi virheet/+duplikaatti-tyomaapaivakirja-virhe-koodi+
                                                                                                   :viesti "Duplikaatti versio ja päivämäärä"}]}))))

        ;; Tallennetaan jokainen osio omalla versionumerolla.
        _ (tallenna-kalusto db data versio tyomaapaivakirja-id urakka-id)
        _ (tallenna-paivystajat db data versio tyomaapaivakirja-id urakka-id)
        _ (tallenna-tyonjohtajat db data versio tyomaapaivakirja-id urakka-id)
        _ (tallenna-saatiedot db data versio tyomaapaivakirja-id urakka-id)
        _ (tallenna-poikkeussaa db data versio tyomaapaivakirja-id urakka-id)
        _ (tallenna-toimenpiteet db data versio tyomaapaivakirja-id urakka-id)
        _ (tallenna-muut-toimenpiteet db data versio tyomaapaivakirja-id urakka-id)
        _ (tallenna-onnettomuudet db data versio tyomaapaivakirja-id urakka-id)
        _ (tallenna-liikenteenohjaus-muutokset db data versio tyomaapaivakirja-id urakka-id)
        _ (tallenna-palautteet db data versio tyomaapaivakirja-id urakka-id)
        _ (tallenna-toimeksiannot db data versio tyomaapaivakirja-id urakka-id)
        _ (tallenna-tapahtuma db data versio tyomaapaivakirja-id urakka-id :tilaajan-yhteydenotot :tilaajan-yhteydenotto "tilaajan-yhteydenotto")
        _ (tallenna-muut-kirjaukset db data versio tyomaapaivakirja-id urakka-id)]
    tyomaapaivakirja-id))

(defn kirjaa-tyomaapaivakirja [db {:keys [id tid] :as parametrit} data kayttaja]
  (validoi-tyomaapaivakirja db data)
  (tarkista-parametrit parametrit)
  (let [urakka-id (konv/konvertoi->int id)
        tyomaapaivakirja (:tyomaapaivakirja data)
        ;; Tallenna
        tyomaapaivakirja-id (jdbc/with-db-transaction [db db]
                              (tallenna-tyomaapaivakirja db urakka-id tyomaapaivakirja kayttaja tid))
        ;; Muodosta OK vastaus - Error vastaus pärähtää throwssa ja sen käsittelee kutsu-kasittelijä
        vastaus {:status "OK"
                 :tyomaapaivakirja-id tyomaapaivakirja-id}]
    vastaus))

(defrecord Tyomaapaivakirja []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki :as this}]
    (julkaise-reitti
      http :kirjaa-tyomaapaivakirja
      (POST "/api/urakat/:id/tyomaapaivakirja" request
        (kasittele-kutsu db
          integraatioloki
          :kirjaa-tyomaapaivakirja
          request
          json-skeemat/tyomaapaivakirja-kirjaus-request
          json-skeemat/tyomaapaivakirja-kirjaus-response
          (fn [parametrit data kayttaja db]
            (kirjaa-tyomaapaivakirja db parametrit data kayttaja))
          :kirjoitus))
      true)
    (julkaise-reitti
      http :paivita-tyomaapaivakirja
      (PUT "/api/urakat/:id/tyomaapaivakirja/:tid" request
        (kasittele-kutsu db
          integraatioloki
          :paivita-tyomaapaivakirja
          request
          json-skeemat/tyomaapaivakirja-paivitys-request
          json-skeemat/tyomaapaivakirja-kirjaus-response
          (fn [parametrit data kayttaja db]
            (kirjaa-tyomaapaivakirja db parametrit data kayttaja))
          :kirjoitus))
      true)
    (julkaise-palvelu (:http-palvelin this)
      :hae-tyomaapaivakirjan-versiotiedot
      (fn [user tiedot]
        (hae-tyomaapaivakirjan-versiotiedot (:db this) user tiedot)))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http
      :kirjaa-tyomaapaivakirja
      :paivita-tyomaapaivakirja
      :hae-tyomaapaivakirjan-versiotiedot)
    this))
