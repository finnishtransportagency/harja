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
  (let [fn-tarkista-arvo (fn [virheet arvo min max viesti] (if (and (some? arvo)
                                                                 (or (< arvo min) (> arvo max)))
                                                             (conj virheet (format viesti arvo))
                                                             virheet))]
    (reduce
      (fn [acc-virheet s]
        (let [saa (:saatieto s)
              acc-virheet (-> acc-virheet
                            (fn-tarkista-arvo (:ilman-lampotila saa) -80 80 "Ilman lämpötila täytyy olla väliltä -80 - 80. Oli nyt %s.")
                            (fn-tarkista-arvo (:tien-lampotila saa) -80 80 "Tien lämpötila täytyy olla väliltä -80 - 80. Oli nyt %s.")
                            (fn-tarkista-arvo (:keskituuli saa) 0 150 "Keskituuli täytyy olla väliltä 0 - 150. Oli nyt %s.")
                            (fn-tarkista-arvo (:sateen-olomuoto saa) 0 150 "Sateen olomuoto täytyy olla väliltä 0 - 150. Oli nyt %s.")
                            (fn-tarkista-arvo (:sadesumma saa) 0 10000 "Sadesumma täytyy olla väliltä 0 - 10000. Oli nyt %s."))]
          acc-virheet))
      virheet saatiedot)))

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
  (let [kategoriat {:liikenteenohjaus-muutos :liikenteenohjaus-muutokset
                    :onnettomuus :onnettomuudet
                    :palaute :palautteet
                    :tilaajan-yhteydenotto :tilaajan-yhteydenotot}
        ;; Funktio joka validoi annetun kentän (:kuvaus) arvon
        fn-validoi-kuvaus (fn [item key]
                            (let [kuvaus (or (:kuvaus (key item)) (:kuvaus item))]
                              (if (> 4 (count kuvaus))
                                (format "Kentän '%s' kuvausteksti pitää olla asiallisen mittainen. Saatiin: '%s'." (name key) kuvaus)
                                nil)))
        ;; Funktio jolla käydään kaikki annetut kategoriat läpi ja validoidaan niiden kuvaukset
        fn-validoi-avain (fn [virheet-acc [key cat-key]]
                           (reduce (fn [acc item]
                                     (let [virhe (fn-validoi-kuvaus item key)]
                                       (if virhe (conj acc virhe) acc)))
                             virheet-acc
                             (cat-key data)))
        ;; Data joka tulee hieman erissä muodossa (muut kirjaukset, urakoitsijan merkinät)
        fn-validoi-yksittainen (fn [virheet-acc avain]
                                 (let [kentta (avain data)]
                                   (if kentta
                                     (if-let [virhe (fn-validoi-kuvaus kentta avain)]
                                       (conj virheet-acc virhe)
                                       virheet-acc)
                                     virheet-acc)))
        virheet-acc (reduce fn-validoi-avain virheet kategoriat)]
    ;; Käydään vielä loput läpi, sallitaan jos näitä avaimia ei ole datassa, mutta jos on niin kuvaus pitää löytyä 
    (-> virheet-acc
      (fn-validoi-yksittainen :muut-kirjaukset)
      (fn-validoi-yksittainen :urakoitsijan-merkinnat))))


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
  ;; Tarkistetaan onko muita kirjauksia annettu, kun tämä ei ole json skeemassa pakolinen tieto
  (when (:muut-kirjaukset data)
    (tyomaapaivakirja-kyselyt/lisaa-tapahtuma<! db {:kuvaus (get-in data [:muut-kirjaukset :kuvaus])
                                                    :versio versio
                                                    :tyomaapaivakirja_id tyomaapaivakirja-id
                                                    :urakka_id urakka-id
                                                    :tyyppi "muut_kirjaukset"})))

(defn- tallenna-urakoitsijan-merkinnat [db data versio tyomaapaivakirja-id urakka-id]
  ;; Tarkista, annettiinko urakoitsijan merkintä ennen tallennusta, tämä on vapaaehtoinen kenttä
  (when (:urakoitsijan-merkinnat data)
    (tyomaapaivakirja-kyselyt/lisaa-kommentti<! db {:urakka_id urakka-id
                                                    :tyomaapaivakirja_id tyomaapaivakirja-id
                                                    :versio versio
                                                    :kommentti (get-in data [:urakoitsijan-merkinnat :kuvaus])
                                                    ;; Kun luoja on nil, insert lause asettaa luojaksi integraation 
                                                    :luoja nil
                                                    :urakoitsijan_merkinta true})))

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
        _ (tallenna-muut-kirjaukset db data versio tyomaapaivakirja-id urakka-id)
        _ (tallenna-urakoitsijan-merkinnat db data versio tyomaapaivakirja-id urakka-id)]
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
